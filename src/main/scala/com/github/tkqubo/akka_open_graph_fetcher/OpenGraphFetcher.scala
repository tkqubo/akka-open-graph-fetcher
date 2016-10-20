package com.github.tkqubo.akka_open_graph_fetcher

import java.net.{URI, URISyntaxException}

import akka.actor.{ActorSystem, Scheduler}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.{Http, HttpExt}
import akka.pattern.after
import akka.stream.ActorMaterializer
import com.github.tkqubo.akka_open_graph_fetcher
import com.github.tkqubo.akka_open_graph_fetcher.OpenGraphFetcher._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, TimeoutException}

/**
  * Fetches [[OpenGraph]] from the specified URL
  * @param openGraphParser Parser for conversion from `HttpResponse` to [[OpenGraph]]
  * @param requestTimeout Request timeout
  * @param maxRedirectionRetries Max trial for multiple redirection
  * @param httpExt HTTP client
  */
class OpenGraphFetcher(
  val openGraphParser: OpenGraphParser = new OpenGraphParser(),
  val requestTimeout: FiniteDuration = defaultRequestTimeout,
  val maxRedirectionRetries: Int = defaultMaxRedirectionRetries,
  val httpExt: HttpExt
)(implicit scheduler: Scheduler, materializer: ActorMaterializer) {
  private val urlPattern = "^https?://.*".r

  /**
    * Fetches [[OpenGraph]] from the specified URL
    * @param url URL to fetch
    * @param headers Additinal `HttpHeader`s
    * @param ec
    * @return
    */
  def fetch(
    url: String, headers: Iterable[HttpHeader] = Nil
  )(implicit ec: ExecutionContext): Future[OpenGraph] =
    if (!urlPattern.pattern.matcher(url).matches()) {
      Future.successful(
        OpenGraph(url, error = Error.maybeFromStatusCode(StatusCodes.BadRequest, Some(ErrorMessage.forInvalidUriScheme())))
      )
    } else {
      try {
        val asciiUrl = new URI(url).toASCIIString
        val request = HttpRequest(uri = Uri(asciiUrl), headers = scala.collection.immutable.Seq(headers.toSeq:_*))
        (fetchSuccess(request) recover recoverFailure(url)) (ec)
      } catch {
        case e: URISyntaxException =>
          Future.successful(
            OpenGraph(url, error = Error.maybeFromStatusCode(StatusCodes.BadRequest, Some(ErrorMessage.forIllegalCharacter(e))))
          )
      }
    }

  private def fetchSuccess(request: HttpRequest)(implicit ec: ExecutionContext): Future[OpenGraph] =
    for {
      response <- Future.firstCompletedOf(
        handleRequestWithRedirect(request, Set(request.uri)) ::
        requestTimeout(request) ::
        Nil
      )
      openGraph <- openGraphParser.parse(request, response)
    } yield openGraph

  private def handleRequestWithRedirect(request: HttpRequest, triedUris: Set[Uri])(implicit ec: ExecutionContext): Future[HttpResponse] =
    for {
      response <- httpExt.singleRequest(request)
      response <- if (triedUris.size < maxRedirectionRetries && response.status.isRedirection()) {
        response.header[Location]
          .map(_.uri)
          .filterNot(triedUris.contains)
          .map(uri => request.copy(uri = uri))
          .map(redirectRequest => handleRequestWithRedirect(redirectRequest, triedUris + redirectRequest.uri))
          .getOrElse(Future.successful(response))
      } else {
        Future.successful(response)
      }
    } yield response

  private def recoverFailure(url: String): PartialFunction[Throwable, OpenGraph] = {
    case e: Throwable => OpenGraph(url, error = Some(akka_open_graph_fetcher.Error.fromThrowable(e)))
  }

  private def requestTimeout(request: HttpRequest)(implicit ec: ExecutionContext): Future[HttpResponse] = {
    after(requestTimeout, scheduler)(Future.failed(new TimeoutException(ErrorMessage.forRequestTimeout(request, requestTimeout))))
  }
}

object OpenGraphFetcher {
  def apply(
    openGraphParser: OpenGraphParser = new OpenGraphParser(),
    requestTimeout: FiniteDuration = defaultRequestTimeout,
    maxRedirectionRetries: Int = defaultMaxRedirectionRetries
  )(implicit actorSystem: ActorSystem, materializer: ActorMaterializer): OpenGraphFetcher =
    new OpenGraphFetcher(openGraphParser, requestTimeout, maxRedirectionRetries, Http())(actorSystem.scheduler, materializer)

  val defaultRequestTimeout: FiniteDuration = 3 seconds
  val defaultMaxRedirectionRetries: Int = 3

  object ErrorMessage {
    def forInvalidUriScheme(): String = "HTTP URI scheme should be either http or https"
    def forIllegalCharacter(e: URISyntaxException): String = s"""URI "${e.getInput}" has illegal character at ${e.getIndex}"""
    def forRequestTimeout(request: HttpRequest, requestTimeout: FiniteDuration): String = s"Request to ${request.uri} failed after $requestTimeout"
  }
}

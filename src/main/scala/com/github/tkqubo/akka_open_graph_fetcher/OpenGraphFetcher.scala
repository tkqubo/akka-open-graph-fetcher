package com.github.tkqubo.akka_open_graph_fetcher

import akka.actor.{ActorSystem, Scheduler}
import akka.http.scaladsl.model._
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
  * @param httpExt HTTP client
  * @param scheduler
  * @param materializer
  */
class OpenGraphFetcher(
  val openGraphParser: OpenGraphParser = new OpenGraphParser(),
  val requestTimeout: FiniteDuration = defaultRequestTimeout,
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
      val request = HttpRequest(uri = Uri(url), headers = scala.collection.immutable.Seq(headers.toSeq:_*))
      (fetchSuccess(request) recover recoverFailure(url)) (ec)
    }

  private def fetchSuccess(request: HttpRequest)(implicit ec: ExecutionContext): Future[OpenGraph] =
    for {
      response <- Future.firstCompletedOf(
        httpExt.singleRequest(request) ::
        requestTimeout(request) ::
        Nil
      )
      openGraph <- openGraphParser.parse(request, response)
    } yield openGraph

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
    requestTimeout: FiniteDuration = OpenGraphFetcher.defaultRequestTimeout
  )(implicit actorSystem: ActorSystem, materializer: ActorMaterializer): OpenGraphFetcher =
    new OpenGraphFetcher(openGraphParser, requestTimeout, Http())(actorSystem.scheduler, materializer)

  val defaultRequestTimeout: FiniteDuration = 3 seconds

  object ErrorMessage {
    def forInvalidUriScheme(): String = "HTTP URI scheme should be either http or https"
    def forRequestTimeout(request: HttpRequest, requestTimeout: FiniteDuration): String = s"Request to ${request.uri} failed after $requestTimeout"
  }
}

package com.github.tkqubo.akka_http_og_fetcher

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.{Http, HttpExt}
import akka.pattern.after
import akka.stream.ActorMaterializer
import com.github.tkqubo.akka_http_og_fetcher

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, TimeoutException}
import scala.language.postfixOps

class OpenGraphFetcher(
  openGraphParser: OpenGraphParser = new OpenGraphParser(),
  requestTimeout: FiniteDuration = OpenGraphFetcher.defaultRequestTimeout
)(implicit actorSystem: ActorSystem, materializer: ActorMaterializer) {
  private val urlPattern = "^https?://.*".r
  private val httpExt: HttpExt = Http()

  def fetch(
    url: String, headers: Iterable[HttpHeader] = Seq.empty
  )(implicit ec: ExecutionContext): Future[OpenGraph] =
    if (!urlPattern.pattern.matcher(url).matches()) {
      Future.successful(
        OpenGraph(url, error = akka_http_og_fetcher.Error.maybeFromStatusCode(StatusCodes.BadRequest, Some("HTTP URI scheme should be either http or https")))
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
    case e: Throwable => OpenGraph(url, error = Some(akka_http_og_fetcher.Error.fromThrowable(e)))
  }

  private def requestTimeout(request: HttpRequest)(implicit ec: ExecutionContext): Future[HttpResponse] =
    after(requestTimeout, actorSystem.scheduler)(Future.failed(new TimeoutException(s"Request to ${request.uri} failed after $requestTimeout")))
}

object OpenGraphFetcher {
  val defaultRequestTimeout: FiniteDuration = 3 seconds
}

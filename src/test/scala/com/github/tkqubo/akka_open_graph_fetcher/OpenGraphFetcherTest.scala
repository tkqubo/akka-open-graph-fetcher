package com.github.tkqubo.akka_open_graph_fetcher


import akka.actor.ActorSystem
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{LanguageRange, `Accept-Language`}
import akka.stream.ActorMaterializer
import com.miguno.akka.testing.VirtualTime
import org.specs2.matcher.Scope
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, TimeoutException}
import scalaz.syntax.std.all._

/**
  * Test class for [[OpenGraphFetcher]]
  * {{{
  * sbt "test-only com.github.tkqubo.akka_open_graph_fetcher.OpenGraphFetcherTest"
  * }}}
  */
// scalastyle:off magic.number
class OpenGraphFetcherTest
  extends Specification
  with Mockito {
  val timeout = 3 seconds

  trait Context extends Scope {
    val time = new VirtualTime
    implicit val system = ActorSystem("test")
    implicit val mat = ActorMaterializer()
    implicit val scheduler = time.scheduler
    val parser = mock[OpenGraphParser]
    val http = mock[HttpExt]
    val target = new OpenGraphFetcher(parser, timeout, http)(scheduler, mat)
  }

  "OpenGraphFetcher" should {
    "fetch" should {
      "pass" in new Context {
        // Given
        val url: String = "http://example.com"
        val headers: Seq[HttpHeader] = Seq(`Accept-Language`(LanguageRange.`*`))
        val request = HttpRequest(uri = Uri(url), headers = scala.collection.immutable.Seq(headers:_*))
        val response = HttpResponse(StatusCodes.OK, entity = mock[ResponseEntity])
        http.singleRequest(===(request), any, any, any)(any) returns Future.successful(response)
        val expected: OpenGraph = OpenGraph(url, "title".some, "desc".some, "image".some, None)
        parser.parse(request, response) returns Future.successful(expected)

        // When
        val actual: OpenGraph = Await.result(target.fetch(url, headers), Duration.Inf)

        // Then
        actual === expected
        there was one(http).singleRequest(any, any, any, any)(any)
        there was one(parser).parse(any, any)(any, any)
      }

      "return error response with malformed url" in new Context {
        // Given
        val url: String = "foo://example.com"
        val expected: OpenGraph = OpenGraph(
          url, error = Error.maybeFromStatusCode(StatusCodes.BadRequest, OpenGraphFetcher.ErrorMessage.forInvalidUriScheme().some)
        )

        // When
        val actual: OpenGraph = Await.result(target.fetch(url), Duration.Inf)

        // Then
        actual === expected
        there was no(http).singleRequest(any, any, any, any)(any)
        there was no(parser).parse(any, any)(any, any)
      }

      s"return timeout error after $timeout timeout" in new Context {
        // Given
        val url: String = "http://example.com"
        val request = HttpRequest(uri = Uri(url))
        val response = HttpResponse(StatusCodes.OK, entity = mock[ResponseEntity])
        http.singleRequest(===(request), any, any, any)(any) returns Future {
          Thread.sleep(timeout.toMillis)
          response
        }
        val expected: OpenGraph = OpenGraph(
          url, error = Error.fromThrowable(new TimeoutException(OpenGraphFetcher.ErrorMessage.forRequestTimeout(request, timeout))).some
        )
        val unexpected: OpenGraph = OpenGraph(url, "title".some, "desc".some, "image".some, None)
        parser.parse(request, response) returns Future.successful(unexpected)

        // When
        val eventualOpenGraph: Future[OpenGraph] = target.fetch(url)
        time.advance(timeout.toMillis + 1)
        val actual: OpenGraph = Await.result(eventualOpenGraph, Duration.Inf)

        // Then
        actual === expected
        there was one(http).singleRequest(any, any, any, any)(any)
      }
    }

    "apply" should {
      "pass" in {
        implicit val system = ActorSystem("test")
        implicit val mat = ActorMaterializer()
        val target = OpenGraphFetcher()
        target.requestTimeout === OpenGraphFetcher.defaultRequestTimeout
      }
    }
  }
}

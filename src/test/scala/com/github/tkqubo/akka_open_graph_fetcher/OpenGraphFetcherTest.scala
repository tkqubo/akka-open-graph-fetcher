package com.github.tkqubo.akka_open_graph_fetcher


import akka.actor.ActorSystem
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{LanguageRange, Location, `Accept-Language`}
import akka.stream.ActorMaterializer
import com.miguno.akka.testing.VirtualTime
import org.specs2.matcher.Scope
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import scala.collection.immutable.Seq

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
  val retries = 3

  trait Context extends Scope {
    val time = new VirtualTime
    implicit val system = ActorSystem("test")
    implicit val mat = ActorMaterializer()
    implicit val scheduler = time.scheduler
    val parser = mock[OpenGraphParser]
    val http = mock[HttpExt]
    val target = new OpenGraphFetcher(parser, timeout, retries, http)(scheduler, mat)
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

      "pass with non-ascii URL" in new Context {
        // Given
        val url: String = "http://example.com?日本語でおｋ"
        val response = HttpResponse(StatusCodes.OK, entity = mock[ResponseEntity])
        http.singleRequest(any, any, any, any)(any) returns Future.successful(response)
        val expected: OpenGraph = OpenGraph(url, "title".some, "desc".some, "image".some, None)
        parser.parse(any, any)(any, any) returns Future.successful(expected)

        // When
        val actual: OpenGraph = Await.result(target.fetch(url), Duration.Inf)

        // Then
        actual === expected
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

      s"return response after two redirection then 200 response" in new Context {
        // Given
        val url1: String = "http://old.example.com"
        val url2: String = "http://new.example.com"
        val url3: String = "http://another-new.example.com"
        val request1 = HttpRequest(uri = Uri(url1))
        val request2 = HttpRequest(uri = Uri(url2))
        val request3 = HttpRequest(uri = Uri(url3))
        val response1 = HttpResponse(
          status = StatusCodes.MovedPermanently, headers = Seq[HttpHeader](Location(Uri(url2))), entity = mock[ResponseEntity]
        )
        val response2 = HttpResponse(
          status = StatusCodes.TemporaryRedirect, headers = Seq[HttpHeader](Location(Uri(url3))), entity = mock[ResponseEntity]
        )
        val response3 = HttpResponse(StatusCodes.OK, entity = mock[ResponseEntity])
        http.singleRequest(===(request1), any, any, any)(any) returns Future.successful(response1)
        http.singleRequest(===(request2), any, any, any)(any) returns Future.successful(response2)
        http.singleRequest(===(request3), any, any, any)(any) returns Future.successful(response3)
        val expected: OpenGraph = OpenGraph(url = url3, title = "redirected".some)
        parser.parse(===(request1), ===(response3))(any, any) returns Future.successful(expected)

        // When
        val actual: OpenGraph = Await.result(target.fetch(url1), Duration.Inf)

        // Then
        actual === expected
        there was three(http).singleRequest(any, any, any, any)(any)
        there was one(parser).parse(any, any)(any, any)
      }

      s"return response after two redirections then 30x response but no more redirection" in new Context {
        // Given
        val url1: String = "http://old.example.com"
        val url2: String = "http://new.example.com"
        val url3: String = "http://another-new.example.com"
        val url4: String = "http://unreachable-new.example.com"
        val request1 = HttpRequest(uri = Uri(url1))
        val request2 = HttpRequest(uri = Uri(url2))
        val request3 = HttpRequest(uri = Uri(url3))
        val response1 = HttpResponse(
          status = StatusCodes.Found, headers = Seq[HttpHeader](Location(Uri(url2))), entity = mock[ResponseEntity]
        )
        val response2 = HttpResponse(
          status = StatusCodes.SeeOther, headers = Seq[HttpHeader](Location(Uri(url3))), entity = mock[ResponseEntity]
        )
        val response3 = HttpResponse(
          status = StatusCodes.UseProxy, headers = Seq[HttpHeader](Location(Uri(url4))), entity = mock[ResponseEntity]
        )
        http.singleRequest(===(request1), any, any, any)(any) returns Future.successful(response1)
        http.singleRequest(===(request2), any, any, any)(any) returns Future.successful(response2)
        http.singleRequest(===(request3), any, any, any)(any) returns Future.successful(response3)
        val expected: OpenGraph = OpenGraph(url = url3, title = "redirected".some)
        parser.parse(===(request1), ===(response3))(any, any) returns Future.successful(expected)

        // When
        val actual: OpenGraph = Await.result(target.fetch(url1), Duration.Inf)

        // Then
        actual === expected
        there was three(http).singleRequest(any, any, any, any)(any)
        there was one(parser).parse(any, any)(any, any)
      }

      s"return response after one redirection but with the same location" in new Context {
        // Given
        val url: String = "http://same.example.com"
        val request = HttpRequest(uri = Uri(url))
        val response = HttpResponse(
          status = StatusCodes.MovedPermanently, headers = Seq[HttpHeader](Location(Uri(url))), entity = mock[ResponseEntity]
        )
        http.singleRequest(===(request), any, any, any)(any) returns Future.successful(response)
        val expected: OpenGraph = OpenGraph(url = url, title = "redirected".some)
        parser.parse(===(request), ===(response))(any, any) returns Future.successful(expected)

        // When
        val actual: OpenGraph = Await.result(target.fetch(url), Duration.Inf)

        // Then
        actual === expected
        there was one(http).singleRequest(any, any, any, any)(any)
        there was one(parser).parse(any, any)(any, any)
      }

      s"return response after one redirection but with the same location recursively" in new Context {
        // Given
        val url1: String = "http://one.example.com"
        val url2: String = "http://another.example.com"
        val request1 = HttpRequest(uri = Uri(url1))
        val request2 = HttpRequest(uri = Uri(url2))
        val response1 = HttpResponse(
          status = StatusCodes.MovedPermanently, headers = Seq[HttpHeader](Location(Uri(url2))), entity = mock[ResponseEntity]
        )
        val response2 = HttpResponse(
          status = StatusCodes.TemporaryRedirect, headers = Seq[HttpHeader](Location(Uri(url1))), entity = mock[ResponseEntity]
        )
        http.singleRequest(===(request1), any, any, any)(any) returns Future.successful(response1)
        http.singleRequest(===(request2), any, any, any)(any) returns Future.successful(response2)
        val expected: OpenGraph = OpenGraph(url = url2, title = "redirected".some)
        parser.parse(===(request1), ===(response2))(any, any) returns Future.successful(expected)

        // When
        val actual: OpenGraph = Await.result(target.fetch(url1), Duration.Inf)

        // Then
        actual === expected
        there was two(http).singleRequest(any, any, any, any)(any)
        there was one(parser).parse(any, any)(any, any)
      }

      s"return 303 page when no location header present" in new Context {
        // Given
        val url: String = "http://old.example.com"
        val request = HttpRequest(uri = Uri(url))
        val response = HttpResponse(
          status = StatusCodes.SeeOther, entity = mock[ResponseEntity]
        )
        http.singleRequest(===(request), any, any, any)(any) returns Future.successful(response)
        val expected: OpenGraph = OpenGraph(url = url, title = "redirected".some)
        parser.parse(===(request), ===(response))(any, any) returns Future.successful(expected)

        // When
        val actual: OpenGraph = Await.result(target.fetch(url), Duration.Inf)

        // Then
        actual === expected
        there was one(http).singleRequest(any, any, any, any)(any)
        there was one(parser).parse(any, any)(any, any)
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

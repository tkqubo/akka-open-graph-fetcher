package com.github.tkqubo.akka_open_graph_fetcher

import java.nio.charset.Charset

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.`Content-Type`
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.FileIO
import org.specs2.matcher.Scope
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.io.{Codec, Source}
import scala.util.Random
import scala.xml.Elem
import scalaz.syntax.std.all._

/**
  * Test class for [[OpenGraphParser]]
  * {{{
  * sbt "test-only com.github.tkqubo.akka_open_graph_fetcher.OpenGraphParserTest"
  * }}}
  */
// scalastyle:off magic.number
class OpenGraphParserTest
  extends Specification
  with Mockito {

  private class Context extends Scope {
    implicit val system = ActorSystem("test")
    implicit val mat = ActorMaterializer()
    val target = new OpenGraphParser()
    val url: String = s"""http://example.com?=${Random.alphanumeric.take(20).mkString("")}"""

    def buildRequest(headers: HttpHeader*): HttpRequest =
      HttpRequest(uri = Uri(url), headers = scala.collection.immutable.Seq(headers:_*))
    def buildResponse(html: Elem, statusCode: StatusCode = StatusCodes.OK): HttpResponse =
      HttpResponse(entity = HttpEntity(html.toString()))
  }

  private class ShiftJisContext extends Context {
    val title = "日本語でOK"
    val description = "世界最大規模"
    val request = buildRequest()
    def buildShiftJisResponse(additionalTag: Elem, headers: HttpHeader*): HttpResponse = {
      val html = <html><head>
        {additionalTag}
        <meta property="og:title" content={title} />
        <meta property="og:description" content={description} />
      </head></html>
      HttpResponse(
        entity = HttpEntity(html.toString().getBytes(Charset.forName("Shift-JIS"))),
        headers = scala.collection.immutable.Seq(headers:_*)
      )
    }
  }

  "OpenGraphParser" should {
    "parse" should {
      "pass empty text" in new Context {
        // Given
        val request = buildRequest()
        val response = HttpResponse(entity = HttpEntity.Empty)

        // When
        val actual = Await.result(target.parse(request, response), Duration.Inf)

        // Then
        actual === OpenGraph(url)
      }

      "parse open graph" in new Context {
        // Given
        val overridingUrl = "http://example.com/new"
        val title = "Hello world"
        val description = "This is the very first time"
        val image = "http://example.com/image.png"
        val request = buildRequest()
        val response = buildResponse(<html><head>
          <meta property="og:url" content={overridingUrl} />
          <meta property="og:title" content={title} />
          <meta property="og:description" content={description} />
          <meta property="og:image" content={image} />
        </head></html>)

        // When
        val actual = Await.result(target.parse(request, response), Duration.Inf)

        // Then
        actual === OpenGraph(overridingUrl, title = title.some, description = description.some, image.some)
      }

      "still parse failed response" in new Context {
        // Given
        val title = "Hello world"
        val request = buildRequest()
        val response = buildResponse(<meta property="og:title" content={title} />, StatusCodes.BadRequest)

        // When
        val actual = Await.result(target.parse(request, response), Duration.Inf)

        // Then
        actual === OpenGraph(url, title = title.some)
      }

      "parse html other than UTF-8 with HTML4" in new ShiftJisContext {
        // Given
        val response = buildShiftJisResponse(<meta http-equiv="Content-Type" content="text/html; charset=Shift_JIS" />)
        // When
        val actual = Await.result(target.parse(request, response), Duration.Inf)
        // Then
        actual === OpenGraph(url, title = title.some, description = description.some)
      }

      "parse html other than UTF-8 with HTML5" in new ShiftJisContext {
        // Given
        val response = buildShiftJisResponse(<meta charset="Shift_JIS" />)
        // When
        val actual = Await.result(target.parse(request, response), Duration.Inf)
        // Then
        actual === OpenGraph(url, title = title.some, description = description.some)
      }

      "not parse html with wrong charset specified" in new ShiftJisContext {
        // Given
        val response = buildShiftJisResponse(<meta charset="UTF-8" />)
        // When
        val actual = Await.result(target.parse(request, response), Duration.Inf)
        // Then
        actual !== OpenGraph(url, title = title.some, description = description.some)
      }

      "not parse html with no charset specified" in new ShiftJisContext {
        // Given
        val response = buildShiftJisResponse(<dummy />)
        // When
        val actual = Await.result(target.parse(request, response), Duration.Inf)
        // Then
        actual !== OpenGraph(url, title = title.some, description = description.some)
      }

      "parse html with charset specified in response header" in new ShiftJisContext {
        // Given
        val response = buildShiftJisResponse(<dummy />, `Content-Type`(ContentType(MediaTypes.`text/html`, HttpCharset.custom("Shift_JIS"))))
        // When
        val actual = Await.result(target.parse(request, response), Duration.Inf)
        // Then
        actual === OpenGraph(url, title = title.some, description = description.some)
      }

      "parse image" in new Context {
        // Given
        val title = "Nobody expects this title!"
        val request = buildRequest()
        val response = HttpResponse(
          entity = HttpEntity(ContentType(MediaTypes.`image/png`), <meta property="og:title" content={title} />.toString().getBytes)
        )

        // When
        val actual = Await.result(target.parse(request, response), Duration.Inf)

        // Then
        actual === OpenGraph(url, image = url.some)
      }
    }
  }
}

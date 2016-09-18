package com.github.tkqubo.akka_open_graph_fetcher

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.StatusCodes.ClientError
import org.specs2.mutable.Specification
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.TimeoutException

/**
  * Test class for [[Error]]
  * {{{
  * sbt "test-only com.github.tkqubo.akka_http_og_fetcher.ErrorTest"
  * }}}
  */
// scalastyle:off magic.number
class ErrorTest extends Specification {
  "Error" should {
    val errorMessage: Option[String] = Some("error")
    "maybeFromStatusCode" should {
      "return None" in {
        Error.maybeFromStatusCode(StatusCodes.OK, errorMessage) === None
      }
      "return Error instance" in {
        val requestTimeout: ClientError = StatusCodes.RequestTimeout
        Error.maybeFromStatusCode(requestTimeout, errorMessage) === Some(Error(requestTimeout.intValue, errorMessage))
        Error.maybeFromStatusCode(requestTimeout) === Some(Error(requestTimeout.intValue))
      }
    }

    "fromThrowable" should {
      "return Error with 408 status code" in {
        Error.fromThrowable(new TimeoutException(), errorMessage) === Error(StatusCodes.RequestTimeout.intValue, errorMessage)
        Error.fromThrowable(new TimeoutException()) === Error(StatusCodes.RequestTimeout.intValue)
      }
      "return Error with 503 status code" in {
        Seq(new IllegalArgumentException, new RuntimeException, new OutOfMemoryError())
          .forall(Error.fromThrowable(_) == Error(StatusCodes.ServiceUnavailable.intValue))
      }
    }

    "rootJsonFormat" should {
      "pass" in {
        val error = Error(StatusCodes.BadRequest.intValue, errorMessage)
        val json =
          s"""
            |{
            |  "status_code": ${StatusCodes.BadRequest.intValue},
            |  "message": ${error.message.get.toJson}
            |}
          """.stripMargin.parseJson
        Error.rootJsonFormat.write(error).prettyPrint === json.prettyPrint
        Error.rootJsonFormat.read(json) === error
      }
    }
  }
}

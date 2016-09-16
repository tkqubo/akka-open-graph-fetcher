package com.github.tkqubo.akka_http_og_fetcher

import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.TimeoutException

case class Error(
  statusCode: Int,
  message: Option[String] = None
)

object Error {
  def maybeFromStatusCode(status: StatusCode, message: => Option[String] = None): Option[Error] =
    if (status.isSuccess()) None else Some(Error(status.intValue(), message))

  def fromThrowable(throwable: Throwable, message: => Option[String] = None): Error = throwable match {
    case _: TimeoutException => Error(StatusCodes.RequestTimeout.intValue, message)
    case _ => Error(StatusCodes.ServiceUnavailable.intValue, message)
  }

  implicit val rootJsonFormat: RootJsonFormat[Error] = DefaultJsonProtocol.jsonFormat(Error.apply, "status_code", "message")
}

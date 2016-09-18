package com.github.tkqubo.akka_open_graph_fetcher

import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.TimeoutException

/**
  * Represents an error on open graph retrieval
  * @param statusCode
  * @param message
  */
case class Error(
  /** status code */
  statusCode: Int,
  /** optional error message */
  message: Option[String] = None
)

/**
  * Companion object of [[Error]]
  */
object Error {
  /**
    * Creates optional [[Error]] instance from `StatusCode`, where return value might be `None` if `StatusCode.isSuccess()` is `true`
    * @param status
    * @param message
    * @return
    */
  def maybeFromStatusCode(status: StatusCode, message: => Option[String] = None): Option[Error] =
    if (status.isSuccess()) None else Some(Error(status.intValue(), message))

  /**
    * Creates [[Error]] instance from `Throwable`, whose status code will be `408` on `TimeoutException`, and `503` otherwise.
    * @param throwable
    * @param message
    * @return
    */
  def fromThrowable(throwable: Throwable, message: => Option[String] = None): Error = throwable match {
    case _: TimeoutException => Error(StatusCodes.RequestTimeout.intValue, message)
    case _ => Error(StatusCodes.ServiceUnavailable.intValue, message)
  }

  /** JSON formatter for [[Error]] */
  implicit val rootJsonFormat: RootJsonFormat[Error] = DefaultJsonProtocol.jsonFormat(Error.apply, "status_code", "message")
}

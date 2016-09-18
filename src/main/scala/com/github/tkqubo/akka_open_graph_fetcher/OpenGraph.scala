package com.github.tkqubo.akka_open_graph_fetcher

import spray.json.DefaultJsonProtocol._
import spray.json._

/**
  * Representa open graph data structure
  * @param url
  * @param title
  * @param description
  * @param image
  * @param error
  */
case class OpenGraph(
  url: String,
  title: Option[String] = None,
  description: Option[String] = None,
  image: Option[String] = None,
  error: Option[Error] = None
)

object OpenGraph {
  /** JSON formatter for [[OpenGraph]] */
  implicit val rootJsonFormat: RootJsonFormat[OpenGraph] =
    DefaultJsonProtocol.jsonFormat(OpenGraph.apply, "url", "title", "description", "image", "error")
}

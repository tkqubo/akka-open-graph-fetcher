package com.github.tkqubo.akka_open_graph_fetcher

import spray.json.DefaultJsonProtocol._
import spray.json._

case class OpenGraph(
  url: String,
  title: Option[String] = None,
  description: Option[String] = None,
  image: Option[String] = None,
  error: Option[Error] = None
)

object OpenGraph {
  implicit val rootJsonFormat: RootJsonFormat[OpenGraph] =
    DefaultJsonProtocol.jsonFormat(OpenGraph.apply, "url", "title", "description", "image", "error")
}

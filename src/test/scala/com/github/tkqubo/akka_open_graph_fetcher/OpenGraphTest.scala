package com.github.tkqubo.akka_open_graph_fetcher

import org.specs2.mutable.Specification
import spray.json.DefaultJsonProtocol._
import spray.json._

/**
  * Test class for [[OpenGraph]]
  * {{{
  * sbt "test-only com.github.tkqubo.akka_http_og_fetcher.OpenGraphTest"
  * }}}
  */
// scalastyle:off magic.number
class OpenGraphTest extends Specification {
  "OpenGraph" should {
    "rootJsonFormat" should {
      "format" in {
        val openGraph = OpenGraph(
          url = "this is url",
          title = Some("this is title"),
          description = Some("this is description"),
          image = Some("this is image"),
          error = Some(Error(42, Some("error")))
        )
        val json =
          s"""
            |{
            |  "url": ${openGraph.url.toJson},
            |  "title": ${openGraph.title.toJson},
            |  "description": ${openGraph.description.toJson},
            |  "image": ${openGraph.image.toJson},
            |  "error": ${openGraph.error.toJson}
            |}
          """.stripMargin.parseJson
        OpenGraph.rootJsonFormat.write(openGraph).prettyPrint === json.prettyPrint
        OpenGraph.rootJsonFormat.read(json) === openGraph
      }
    }
  }
}

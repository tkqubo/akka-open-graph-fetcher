# Akka Open Graph Fetcher

[![License: MIT](http://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Circle CI](https://img.shields.io/circleci/project/tkqubo/akka-open-graph-fetcher/master.svg)](https://circleci.com/gh/tkqubo/akka-open-graph-fetcher)
[![Coverage Status](https://coveralls.io/repos/tkqubo/akka-open-graph-fetcher/badge.svg?branch=master&service=github)](https://coveralls.io/github/tkqubo/akka-open-graph-fetcher?branch=master)

## About

Tiny library which fetches open graph data from the specified URL, powered by akka-http client

## Usage

```scala
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import com.github.tkqubo.akka_open_graph_fetcher._

implicit val system = ActorSystem("console")
implicit val mat = ActorMaterializer()

val fetcher = OpenGraphFetcher()
val future = fetcher.fetch("https://coveralls.io/github/tkqubo/akka-open-graph-fetcher?branch=master")
val openGraph = Await.result(future, Duration.Inf)

println(openGraph.url)
println(openGraph.title)
println(openGraph.description)
println(openGraph.image)
println(openGraph.error)

// https://coveralls.io/github/tkqubo/akka-open-graph-fetcher?branch=master
// Some(Coveralls.io - Test Coverage History and Statistics)
// Some(The leading provider of test coverage analytics. Ensure that all your new code is fully covered, and see coverage trends emerge. Works with most CI services. Always free for open source.)
// Some(/social.png)
// None
```

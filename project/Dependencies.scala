import sbt._

object Dependencies {
  object Versions {
    val akka      = "2.4.16"
    val akkaHttp  = "10.0.3"
    val specs2    = "3.8.7"
    val jsoup     = "1.10.2"
    val mockScheduler: String = "0.5.1"
  }

  val dependencies: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %%  "akka-actor"            % Versions.akka,
    "com.typesafe.akka" %%  "akka-slf4j"            % Versions.akka,
    "com.typesafe.akka" %%  "akka-testkit"          % Versions.akka % Test,
    "com.typesafe.akka" %%  "akka-http"             % Versions.akkaHttp,
    "com.typesafe.akka" %%  "akka-http-core"        % Versions.akkaHttp,
    "com.typesafe.akka" %%  "akka-http-testkit"     % Versions.akkaHttp % Test,
    "com.typesafe.akka" %%  "akka-http-spray-json"  % Versions.akkaHttp,
    "com.miguno.akka"   %%  "akka-mock-scheduler"   % Versions.mockScheduler % Test,
    "org.jsoup"         %   "jsoup"                 % Versions.jsoup,
    "org.specs2"        %%  "specs2-core"           % Versions.specs2 % Test,
    "org.specs2"        %%  "specs2-matcher"        % Versions.specs2 % Test,
    "org.specs2"        %%  "specs2-matcher-extra"  % Versions.specs2 % Test,
    "org.specs2"        %%  "specs2-mock"           % Versions.specs2 % Test
  )
}
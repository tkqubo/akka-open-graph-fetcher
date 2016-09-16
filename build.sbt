organization := "com.github.tkqubo"

name := "akka-open-graph-fetcher"

scalaVersion := "2.11.8"

val specs2Ver = "3.7.3"

libraryDependencies ++= Seq(
  "org.specs2" %% "specs2-core"           % specs2Ver % Test,
  "org.specs2" %% "specs2-matcher"        % specs2Ver % Test,
  "org.specs2" %% "specs2-matcher-extra"  % specs2Ver % Test,
  "org.specs2" %% "specs2-mock"           % specs2Ver % Test
)

javaOptions in Test ++= Seq(
  s"-Djava.util.Arrays.useLegacyMergeSort=true"
)

initialCommands := "import com.github.tkqubo.akka_open_graph_fetcher._"

// sbt publish
publishArtifact in Test := false
publishMavenStyle := true
pomIncludeRepository := { _ => false }
pomExtra := (
  <url>https://github.com/tkqubo/akka-open-graph-fetcher</url>
    <licenses>
      <license>
        <name>MIT</name>
        <url>http://opensource.org/licenses/MIT</url>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:tkqubo/akka-open-graph-fetcher.git</url>
      <connection>scm:git:github.com/tkqubo/akka-open-graph-fetcher.git</connection>
      <developerConnection>scm:git:git@github.com:tkqubo/akka-open-graph-fetcher.git</developerConnection>
    </scm>
    <developers>
      <developer>
        <id>tkqubo</id>
        <name>Takaichi Kubo</name>
        <url>https://github.com/tkqubo</url>
      </developer>
    </developers>
  )
publishTo <<= version { (v: String) =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT")) {
    Some("snapshots" at nexus + "content/repositories/snapshots")
  } else {
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }
}
useGpg := true

// sbt-release
releaseVersionBump := sbtrelease.Version.Bump.Bugfix
releasePublishArtifactsAction := PgpKeys.publishSigned.value

// sbt-ghpages
site.settings
site.includeScaladoc()
ghpages.settings
git.remoteRepo := "git@github.com:tkqubo/akka-open-graph-fetcher.git"

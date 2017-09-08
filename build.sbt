name := "solr-client"

organization := "nl.elmar"

version := "0.4"

scalaVersion := "2.12.2"

val `akka-http-version` = "10.0.5"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % "2.6.0",
  "com.typesafe.akka" %% "akka-http" % `akka-http-version`,
  "org.scalacheck" %% "scalacheck" % "1.13.4" % "test",
  "org.specs2" %% "specs2-core" % "3.8.9" % "test",
  "org.apache.solr" % "solr-core" % "6.5.0" % "test",
  "org.slf4j" % "slf4j-api" % "1.7.25" % "test",
  "ch.qos.logback" % "logback-classic" % "1.2.3" % "test",
  "com.typesafe.akka" %% "akka-http-testkit" % `akka-http-version` % "test"
)

homepage := Some(url("https://github.com/elmarreizen/solr-client"))

licenses := Seq("Apache 2.0" -> url("http://opensource.org/licenses/Apache-2.0"))

scmInfo := Some(
  ScmInfo(
    url("https://github.com/elmarreizen/solr-client"),
    "scm:git@github.com:elmarreizen/solr-client.git"
  )
)

developers := List(
  Developer(
    id    = "lavrov",
    name  = "Vitaly Lavrov",
    email = "",
    url   = url("https://github.com/lavrov")
  )
)

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false
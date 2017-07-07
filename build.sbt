name := "solr-client"

organization := "nl.elmar"

version := "0.1"

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

name := "solr-client"

organization := "nl.elmar"

scalaVersion := "2.13.1"
crossScalaVersions := Seq("2.12.8", scalaVersion.value)

val `akka-http-version` = "10.1.10"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % "2.7.4",
  "com.typesafe.akka" %% "akka-http" % `akka-http-version`,
  "com.typesafe.akka" %% "akka-stream" % "2.5.25",
  "org.specs2" %% "specs2-core" % "4.7.1" % "test",
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

releasePublishArtifactsAction := PgpKeys.publishSigned.value

import ReleaseTransformations._

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  publishArtifacts,
  releaseStepCommand(Sonatype.SonatypeCommand.sonatypeRelease),
  setNextVersion,
  commitNextVersion,
  pushChanges
)

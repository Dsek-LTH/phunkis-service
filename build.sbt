name := "phunkis-service"

version := "0.1"

scalaVersion := "2.12.6"

scapegoatVersion in ThisBuild := "1.3.8"

libraryDependencies ++= Seq(
  "org.sangria-graphql" %% "sangria" % "1.4.2",
  "org.sangria-graphql" %% "sangria-slowlog" % "0.1.8",
  "org.sangria-graphql" %% "sangria-circe" % "1.2.1",

  "com.typesafe.akka" %% "akka-http" % "10.1.3",
  "de.heikoseeberger" %% "akka-http-circe" % "1.21.0",

  "io.circe" %%	"circe-core" % "0.9.3",
  "io.circe" %% "circe-parser" % "0.9.3",
  "io.circe" %% "circe-optics" % "0.9.3",

  "mysql" % "mysql-connector-java" % "5.1.46",
  "io.getquill" %% "quill-jdbc" % "2.5.4",
  "org.slf4j" % "slf4j-api" % "1.7.25",
  "org.slf4j" % "slf4j-log4j12" % "1.7.25",

  "com.typesafe" % "config" % "1.3.2",

  "com.github.pathikrit" %% "better-files" % "3.7.0",

  "org.scalatest" %% "scalatest" % "3.0.5" % Test
)

addCommandAlias("lint", ";scapegoat;scalastyle")
addCommandAlias("init", "runMain se.dsek.phunkisservice.db.DBUtil")
mainClass in (Compile, packageBin) := Some("se.dsek.phunkisservice.PhunkisService")
mainClass in (Compile, run) := Some("se.dsek.phunkisservice.PhunkisService")

enablePlugins(DockerComposePlugin)
enablePlugins(DockerPlugin)

dockerfile in docker := {
  // The assembly task generates a fat JAR file
  val artifact: File = assembly.value
  val artifactTargetPath = s"/app/${artifact.name}"

  new Dockerfile {
    from("openjdk:8-jre")
    add(artifact, artifactTargetPath)
    entryPoint("java", "-jar", artifactTargetPath)
  }
}

imageNames in docker := Seq(ImageName(
  repository = name.value.toLowerCase,
  tag = Some(version.value))
)

dockerImageCreationTask := docker.value


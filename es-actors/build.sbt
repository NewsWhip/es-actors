import Settings._
import sbt._

lazy val root = project.root
  .setName("ES Actors")
  .setDescription("ES Actors")
  .setInitialCommand("_")
  .configureRoot
  .aggregate(common, client, server)

lazy val common = project.from("common")
  .setName("common")
  .setDescription("Common utilities")
  .setInitialCommand("_")
  .configureModule

lazy val client = project.from("client")
  .setName("client")
  .setDescription("Client project")
  .setInitialCommand("_")
  .configureModule
  .configureIntegrationTests
  .configureFunctionalTests
  .configureUnitTests
  .dependsOnProjects(common)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    mainClass in(Compile, run) := Some("com.broilogabriel.Client"),
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := Settings.defaultOrg,
    libraryDependencies += "org.elasticsearch" % "elasticsearch" % "1.7.5"
  )

lazy val server = project.from("server")
  .setName("server")
  .setDescription("Server project")
  .setInitialCommand("_")
  .configureModule
  .configureIntegrationTests
  .configureFunctionalTests
  .configureUnitTests
  .dependsOnProjects(common)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    mainClass in(Compile, run) := Some("com.broilogabriel.Server"),
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := Settings.defaultOrg,
    libraryDependencies += "org.elasticsearch" % "elasticsearch" % "1.7.5"
  )




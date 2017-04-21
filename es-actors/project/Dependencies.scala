import Dependencies._
import sbt._

object Dependencies {

  // scala version
  val scalaVersion = "2.12.2"

  // resolvers
  val resolvers = Seq(
    Resolver.sonatypeRepo("public")
  )

  // elasticsearch
  val elasticsearch: ModuleID = "org.elasticsearch" % "elasticsearch" % "2.4.1"
  val elastic4sCore: ModuleID = "com.sksamuel.elastic4s" % "elastic4s-core_2.11" % "2.4.0"
  val elastic4sStreams: ModuleID = "com.sksamuel.elastic4s" % "elastic4s-streams_2.11" % "2.4.0"
  val elastic4s: Seq[ModuleID] = Seq(elastic4sCore, elastic4sStreams, elasticsearch)

  // akka
  val akkaActor: ModuleID = "com.typesafe.akka" %% "akka-actor" % "2.5.0"
  val akkaRemote: ModuleID = "com.typesafe.akka" %% "akka-remote" % "2.5.0"
  val akkaStream: ModuleID = "com.typesafe.akka" %% "akka-stream" % "2.5.0"
  val akkaHttp: ModuleID = "com.typesafe.akka" %% "akka-http" % "10.0.5"
  //  val akkaKyro: ModuleID = "com.github.romix.akka" % "akka-kryo-serialization_2.12" % "0.5.2"
  //  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % "2.4.11" % "test"
  val akka: Seq[ModuleID] = Seq(akkaActor, akkaRemote, akkaStream, akkaHttp)

  // functional utils
  val scalazCore: ModuleID = "org.scalaz" %% "scalaz-core" % "7.2.10"
  val scalazConcurrent: ModuleID = "org.scalaz" %% "scalaz-concurrent" % "7.2.10"
  val scalazContrib: ModuleID = "org.typelevel" %% "scalaz-contrib-210" % "0.2" excludeAll ExclusionRule("org.scalaz")
  val scalaz: Seq[ModuleID] = Seq(scalazCore, scalazConcurrent, scalazContrib)


  // util
  val jodaTime: ModuleID = "joda-time" % "joda-time" % "2.9.4"
  val jodaConvert: ModuleID = "org.joda" % "joda-convert" % "1.8"

  // command line
  val scopt: ModuleID = "com.github.scopt" %% "scopt" % "3.5.0"

  // logging
  val logback: ModuleID = "ch.qos.logback" % "logback-classic" % "1.1.7"
  val scalaLogging: ModuleID = "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0"
  val log: Seq[ModuleID] = Seq(logback, scalaLogging)


  // testing
  val mockito: ModuleID = "org.mockito" % "mockito-core" % "2.7.22"
  //  val spec2Core: ModuleID = "org.specs2" % "specs2-core_2.12" % "3.8.9"
  //  val spec2: ModuleID = "org.specs2" %% "specs2" % "3.7"
  //  val spec2JUnit: ModuleID = "org.specs2" % "specs2-junit_2.11" % "3.8.9"
}

trait Dependencies {

  val scalaVersionUsed: String = scalaVersion

  // resolvers
  val commonResolvers: Seq[Resolver] = resolvers

  val mainDeps: Seq[ModuleID] = Seq(scopt, jodaTime, jodaConvert) ++:
    elastic4s ++:
    akka ++:
    //    scalaz ++:
    log

  val testDeps = Seq(mockito)

  implicit class ProjectRoot(project: Project) {

    def root: Project = project in file(".")
  }

  implicit class ProjectFrom(project: Project) {

    private val commonDir = "modules"

    def from(dir: String): Project = project in file(s"$commonDir/$dir")
  }

  implicit class DependsOnProject(project: Project) {

    val dependsOnCompileAndTest = "test->test;compile->compile"

    def dependsOnProjects(projects: Project*): Project =
      project dependsOn (projects.map(_ % dependsOnCompileAndTest): _*)
  }

}

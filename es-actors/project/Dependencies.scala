import Dependencies._
import sbt._

object Dependencies {

  // scala version
  val scalaVersion = "2.11.8"

  // resolvers
  val resolvers = Seq(
    Resolver.sonatypeRepo("public")
  )

  // akka
  val akkaActor: ModuleID = "com.typesafe.akka" % "akka-actor_2.11" % "2.4.17"
  val akkaRemote: ModuleID = "com.typesafe.akka" % "akka-remote_2.11" % "2.4.17"
  //  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % "2.4.11" % "test"

  // functional utils
  val scalaz: ModuleID = "org.scalaz" %% "scalaz-core" % "7.2.6"
  val scalazConcurrent: ModuleID = "org.scalaz" %% "scalaz-concurrent" % "7.2.6"
  val scalazContrib: ModuleID = "org.typelevel" %% "scalaz-contrib-210" % "0.2" excludeAll ExclusionRule("org.scalaz")

  // util
  val jodaTime: ModuleID = "joda-time" % "joda-time" % "2.9.4"
  val jodaConvert: ModuleID = "org.joda" % "joda-convert" % "1.8"

  // command line
  val scopt: ModuleID = "com.github.scopt" %% "scopt" % "3.5.0"

  // logging
  val logback: ModuleID = "ch.qos.logback" % "logback-classic" % "1.1.7"
  val scalaLogging: ModuleID = "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0"

  // testing
  val mockito: ModuleID = "org.mockito" % "mockito-core" % "1.10.19"
  val spec2: ModuleID = "org.specs2" %% "specs2" % "3.7"
  val spec2Core: ModuleID = "org.specs2" %% "specs2-core" % "3.8.5"
  val spec2JUnit: ModuleID = "org.specs2" %% "specs2-junit" % "3.8.5"
}

trait Dependencies {

  val scalaVersionUsed: String = scalaVersion

  // resolvers
  val commonResolvers: Seq[Resolver] = resolvers

  val mainDeps = Seq(scalaz, scalazConcurrent, scalazContrib, scopt, logback, scalaLogging, akkaActor, akkaRemote,
    jodaTime, jodaConvert)

  val testDeps = Seq(mockito, spec2, spec2Core, spec2JUnit)

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

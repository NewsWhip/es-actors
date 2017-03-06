import com.typesafe.sbt.SbtScalariform._
import org.scalastyle.sbt.ScalastylePlugin._
import sbt.Defaults.testTasks
import sbt.Keys._
import sbt.TestFrameworks.Specs2
import sbt.Tests.Argument
import sbt._
import sbt.{ IntegrationTest => _ }
import scoverage.ScoverageKeys._
import scoverage.ScoverageSbtPlugin

import scalariform.formatter.preferences._

object Settings extends Dependencies {

  val defaultOrg = "com.broilogabriel"

  private val integrationTestTag = TestTag.IntegrationTest
  val IntegrationTest: sbt.Configuration = config(integrationTestTag) extend Test describedAs "Integration Tests"

  private val functionalTestTag = TestTag.FunctionalTest
  val FunctionalTest: sbt.Configuration = config(functionalTestTag) extend Test describedAs "Functional Tests"

  private val unitTestTag = TestTag.UnitTest
  val UnitTest: sbt.Configuration = config(unitTestTag) extend Test describedAs "Unit Tests"

  private val disabledTestTag = TestTag.DisabledTest

  private val commonSettings = Seq(
    organization := defaultOrg,
    version := "1.3.28-kibana",
    scalaVersion := scalaVersionUsed
  )

  private val rootSettings = commonSettings

  private val modulesSettings = scalariformSettings ++ commonSettings ++ Seq(
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-feature",
      "-language:existentials",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-language:postfixOps",
      "-Ywarn-dead-code",
      "-Ywarn-infer-any",
      "-Ywarn-unused-import",
      "-Xfatal-warnings",
      "-Xlint"
    ),

    resolvers ++= commonResolvers,

    libraryDependencies ++= mainDeps,
    libraryDependencies ++= testDeps map (_ % "test"),

    testOptions in Test += excludeTags(disabledTestTag),
    coverageEnabled := false,

    ScalariformKeys.preferences := ScalariformKeys.preferences.value
//      .setPreference(AlignArguments, false)
//      .setPreference(AlignParameters, false)
//      .setPreference(AlignSingleLineCaseStatements, false)
//      .setPreference(DoubleIndentClassDeclaration, false)
//      .setPreference(IndentLocalDefs, false)
//      .setPreference(PreserveSpaceBeforeArguments, true)
      .setPreference(SpacesWithinPatternBinders, false)
    , scalastyleFailOnError := true

  )

  private def excludeTags(tags: String*) = Argument(Specs2, "exclude", tags.reduce(_ + "," + _))

  private def includeTags(tags: String*) = Argument(Specs2, "include", tags.reduce(_ + "," + _))

  private def sequential = Argument(Specs2, "sequential")

  abstract class Configurator(project: Project, config: Configuration, tag: String) {

    protected def configure: Project = project.
      configs(config).
      settings(inConfig(config)(testTasks): _*).
      settings(testOptions in config := Seq(includeTags(tag))).
      settings(libraryDependencies ++= testDeps map (_ % tag)).
      enablePlugins(ScoverageSbtPlugin)

    protected def configureSequential: Project = configure.
      settings(testOptions in config ++= Seq(sequential)).
      settings(parallelExecution in config := false)
  }

  implicit class DataConfigurator(project: Project) {

    def setName(newName: String): Project = project.settings(name := newName)

    def setDescription(newDescription: String): Project = project.settings(description := newDescription)

    def setInitialCommand(newInitialCommand: String): Project =
      project.settings(initialCommands := s"com.broilogabriel.$newInitialCommand")
  }

  implicit class RootConfigurator(project: Project) {

    def configureRoot: Project = project.settings(rootSettings: _*)
  }

  implicit class ModuleConfigurator(project: Project) {

    def configureModule: Project = project.settings(modulesSettings: _*)
  }

  implicit class IntegrationTestConfigurator(project: Project)
    extends Configurator(project, IntegrationTest, integrationTestTag) {

    def configureIntegrationTests: Project = configureSequential
  }

  implicit class FunctionalTestConfigurator(project: Project)
    extends Configurator(project, FunctionalTest, functionalTestTag) {

    def configureFunctionalTests: Project = configure
  }

  implicit class UnitTestConfigurator(project: Project)
    extends Configurator(project, UnitTest, unitTestTag) {

    def configureUnitTests: Project = configure
  }

}

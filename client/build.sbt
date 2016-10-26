name := "es-client"

version := "1.1.0"

scalaVersion := "2.11.8"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.11"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.4.11" % "test"

libraryDependencies += "com.typesafe.akka" %% "akka-remote" % "2.4.11"

libraryDependencies += "org.elasticsearch" % "elasticsearch" % "1.7.5"

libraryDependencies += "com.github.scopt" %% "scopt" % "3.5.0"
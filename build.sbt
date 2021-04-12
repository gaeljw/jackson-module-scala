import java.io.File
import com.typesafe.tools.mima.core._

// Basic facts
name := "jackson-module-scala"

organization := "com.fasterxml.jackson.module"

scalaVersion := "2.13.5"

crossScalaVersions := Seq("2.11.12", "2.12.13", "2.13.5", "3.0.0-RC2")

mimaPreviousArtifacts := {
  if (isDotty.value)
    Set.empty
  else
    Set(organization.value %% name.value % "2.12.1")
}

resolvers += Resolver.sonatypeRepo("snapshots")

val scalaMajorVersion = SettingKey[Int]("scalaMajorVersion")
scalaMajorVersion := {
  val v = scalaVersion.value
  CrossVersion.partialVersion(v).map(_._2.toInt).getOrElse {
    throw new RuntimeException(s"could not get Scala major version from $v")
  }
}

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature")

// Temporarily disable warnings as error since SerializationFeature.WRITE_NULL_MAP_VALUES has been deprecated
// and we use it.
//scalacOptions in (Compile, compile) += "-Xfatal-warnings"

Compile / unmanagedSourceDirectories ++= {
  if (isDotty.value) {
    Seq(
      (baseDirectory in LocalRootProject).value / "src" / "main" / "scala-2.13",
      (baseDirectory in LocalRootProject).value / "src" / "main" / "scala-3.0"
    )
  } else {
    Seq(
      (baseDirectory in LocalRootProject).value / "src" / "main" / "scala-2.+",
      (baseDirectory in LocalRootProject).value / "src" / "main" / s"scala-2.${scalaMajorVersion.value}"
    )
  }
}

Test / unmanagedSourceDirectories += {
  val suffix = if (isDotty.value) "3.0" else "2.+"
  (LocalRootProject / baseDirectory).value / "src" / "test" / s"scala-${suffix}"
}

val jacksonVersion = "2.13.0-SNAPSHOT"
val jacksonJsonSchemaVersion = "2.12.3"

libraryDependencies ++= Seq(
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.thoughtworks.paranamer" % "paranamer" % "2.8",
  // test dependencies
  "com.fasterxml.jackson.datatype" % "jackson-datatype-joda" % jacksonVersion % Test,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-guava" % jacksonVersion % Test,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % jacksonVersion % Test,
  "com.fasterxml.jackson.module" % "jackson-module-jsonSchema" % jacksonJsonSchemaVersion % Test,
  "io.swagger" % "swagger-core" % "1.6.2" % Test,
  "org.scalatest" %% "scalatest" % "3.2.7" % Test
)

// build.properties
Compile / resourceGenerators += Def.task {
    val file = (Compile / resourceManaged).value / "com" / "fasterxml" / "jackson" / "module" / "scala" / "build.properties"
    val contents = "version=%s\ngroupId=%s\nartifactId=%s\n".format(version.value, organization.value, name.value)
    IO.write(file, contents)
    Seq(file)
}.taskValue

// site
enablePlugins(SiteScaladocPlugin)
enablePlugins(GhpagesPlugin)
git.remoteRepo := "git@github.com:FasterXML/jackson-module-scala.git"

mimaBinaryIssueFilters ++= Seq(
  ProblemFilters.exclude[ReversedMissingMethodProblem]("com.fasterxml.jackson.module.scala.util.ClassW.isScalaObject"),
  ProblemFilters.exclude[IncompatibleResultTypeProblem]("com.fasterxml.jackson.module.scala.deser.UntypedObjectDeserializerResolver.findBeanDeserializer"),
  ProblemFilters.exclude[MissingClassProblem]("com.fasterxml.jackson.module.scala.deser.UntypedObjectDeserializer*"),
  ProblemFilters.exclude[DirectMissingMethodProblem]("com.fasterxml.jackson.module.scala.introspect.BeanIntrospector.apply"),
  ProblemFilters.exclude[DirectMissingMethodProblem]("com.fasterxml.jackson.module.scala.introspect.PropertyDescriptor.findAnnotation")
)

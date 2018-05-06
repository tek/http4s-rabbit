scalaVersion in ThisBuild := "2.12.5"

def commonSettings = List(
  fork := true,
  scalacOptions ++= List(
    "-language:implicitConversions",
    "-language:higherKinds",
  )
)

val circeVersion = "0.9.2"
val http4sVersion = "0.18.4"

val core = project
  .settings(commonSettings)
  .settings(
    name := "http4s-rabbit-core",
    libraryDependencies ++= List(
      "org.typelevel" %% "cats-effect" % "0.10",
      "io.circe" %% "circe-core" % circeVersion,
    )
  )

val rabbit = project
  .settings(commonSettings)
  .settings(
    name := "http4s-rabbit-rabbit",
    libraryDependencies ++= List(
      "com.github.gvolpe" %% "fs2-rabbit-circe" % "0.5"
    )
  )

val api = project
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "http4s-rabbit-api",
    libraryDependencies ++= List(
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "io.circe" %% "circe-generic" % circeVersion,
    ),
    fork := true
  )

val app = project
  .dependsOn(api, rabbit)
  .settings(commonSettings)
  .settings(
    name := "http4s-rabbit",
    libraryDependencies ++= List(
     "org.http4s" %% "http4s-blaze-server" % http4sVersion,
    )
  )

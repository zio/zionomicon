inThisBuild {
  Seq(
    name                       := "zionomicon-exercises",
    scalaVersion               := "2.13.17",
    ciReleaseJobs              := Seq.empty,
    ciCheckWebsiteBuildProcess := Seq.empty,
    ciPostReleaseJobs          := Seq.empty,
    ciUpdateReadmeJobs         := Seq.empty,
    crossScalaVersions         := Seq("2.13.17")
  )
}

libraryDependencies ++= Seq(
  "dev.zio"      %% "zio"                 % "2.1.21",
  "dev.zio"      %% "zio-config"          % "4.0.5",
  "dev.zio"      %% "zio-config-magnolia" % "4.0.5",
  "dev.zio"      %% "zio-config-typesafe" % "4.0.5",
  "dev.zio"      %% "zio-http"            % "3.7.1",
  "dev.zio"      %% "zio-test"            % "2.1.21",
  "dev.zio"      %% "zio-interop-cats"    % "23.1.0.5",
  "org.tpolecat" %% "doobie-core"         % "1.0.0-RC9",
  "org.tpolecat" %% "doobie-hikari"       % "1.0.0-RC9",
  "org.xerial"    % "sqlite-jdbc"         % "3.49.1.0"
)

scalacOptions ++= Seq(
  "-deprecation"
)

publish / skip := true

stdSettings(turnCompilerWarningIntoErrors = false, enableKindProjector = false)

enablePlugins(ZioSbtCiPlugin, ZioSbtEcosystemPlugin)

inThisBuild {
  Seq(
    name                       := "zionomicon-exercises",
    scalaVersion               := "2.13.16",
    ciReleaseJobs              := Seq.empty,
    ciCheckWebsiteBuildProcess := Seq.empty,
    ciPostReleaseJobs          := Seq.empty,
    ciUpdateReadmeJobs         := Seq.empty,
    crossScalaVersions         := Seq("2.13.16")
  )
}

libraryDependencies ++= Seq(
  "dev.zio"      %% "zio"              % "2.1.19",
  "dev.zio"      %% "zio-test"         % "2.1.19",
  "dev.zio"      %% "zio-interop-cats" % "23.1.0.5",
  "org.tpolecat" %% "doobie-core"      % "1.0.0-RC9",
  "org.tpolecat" %% "doobie-hikari"    % "1.0.0-RC9",
  "org.xerial"    % "sqlite-jdbc"      % "3.49.1.0"
)

scalacOptions ++= Seq(
  "-deprecation"
)

stdSettings()

enablePlugins(ZioSbtCiPlugin, ZioSbtEcosystemPlugin)

libraryDependencies ++= Seq(
  "dev.zio"      %% "zio-interop-cats" % "23.1.0.5",
  "org.tpolecat" %% "doobie-core"      % "1.0.0-RC9",
  "org.tpolecat" %% "doobie-hikari"    % "1.0.0-RC9",
  "org.xerial"    % "sqlite-jdbc"      % "3.49.1.0"
)

inThisBuild {
  Seq(
    ciReleaseJobs              := Seq.empty,
    ciCheckWebsiteBuildProcess := Seq.empty,
    ciPostReleaseJobs          := Seq.empty,
    ciUpdateReadmeJobs         := Seq.empty
  )
}

scalacOptions ++= Seq(
  "-deprecation"
)

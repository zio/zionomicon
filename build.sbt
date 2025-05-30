val zioVersion = "2.1.18"

libraryDependencies ++= Seq(
  "dev.zio"      %% "zio"              % zioVersion,
  "dev.zio"      %% "zio-test"         % zioVersion,
  "dev.zio"      %% "zio-interop-cats" % "23.1.0.5",
  "org.tpolecat" %% "doobie-core"      % "1.0.0-RC9",
  "org.tpolecat" %% "doobie-hikari"    % "1.0.0-RC9",
  "org.xerial"    % "sqlite-jdbc"      % "3.49.1.0"
)

scalacOptions ++= Seq(
  "-deprecation"
)

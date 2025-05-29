val zioVersion = "2.1.17"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio"      % zioVersion,
  "dev.zio" %% "zio-test" % zioVersion
)

scalacOptions ++= Seq(
  "-deprecation"
)

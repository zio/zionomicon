val zioVersion = "2.1.17"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % zioVersion
)

scalacOptions ++= Seq(
  "-deprecation"
)

inThisBuild {
  Seq(
    name                       := "zionomicon-exercises",
    scalaVersion               := "3.7.0",
    ciReleaseJobs              := Seq.empty,
    ciCheckWebsiteBuildProcess := Seq.empty,
    ciPostReleaseJobs          := Seq.empty,
    ciUpdateReadmeJobs         := Seq.empty,
    crossScalaVersions         := Seq("3.7.0")
  )
}

libraryDependencies ++= Seq(
  "dev.zio"        %% "zio"                      % "2.1.21",
  "dev.zio"        %% "zio-config"               % "4.0.5",
  "dev.zio"        %% "zio-config-magnolia"      % "4.0.5",
  "dev.zio"        %% "zio-config-typesafe"      % "4.0.5",
  "dev.zio"        %% "zio-http"                 % "3.7.1",
  "dev.zio"        %% "zio-json"                 % "0.7.1",
  "dev.zio"        %% "zio-schema"               % "1.7.3",
  "dev.zio"        %% "zio-schema-derivation"    % "1.7.3",
  "dev.zio"        %% "zio-test"                 % "2.1.21",
  "dev.zio"        %% "zio-prelude"              % "1.0.0-RC39",
  "dev.zio"        %% "zio-interop-cats"         % "23.1.0.5",
  "dev.zio"        %% "zio-streams"              % "2.1.21",
  "org.tpolecat"   %% "doobie-core"              % "1.0.0-RC9",
  "org.tpolecat"   %% "doobie-hikari"            % "1.0.0-RC9",
  "com.rabbitmq"    % "amqp-client"              % "5.21.0",
  "dev.hnaderi"    %% "lepus-std"                % "0.5.5",
  "dev.hnaderi"    %% "lepus-circe"              % "0.5.5",
  "org.xerial"      % "sqlite-jdbc"              % "3.49.1.0",
  "org.openjdk.jmh" % "jmh-core"                 % "1.37",
  "org.openjdk.jmh" % "jmh-generator-annprocess" % "1.37",
  "io.getkyo"      %% "kyo-scheduler"            % "0.19.0"
)

scalacOptions ++= Seq(
  "-deprecation",
  "-Wunused:imports"
)

publish / skip := true

stdSettings(turnCompilerWarningIntoErrors = false, enableKindProjector = false)

enablePlugins(ZioSbtCiPlugin, ZioSbtEcosystemPlugin, JmhPlugin)

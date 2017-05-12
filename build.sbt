name := """cortex"""

lazy val cortex = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(PublishToBinTray.settings)

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  cache,
  ws,
  "net.codingwell" %% "scala-guice" % "4.0.1",
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test
)

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

Release.releaseVersionUIFile := baseDirectory.value / "ui" / "package.json"

Release.changelogFile := baseDirectory.value / "CHANGELOG.md"

publishArtifact in (Compile, packageDoc) := false

publishArtifact in packageDoc := false

sources in (Compile,doc) := Seq.empty

// Front-end //
run := {
  (run in Compile).evaluated
  frontendDev.value
}
mappings in packageBin in Assets ++= frontendFiles.value

// Install files //
mappings in Universal ~= {
  _.flatMap {
    case (_, "conf/application.conf") => Nil
    case (file, "conf/apllication.sample") => Seq(file -> "conf/application.conf")
    case (_, "conf/logback.xml") => Nil
    case other => Seq(other)
  } ++ Seq(
    file("package/cortex.service") -> "package/cortex.service",
    file("package/cortex.conf") -> "package/cortex.conf",
    file("package/cortex") -> "package/cortex",
    file("package/logback.xml") -> "conf/logback.xml"
  )
}

// Package //
maintainer := "Thomas Franco <toom@thehive-project.org"
packageSummary := "-"
packageDescription := """--""".stripMargin
defaultLinuxInstallLocation := "/opt"
linuxPackageMappings ~= { _.map { pm =>
  val mappings = pm.mappings.filterNot {
    case (_, path) => path.startsWith("/opt/cortex/package") || path.startsWith("/opt/cortex/conf")
  }
  com.typesafe.sbt.packager.linux.LinuxPackageMapping(mappings, pm.fileData).withConfig()
} :+ packageMapping(
  file("package/cortex.service") -> "/etc/systemd/system/cortex.service",
  file("package/cortex.conf") -> "/etc/init/cortex.conf",
  file("package/cortex") -> "/etc/init.d/cortex",
  file("conf/application.sample") -> "/etc/cortex/application.conf",
  file("conf/logback.xml") -> "/etc/cortex/logback.xml"
).withConfig()
}

packageBin := {
  (packageBin in Universal).value
  (packageBin in Debian).value
  (packageBin in Rpm).value
}
// DEB //
debianPackageDependencies += "java8-runtime-headless | java8-runtime"
maintainerScripts in Debian := maintainerScriptsFromDirectory(
  baseDirectory.value / "package" / "debian",
  Seq(DebianConstants.Postinst, DebianConstants.Prerm, DebianConstants.Postrm)
)
linuxEtcDefaultTemplate in Debian := (baseDirectory.value / "package" / "etc_default_cortex").asURL
linuxMakeStartScript in Debian := None

// RPM //
rpmRelease := "1"
rpmVendor in Rpm := "TheHive Project"
rpmUrl := Some("http://thehive-project.org/")
rpmLicense := Some("AGPL")
rpmRequirements += "java-1.8.0-openjdk-headless"
maintainerScripts in Rpm := maintainerScriptsFromDirectory(
  baseDirectory.value / "package" / "rpm",
  Seq(RpmConstants.Pre, RpmConstants.Preun, RpmConstants.Postun)
)
linuxPackageSymlinks in Rpm := Nil
rpmPrefix := Some(defaultLinuxInstallLocation.value)
linuxEtcDefaultTemplate in Rpm := (baseDirectory.value / "package" / "etc_default_cortex").asURL
packageBin in Rpm := {
  val rpmFile = (packageBin in Rpm).value
  s"rpm --addsign $rpmFile".!!
  rpmFile
}

// DOCKER //
import com.typesafe.sbt.packager.docker.{ Cmd, ExecCmd }

defaultLinuxInstallLocation in Docker := "/opt/cortex"
dockerRepository := Some("certbdf")
dockerUpdateLatest := true
dockerEntrypoint := Seq("/opt/cortex/entrypoint")
dockerExposedPorts := Seq(9000)
mappings in Docker ++= Seq(
  file("package/docker/entrypoint") -> "/opt/cortex/entrypoint",
  file("conf/logback.xml") -> "/etc/cortex/logback.xml",
  file("package/empty") -> "/var/log/cortex/application.log")
mappings in Docker ~= (_.filterNot {
  case (_, filepath) => filepath == "/opt/cortex/conf/application.conf"
})

dockerCommands ~= { dc =>
  val (dockerInitCmds, dockerTailCmds) = dc.splitAt(4)
  dockerInitCmds ++
    Seq(
      Cmd("USER", "root"),
      ExecCmd("RUN", "bash", "-c",
        "apt-get update && " +
          "apt-get install -y --no-install-recommends python-pip python2.7-dev ssdeep libfuzzy-dev libfuzzy2 libimage-exiftool-perl libmagic1 build-essential git && " +
          "cd /opt && " +
          "git clone https://github.com/CERT-BDF/Cortex-Analyzers.git && " +
          "pip install $(sort -u Cortex-Analyzers/analyzers/*/requirements.txt)"),
      Cmd("ADD", "var", "/var"),
      Cmd("ADD", "etc", "/etc"),
      ExecCmd("RUN", "chown", "-R", "daemon:daemon", "/var/log/cortex")) ++
    dockerTailCmds
}

// Bintray //
bintrayOrganization := Some("cert-bdf")
bintrayRepository := "cortex"
publish := {
  (publish in Docker).value
  PublishToBinTray.publishRelease.value
  PublishToBinTray.publishLatest.value
  PublishToBinTray.publishRpm.value
  PublishToBinTray.publishDebian.value
}

// Scalariform //
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

ScalariformKeys.preferences in ThisBuild := ScalariformKeys.preferences.value
  .setPreference(AlignParameters, false)
//  .setPreference(FirstParameterOnNewline, Force)
  .setPreference(AlignArguments, true)
//  .setPreference(FirstArgumentOnNewline, true)
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 60)
  .setPreference(CompactControlReadability, true)
  .setPreference(CompactStringConcatenation, false)
  .setPreference(DoubleIndentClassDeclaration, true)
//  .setPreference(DoubleIndentMethodDeclaration, true)
  .setPreference(FormatXml, true)
  .setPreference(IndentLocalDefs, false)
  .setPreference(IndentPackageBlocks, false)
  .setPreference(IndentSpaces, 2)
  .setPreference(IndentWithTabs, false)
  .setPreference(MultilineScaladocCommentsStartOnFirstLine, false)
//  .setPreference(NewlineAtEndOfFile, true)
  .setPreference(PlaceScaladocAsterisksBeneathSecondAsterisk, false)
  .setPreference(PreserveSpaceBeforeArguments, false)
//  .setPreference(PreserveDanglingCloseParenthesis, false)
  .setPreference(DanglingCloseParenthesis, Prevent)
  .setPreference(RewriteArrowSymbols, true)
  .setPreference(SpaceBeforeColon, false)
//  .setPreference(SpaceBeforeContextColon, false)
  .setPreference(SpaceInsideBrackets, false)
  .setPreference(SpaceInsideParentheses, false)
  .setPreference(SpacesWithinPatternBinders, true)
  .setPreference(SpacesAroundMultiImports, true)

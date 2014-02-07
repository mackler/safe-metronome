import sbt._

import Keys._
import sbtandroid.AndroidKeys._

object General {
  val settings = Defaults.defaultSettings ++ Seq (
    name := "SafeMetronome",
    version := "0.1.1-2",
    versionCode := 6,
    scalaVersion := "2.10.3",
    platformName in Android := "android-15",
    scalacOptions in Compile ++= Seq("-deprecation","-feature","-language:implicitConversions","-unchecked"),
    javaOptions in Compile += "-Dscalac.patmat.analysisBudget=off",
    initialize ~= { _ ⇒
      sys.props("scalac.patmat.analysisBudget") = "512"
    },
    unmanagedClasspath in Runtime <+= (baseDirectory) map { bd => Attributed.blank(bd / "config") }
  )

  val proguardSettings = Seq (
    useProguard in Android := true,
    proguardOption in Android := "@project/proguard.cfg"
  )

  lazy val fullAndroidSettings =
    General.settings ++
    sbtandroid.AndroidProject.androidSettings ++
    sbtandroid.TypedResources.settings ++
    proguardSettings ++
    sbtandroid.AndroidManifestGenerator.settings ++
    sbtandroid.AndroidMarketPublish.settings ++ Seq (
      keyalias in Android := "googleplay",
      libraryDependencies ++= Seq(
        "com.typesafe.akka" % "akka-actor_2.10" % "2.2.2"
//      ,"org.scalatest" %% "scalatest"     % "2.0" % "test"
      )
    )
}

object AndroidBuild extends Build {
  lazy val main = Project (
    id = "SafeMetronome",
    base = file("."),
    settings = General.fullAndroidSettings
  )

  lazy val tests = Project (
    id = "tests",
    base = file("tests"),
    settings = General.settings ++
               sbtandroid.AndroidTest.androidSettings ++
               General.proguardSettings ++ Seq (
      name := "SafeMetronomeTests"
    )
  ) dependsOn main
}

name := "consul-example"

version := "0.1"

scalaVersion := "2.12.6"

libraryDependencies ++= {
  Seq(
    "org.scalatest" %% "scalatest" % "3.0.1" % Test,
    "org.scalamock" %% "scalamock" % "4.0.0" % Test,

    "ch.qos.logback" % "logback-core" % "1.2.3",
    "ch.qos.logback" % "logback-classic" % "1.2.3",

    "com.ecwid.consul" % "consul-api" % "1.4.0"
  )
}

resolvers += Resolver.mavenLocal
Defaults.itSettings
//Revolver.settings

//enablePlugins(JavaAppPackaging, JavaAgent)
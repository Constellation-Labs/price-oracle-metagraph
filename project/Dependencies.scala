import sbt.*

object Dependencies {

  object V {
    val tessellation: String = sys.env.getOrElse("TESSELLATION_VERSION", "99.99.99-SNAPSHOT")
    val decline = "2.4.1"
    val weaver = "0.8.4"
    val catsEffectTestkit = "3.6.1"
    val organizeImports = "0.6.0"
  }

//  def tessellation(artifact: String): ModuleID = "io.constellationnetwork" %% s"tessellation-$artifact" % V.tessellation
  def tessellation(artifact: String): ModuleID = {
    val version = V.tessellation
    println(s"Using tessellation version: $version") // Debug logging
    "io.constellationnetwork" %% s"tessellation-$artifact" % version
  }

  def decline(artifact: String = ""): ModuleID =
    "com.monovore" %% {
      if (artifact.isEmpty) "decline" else s"decline-$artifact"
    } % V.decline

  object Libraries {
    val tessellationSdk = tessellation("sdk")
    val declineCore = decline()
    val declineEffect = decline("effect")
    val declineRefined = decline("refined")

    // Test
    val weaverCats = "com.disneystreaming" %% "weaver-cats" % V.weaver
    val weaverDiscipline = "com.disneystreaming" %% "weaver-discipline" % V.weaver
    val weaverScalaCheck = "com.disneystreaming" %% "weaver-scalacheck" % V.weaver
    val catsEffectTestkit = "org.typelevel" %% "cats-effect-testkit" % V.catsEffectTestkit

    val organizeImports = "com.github.liancheng" %% "organize-imports" % V.organizeImports
  }

  object CompilerPlugin {

    val betterMonadicFor = compilerPlugin(
      "com.olegpy" %% "better-monadic-for" % "0.3.1"
    )

    val kindProjector = compilerPlugin(
      ("org.typelevel" % "kind-projector" % "0.13.3").cross(CrossVersion.full)
    )

    val semanticDB = compilerPlugin(
      ("org.scalameta" % "semanticdb-scalac" % "4.13.1.1").cross(CrossVersion.full)
    )
  }
}

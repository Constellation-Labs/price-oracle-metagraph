import sbt.*

object Dependencies {

  object V {
    val tessellation: String = sys.env.getOrElse("TESSELLATION_VERSION", "3.0.1-rc.7-12-9dfdee86-SNAPSHOT")
    val decline = "2.4.1"
    val weaver = "0.8.1"
    val catsEffectTestkit = "3.4.7"
    val organizeImports = "0.5.0"
  }

  def tessellation(artifact: String): ModuleID = "io.constellationnetwork" %% s"tessellation-$artifact" % V.tessellation

  def decline(artifact: String = ""): ModuleID =
    "com.monovore" %% {
      if (artifact.isEmpty) "decline" else s"decline-$artifact"
    } % V.decline

  object Libraries {
    val tessellationSdk = tessellation("sdk")
    val tessellationNodeShared = tessellation("node-shared")
    val tessellationCurrencyL0 = tessellation("currency-l0")
    val tessellationCurrencyL1 = tessellation("currency-l1")
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

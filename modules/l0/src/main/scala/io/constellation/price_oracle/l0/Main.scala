package io.constellation.price_oracle.l0

import cats.effect.{IO, Resource}
import org.tessellation.BuildInfo
import org.tessellation.currency.dataApplication.{BaseDataApplicationL0Service}
import org.tessellation.currency.l0.CurrencyL0App
import org.tessellation.schema.cluster.ClusterId
import org.tessellation.security.SecurityProvider
import org.tessellation.schema.semver.{MetagraphVersion, TessellationVersion}

import java.util.UUID

  object Main
    extends CurrencyL0App(
      "custom-project-l0",
      "custom-project L0 node",
      ClusterId(UUID.fromString("517c3a05-9219-471b-a54c-21b7d72f4ae5")),
      tessellationVersion = TessellationVersion.unsafeFrom(BuildInfo.version),
      metagraphVersion = MetagraphVersion.unsafeFrom(BuildInfo.version)
    ) {
  }

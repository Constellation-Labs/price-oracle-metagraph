package io.constellation.price_oracle.shared_data.app

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.syntax.all._

import scala.concurrent.duration._
import scala.tools.nsc.tasty.SafeEq

import io.constellationnetwork.ext.cats.effect.ResourceIO
import io.constellationnetwork.schema.epoch.EpochProgress
import io.constellationnetwork.schema.priceOracle.TokenPair.DAG_USD

import ciris.Secret
import eu.timepit.refined.types.numeric.NonNegLong
import io.constellation.price_oracle.shared_data.app.ApplicationConfig._
import org.http4s.Uri
import weaver.MutableIOSuite

object ApplicationConfigSuite extends MutableIOSuite {

  test("intervalsConfig should find the correct config for a given epoch") { _ =>
    val config = ApplicationConfig(
      dataL1ServerUri = Uri.unsafeFromString("localhost:9000"),
      signing = SigningConfig("", Secret(""), Secret("")),
      environment = Dev,
      intervals = NonEmptyList.of(
        IntervalsConfig(
          poll = 1.second,
          storage = 3.seconds,
          movingAverage = 3.seconds,
          minEpochsBetweenUpdates = NonNegLong(0),
          asOfEpoch = EpochProgress.MinValue
        ),
        IntervalsConfig(
          poll = 2.seconds,
          storage = 6.seconds,
          movingAverage = 6.seconds,
          minEpochsBetweenUpdates = NonNegLong(1),
          asOfEpoch = EpochProgress(NonNegLong(10))
        )
      ),
      priceFeeds = NonEmptyList.of(PriceFeedConfig(DAG_USD, "DAG_USDT".some, "DAGUSDT".some, "DAGUSDT".some)),
      seedlist = List.empty
    )

    for {
      config0 <- config.intervalsConfig[IO](EpochProgress(NonNegLong(1)))
      config1 <- config.intervalsConfig[IO](EpochProgress(NonNegLong(10)))
      config2 <- config.intervalsConfig[IO](EpochProgress(NonNegLong(20)))
    } yield
      expect.all(
        config0.poll == 1.seconds,
        config0.minEpochsBetweenUpdates === NonNegLong(0),
        config1.poll == 2.seconds,
        config1.minEpochsBetweenUpdates === NonNegLong(1),
        config2.poll == 2.seconds,
        config2.minEpochsBetweenUpdates === NonNegLong(1)
      )
  }

  test("intervalsConfig should throw error when no config is found for epoch") { _ =>
    val config = ApplicationConfig(
      dataL1ServerUri = Uri.unsafeFromString("localhost:9000"),
      signing = SigningConfig("", Secret(""), Secret("")),
      environment = Dev,
      intervals = NonEmptyList.of(
        IntervalsConfig(
          poll = 1.second,
          storage = 3.seconds,
          movingAverage = 3.seconds,
          minEpochsBetweenUpdates = NonNegLong(0),
          asOfEpoch = EpochProgress(NonNegLong(1)) // Only config for epoch 1 and above
        )
      ),
      priceFeeds = NonEmptyList.of(PriceFeedConfig(DAG_USD, "DAG_USDT".some, "DAGUSDT".some, "DAGUSDT".some)),
      seedlist = List.empty
    )

    for {
      result <- config.intervalsConfig[IO](EpochProgress.MinValue).attempt
    } yield
      expect.all(
        result.isLeft,
        result.left.toOption.get.getMessage == "Cannot find an interval config for 0"
      )
  }

  test("intervalsConfig should handle unordered configs correctly") {
    val config = ApplicationConfig(
      dataL1ServerUri = Uri.unsafeFromString("localhost:9000"),
      signing = SigningConfig("", Secret(""), Secret("")),
      environment = Dev,
      intervals = NonEmptyList.of(
        IntervalsConfig(
          poll = 2.seconds,
          storage = 6.seconds,
          movingAverage = 6.seconds,
          minEpochsBetweenUpdates = NonNegLong(1),
          asOfEpoch = EpochProgress(NonNegLong(10))
        ),
        IntervalsConfig(
          poll = 1.second,
          storage = 3.seconds,
          movingAverage = 3.seconds,
          minEpochsBetweenUpdates = NonNegLong(0),
          asOfEpoch = EpochProgress.MinValue
        )
      ),
      priceFeeds = NonEmptyList.of(PriceFeedConfig(DAG_USD, "DAG_USDT".some, "DAGUSDT".some, "DAGUSDT".some)),
      seedlist = List.empty
    )

    // Should still find the correct config regardless of order in the list
    for {
      config0 <- config.intervalsConfig[IO](EpochProgress(NonNegLong(1)))
      config1 <- config.intervalsConfig[IO](EpochProgress(NonNegLong(10)))
    } yield
      expect.all(
        config0.poll == 1.seconds,
        config0.minEpochsBetweenUpdates === NonNegLong(0),
        config1.poll == 2.seconds,
        config1.minEpochsBetweenUpdates === NonNegLong(1)
      )
  }

  override type Res = Unit

  override def sharedResource: Resource[IO, Res] = ().pure[IO].asResource
}

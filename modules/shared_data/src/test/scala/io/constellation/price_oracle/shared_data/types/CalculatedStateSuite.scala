package io.constellation.price_oracle.shared_data.types

import java.security.KeyPair

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.syntax.all._

import io.constellationnetwork.ext.cats.effect.ResourceIO
import io.constellationnetwork.json.JsonSerializer
import io.constellationnetwork.schema.epoch.EpochProgress
import io.constellationnetwork.schema.priceOracle.TokenPair.DAG_USD
import io.constellationnetwork.security.signature.Signed
import io.constellationnetwork.security.{Hasher, KeyPairGenerator, SecurityProvider}

import eu.timepit.refined.types.numeric.{NonNegInt, NonNegLong}
import io.constellation.price_oracle.shared_data.pricefeed.PriceFeedId.GateIO
import weaver.MutableIOSuite

object CalculatedStateSuite extends MutableIOSuite {

  override type Res = (Hasher[IO], SecurityProvider[IO], KeyPair)

  override def sharedResource: Resource[IO, Res] =
    for {
      implicit0(sp: SecurityProvider[IO]) <- SecurityProvider.forAsync[IO]
      implicit0(j: JsonSerializer[IO]) <- JsonSerializer.forSync[IO].asResource
      implicit0(jh: Hasher[IO]) = Hasher.forJson[IO]
      kp <- KeyPairGenerator.makeKeyPair[IO].asResource

    } yield (jh, sp, kp)

  test("PriceValues calculates median values") { res =>
    implicit val (h, sp, kp) = res
    expect
      .all(
        priceValues(1).median == BigDecimal(1),
        priceValues(1, 2).median == BigDecimal("1.5"),
        priceValues(3, 1, 2).median == BigDecimal(2)
      )
      .pure[IO]
  }

  test("PriceOracleCalculatedState.append() maintains maxSize") { res =>
    implicit val (h, sp, kp) = res
    val maxSize = NonNegInt(3)

    for {
      signed1 <- signed(priceUpdate(1))
      signed2 <- signed(priceUpdate(2))
      signed3 <- signed(priceUpdate(3))
      signed4 <- signed(priceUpdate(4))
      state = PriceOracleCalculatedState()
        .append(signed1, maxSize)
        .append(signed2, maxSize)
        .append(signed3, maxSize)
        .append(signed4, maxSize)

    } yield
      expect.all(
        state.priceState(DAG_USD).size == 1,
        state.priceState(DAG_USD)(signed1.proofs.head.id).size == maxSize.value,
        state.priceState(DAG_USD) == Map(signed1.proofs.head.id -> List(signed2.value, signed3.value, signed4.value))
      )
  }

  test("PriceOracleCalculatedState.combine() maintains maxSize") { res =>
    implicit val (h, sp, kp) = res
    val maxSize = NonNegInt(3)

    for {
      signed1 <- signed(priceUpdate(1))
      signed2 <- signed(priceUpdate(2))
      signed3 <- signed(priceUpdate(3))
      signed4 <- signed(priceUpdate(4))

      state1 = PriceOracleCalculatedState()
        .append(signed1, maxSize)
        .append(signed2, maxSize)

      state2 = PriceOracleCalculatedState()
        .append(signed3, maxSize)
        .append(signed4, maxSize)

      state = state1.combine(state2, maxSize)
    } yield
      expect.all(
        state.priceState(DAG_USD).size == 1,
        state.priceState(DAG_USD)(signed1.proofs.head.id).size == maxSize.value,
        state.priceState(DAG_USD) == Map(signed1.proofs.head.id -> List(signed2.value, signed3.value, signed4.value))
      )
  }

  private def priceValues(ns: Int*) = PriceValues(NonEmptyList.fromListUnsafe(ns.toList.map(n => PriceValue(GateIO, BigDecimal(n)))))

  private def priceUpdate(ns: Int*) = PriceUpdate(DAG_USD, priceValues(ns: _*), EpochProgress.MinValue)

  private def signed(u: PriceUpdate)(implicit hasher: Hasher[IO], sp: SecurityProvider[IO], kp: KeyPair): IO[Signed[PriceUpdate]] =
    Signed.forAsyncHasher(u, kp)

}

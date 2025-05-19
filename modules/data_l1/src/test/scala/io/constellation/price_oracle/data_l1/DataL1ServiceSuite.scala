package io.constellation.price_oracle.data_l1

import cats.data.NonEmptyList
import cats.effect.{IO, Resource}
import cats.syntax.applicative._

import io.constellationnetwork.currency.dataApplication.dataApplication.DataApplicationValidationErrorOr
import io.constellationnetwork.currency.dataApplication.{DataState, L0NodeContext, L1NodeContext}
import io.constellationnetwork.ext.cats.effect.ResourceIO
import io.constellationnetwork.json.JsonSerializer
import io.constellationnetwork.schema.epoch.EpochProgress
import io.constellationnetwork.schema.priceOracle.TokenPair.DAG_USD
import io.constellationnetwork.security.SecurityProvider
import io.constellationnetwork.security.signature.Signed

import eu.timepit.refined.types.numeric.NonNegLong
import io.circe.parser._
import io.constellation.price_oracle.shared_data.pricefeed.PriceFeedId.{GateIO, KuCoin, MEXC}
import io.constellation.price_oracle.shared_data.types._
import io.constellation.price_oracle.shared_data.validations.ValidationService
import weaver.MutableIOSuite

object DataL1ServiceSuite extends MutableIOSuite {

  override type Res = JsonSerializer[IO]

  override def sharedResource: Resource[IO, Res] = for {
    implicit0(sp: SecurityProvider[IO]) <- SecurityProvider.forAsync[IO]
    implicit0(j: JsonSerializer[IO]) <- JsonSerializer.forSync[IO].asResource
  } yield j

  test("data decoder") { js =>
    val service = DataL1Service.make(mkValidationService, js)

    val s = """{
      |  "tokenPair" : {
      |    "base" : {
      |      "DAG" : {
      |
      |      }
      |    },
      |    "quote" : {
      |      "USD" : {
      |
      |      }
      |    }
      |  },
      |  "prices" : {
      |    "prices" : [
      |      {
      |        "priceFeedId" : {
      |          "GateIO" : {
      |
      |          }
      |        },
      |        "price" : 0.04252
      |      },
      |      {
      |        "priceFeedId" : {
      |          "KuCoin" : {
      |
      |          }
      |        },
      |        "price" : 0.042583
      |      },
      |      {
      |        "priceFeedId" : {
      |          "MEXC" : {
      |
      |          }
      |        },
      |        "price" : 0.04244
      |      }
      |    ]
      |  },
      |  "epochProgress": 12345,
      |  "createdAt" : 1748546929930
      |}""".stripMargin

    parse(s) match {
      case Right(json) =>
        expect
          .all(
            service.dataDecoder.decodeJson(json) == Right(
              PriceUpdate(
                DAG_USD,
                PriceValues.of(
                  PriceValue(GateIO, BigDecimal("0.04252")),
                  PriceValue(KuCoin, BigDecimal("0.042583")),
                  PriceValue(MEXC, BigDecimal("0.04244"))
                ),
                EpochProgress(NonNegLong(12345)),
                1748546929930L
              )
            )
          )
          .pure[IO]
      case Left(error) =>
        println(s"Failed to parse JSON: ${error.getMessage}")
        throw error
    }
  }

  private def mkValidationService = new ValidationService[IO] {
    override def validateUpdate(update: PriceUpdate)(implicit context: L1NodeContext[IO]): IO[DataApplicationValidationErrorOr[Unit]] = ???

    override def validateData(
      signedUpdates: NonEmptyList[Signed[PriceUpdate]],
      state: DataState[PriceOracleOnChainState, PriceOracleCalculatedState]
    )(implicit context: L0NodeContext[IO]): IO[DataApplicationValidationErrorOr[Unit]] = ???
  }
}

package io.constellation.price_oracle.shared_data.storages

import cats.effect.Async
import cats.syntax.all._

import scala.collection.immutable.SortedMap

import io.constellationnetwork.schema.{GlobalIncrementalSnapshot, SnapshotOrdinal}
import io.constellationnetwork.security.Hashed

import fs2.concurrent.SignallingRef

trait GlobalSnapshotsStorage[F[_]] {
  def set(snapshot: Hashed[GlobalIncrementalSnapshot]): F[Unit]
  def get: F[Option[Hashed[GlobalIncrementalSnapshot]]]
  def get(ordinal: SnapshotOrdinal): F[Option[Hashed[GlobalIncrementalSnapshot]]]
}

object GlobalSnapshotsStorage {
  def make[F[_]: Async]: F[GlobalSnapshotsStorage[F]] =
    SignallingRef
      .of[F, SortedMap[SnapshotOrdinal, Hashed[GlobalIncrementalSnapshot]]](SortedMap.empty)
      .map(make(_))

  def make[F[_]: Async](
    snapshotsR: SignallingRef[F, SortedMap[SnapshotOrdinal, Hashed[GlobalIncrementalSnapshot]]]
  ): GlobalSnapshotsStorage[F] =
    new GlobalSnapshotsStorage[F] {
      def set(snapshot: Hashed[GlobalIncrementalSnapshot]): F[Unit] =
        snapshotsR.update(snapshots => snapshots.updated(snapshot.ordinal, snapshot))

      def get: F[Option[Hashed[GlobalIncrementalSnapshot]]] = snapshotsR.get.map {
        _.lastOption.map { case (_, snapshot) => snapshot }
      }

      def get(ordinal: SnapshotOrdinal): F[Option[Hashed[GlobalIncrementalSnapshot]]] =
        snapshotsR.get.map(_.get(ordinal))
    }
}

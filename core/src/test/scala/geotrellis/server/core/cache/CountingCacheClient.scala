package geotrellis.server.core.cache

import com.github.blemale.scaffeine.{Cache, Scaffeine}
import cats.effect.{IO, Timer}

import scala.collection.mutable

class CountingCacheClient(
  val maxRetryDepth: Int,
  val retrySleepMillis: Int
) extends CacheClient {

  val counts = mutable.HashMap(
    "get" -> 0,
    "put" -> 0,
    "delete" -> 0
  )

  def getOption[T](key: String): IO[Option[T]] = ???

  def delete(key: String): IO[Unit] = ???

  def put[T](key: String, value: T, ttlSeconds: Int): IO[Unit] = ???
}



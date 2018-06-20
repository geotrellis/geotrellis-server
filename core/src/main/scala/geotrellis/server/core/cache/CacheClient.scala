package geotrellis.server.core.cache

//import net.spy.memcached._

import cats._
import cats.data._
import cats.implicits._
import cats.effect.{IO, Timer}
import cats.effect.implicits._
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.typesafe.scalalogging.LazyLogging
import com.github.blemale.scaffeine.{Cache => InMemCache, Scaffeine}

import scala.util.{Failure, Success}
import scala.annotation.tailrec
import scala.concurrent._
import scala.concurrent.duration._


object CacheClient {
  def apply(conf: CacheConfig): InMemCache[String, Option[Any]] =
    conf match {
      case CacheConfig.NoCache => ???
        //DummyCache
      case CacheConfig.InMemory(size, maxRetries, retryMillis) => ???
        //InMemory(size, maxRetries, retryMillis)
      case CacheConfig.Memcached(keySize, host, port, dymanicClient, timeout, threads, maxRetries, retryMillis) => ???
        //MemCachedClient(keySize, host, port, dynamicClient, timeout, threads, maxRetries, retryMillis)
      case CacheConfig.MemcachedWithLocal(memcachedConf, localConf) => ???
        //MemoryCache(localConf).combine(MemCachedClient(memcachedConf))
    }
}


trait CacheClient extends LazyLogging {

  final val AWAIT_SIGNAL = Byte.MinValue

  def getOption[T](key: String): IO[Option[T]]

  def delete(key: String): IO[Unit]

  def put[T](key: String, value: T, ttlSeconds: Int = 0): IO[Unit]

  def getOrElse[T](key: String, expensiveOperation: => IO[Option[T]], depth: Int = 0)(implicit timer: Timer[IO]): IO[Option[T]]

  def caching[T](key: String)(expensiveOperation: => IO[Option[T]])(implicit timer: Timer[IO]): IO[Option[T]] =
    getOrElse[T](key, expensiveOperation, 0)

  def andThen(secondary: CacheClient): CacheClient = {
    val primary = this
    new CacheClient {

      def getOption[T](key: String): IO[Option[T]] = primary.getOption[T](key)

      def delete(key: String): IO[Unit] =
        for {
          _ <- primary.delete(key)
          _ <- secondary.delete(key)
        } yield ()

      def put[T](key: String, value: T, ttlSeconds: Int): IO[Unit] =
        for {
          _ <- primary.put(key, value, ttlSeconds)
          _ <- secondary.put(key, value, ttlSeconds)
        } yield ()

      def getOrElse[T](key: String, expensiveOperation: => IO[Option[T]], depth: Int = 0)(implicit timer: Timer[IO]): IO[Option[T]] = {
        val secondaryGetOrElse = secondary.getOrElse[T](key, expensiveOperation, depth)
        primary.getOrElse[T](key, secondaryGetOrElse, depth)
      }
    }
  }
}


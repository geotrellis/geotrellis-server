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
        //MemoryCache(localConf).andThen(MemCachedClient(memcachedConf))
    }
}

trait CacheClient extends LazyLogging {

  private final val AWAIT_SIGNAL = Byte.MinValue

  val maxRetryDepth: Int

  val retrySleepMillis: Int

  def getOption[T](key: String): IO[Option[T]]

  def delete(key: String): IO[Unit]

  def put[T](key: String, value: T, ttlSeconds: Int = 0): IO[Unit]
    //IO.shift *> IO {
    //  withAbbreviatedKey(key) { key =>
    //    logger.debug(s"Setting Key: ${key} with TTL ${ttlSeconds}")
    //    client.set(key, ttlSeconds, value)
    //  }
    //}.runAsync match {
    //  case Right(_) => ()
    //  case Left(err) =>
    //    logger.error(s"Error ${err.getMessage}")
    //    reporter.error(err)
    //}

  private final def getOrElse[T](
    key: String,
    expensiveOperation: => IO[Option[T]],
    depth: Int = 0
  )(implicit timer: Timer[IO]): IO[Option[T]] = {
    // in order not to break tailrec
    val initializeKeyAndReturn: IO[Option[T]] = (for {
      _ <- put(key, Some(AWAIT_SIGNAL))
      res <- expensiveOperation
      _ <- put(key, Some(res))
    } yield res).attempt map { opResult =>
      opResult match {
        case Right(result) =>
          result
        case Left(e) =>
          logger.error(s"Cache set error at local cache: ${e.getMessage}")
          throw e
      }
    }

    getOption[T](key).flatMap {
      case Some(cachedValueO) =>
        cachedValueO match {
          case Some(AWAIT_SIGNAL) if depth < maxRetryDepth =>
            for {
              _ <- IO.sleep(retrySleepMillis milliseconds)
              _ <- IO.shift // throw this back to the system for potential thread reallocation
              res <- getOrElse[T](key, expensiveOperation, depth + 1)
            } yield res
          case Some(cachedValue) if depth <= maxRetryDepth =>
            logger.debug(s"Local Cache Hit: ${key}")
            IO.pure(Some(cachedValue.asInstanceOf[T]))
          case _ =>
            initializeKeyAndReturn
        }
      case _ =>
        initializeKeyAndReturn
    }
  }


  def caching[T](key: String)(expensiveOperation: => IO[Option[T]])(implicit timer: Timer[IO]): IO[Option[T]] =
    getOrElse[T](key, expensiveOperation, 0)

  def andThen(secondaryClient: CacheClient): CacheClient = {
    val primaryClient = this

    new CacheClient {

      val maxRetryDepth: Int = primaryClient.maxRetryDepth

      val retrySleepMillis: Int = primaryClient.retrySleepMillis

      def getOption[T](key: String): IO[Option[T]] = primaryClient.getOption[T](key)

      def delete(key: String): IO[Unit] =
        for {
          _ <- primaryClient.delete(key)
          _ <- secondaryClient.delete(key)
        } yield ()

      def put[T](key: String, value: T, ttlSeconds: Int): IO[Unit] =
        for {
          _ <- primaryClient.put(key, value, ttlSeconds)
          _ <- secondaryClient.put(key, value, ttlSeconds)
        } yield ()

      override def caching[T](key: String)(expensiveOperation: => IO[Option[T]])(implicit timer: Timer[IO]): IO[Option[T]] = {
        val secondaryCaching: IO[Option[T]] = secondaryClient.caching[T](key)(expensiveOperation)
        primaryClient.caching[T](key)(secondaryCaching)
      }
    }
  }
}

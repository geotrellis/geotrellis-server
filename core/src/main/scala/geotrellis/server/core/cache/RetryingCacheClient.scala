package geotrellis.server.core.cache

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


abstract class RetryingCacheClient(maxRetryDepth: Int, retrySleepMillis: Int) extends CacheClient {
  def getOrElse[T](
    key: String,
    expensiveOperation: => IO[Option[T]],
    depth: Int = 0
  )(implicit timer: Timer[IO]): IO[Option[T]] = {
    // in order not to break tailrec
    val initializeKeyAndReturn: IO[Option[T]] = (for {
      _ <- put(key, AWAIT_SIGNAL)
      res <- expensiveOperation
      _ <- put(key, res)
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
}


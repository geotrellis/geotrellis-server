package geotrellis.server.core.cache

import com.github.blemale.scaffeine.{Cache, Scaffeine}
import cats.effect.{IO, Timer}

import scala.collection.mutable

class CountingCacheClient(
  maxRetries: Int,
  retryWaitMillis: Int,
  counts: mutable.HashMap[String, Int] = mutable.HashMap(
    "get(hit)" -> 0,
    "get(miss)" -> 0,
    "put" -> 0,
    "delete" -> 0
  )
) extends RetryingCacheClient(maxRetries, retryWaitMillis) {

  def getCounts = counts

  val store: mutable.HashMap[String, Any] = mutable.HashMap()

  def getOption[T](key: String): IO[Option[T]] = IO {
    val result = store.get(key) map { _.asInstanceOf[T] }
    result match {
      case Some(_) =>
        println("get(hit)", key)
        counts("get(hit)") = counts("get(hit)") + 1
      case None =>
        println("get(miss)", key)
        counts("get(miss)") = counts("get(miss)") + 1
    }
    result
  }

  def delete(key: String): IO[Unit] = IO {
    counts("delete") = counts("delete") + 1
    store -= key
    ()
  }

  def put[T](key: String, value: T, ttlSeconds: Int): IO[Unit] = IO {
    println("put", key, value, ttlSeconds)
    counts("put") = counts("put") + 1
    store += ((key, value.asInstanceOf[Any]))
    ()
  }
}


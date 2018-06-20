package geotrellis.server.core.cache

import cats._
import cats.effect._
import cats.implicits._
import org.scalatest._

import scala.collection.mutable
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class RetryingCacheClientSpec extends FunSpec with Matchers {
  implicit val timer = IO.timer
  describe("CacheClient counts") {
    it("counts gets") {
      val cache = new CountingCacheClient(10, 100)
      cache.getOption[Int]("asdf").unsafeRunSync
      cache.getCounts("get(miss)") should equal (1)
    }

    it("counts put miss") {
      val cache = new CountingCacheClient(10, 100)
      cache.getOption[Int]("asdf").unsafeRunSync
      cache.getCounts("get(miss)") should equal (1)
      cache.getCounts("get(hit)") should equal (0)
    }

    it("counts delete") {
      val cache = new CountingCacheClient(10, 100)
      cache.getOption[Int]("asdf").unsafeRunSync
      cache.getCounts("get(miss)") should equal (1)
    }

    it("counts put hits") {
      val cache = new CountingCacheClient(10, 100)
      val response = (for {
        _ <- cache.put[Int]("asdf", 123)
        res  <- cache.getOption[Int]("asdf")
      } yield res).unsafeRunSync
      cache.getCounts("get(miss)") should equal (0)
      cache.getCounts("get(hit)") should equal (1)
      response should equal (Some(123))
    }

    it("should fuse caches") {
      val countMap = mutable.HashMap("get(hit)" -> 0, "get(miss)" -> 0, "put" -> 0, "delete" -> 0)
      val badCountMap = mutable.HashMap("get(hit)" -> -1, "get(miss)" -> -1, "put" -> -1, "delete" -> -1)
      val cache = new CountingCacheClient(10, 100, countMap).andThen(new CountingCacheClient(10, 100, badCountMap))
      val response = (for {
        _ <- cache.put[Int]("asdf", 123)
        _ <- cache.put[Int]("1234", 234)
        _ <- cache.put[Int]("zxcv", 345)
        res  <- cache.getOption[Int]("asdf")
      } yield res).unsafeRunSync
      countMap("get(hit)") should equal (1)
      countMap("put") should equal (3)
    }
  }

  it("should retry up to the max number of times") {
    val retries = 10
    val waitMillis = 100
    val cache = new CountingCacheClient(retries, waitMillis)
    val result = (for {
      _   <- cache.put[Byte]("testKey", Byte.MinValue)
      res <- cache.caching[Int]("testKey")(IO.sleep(retries * waitMillis + waitMillis milliseconds) *> IO { Some(42) })
    } yield res).unsafeToFuture
    val await1 = Await.result(result, 5 seconds)
    cache.getCounts("get(miss)") should be (1)
    cache.getCounts("get(hit)") should be (retries + 1)
    await1 should be (Some(42))
  }
}


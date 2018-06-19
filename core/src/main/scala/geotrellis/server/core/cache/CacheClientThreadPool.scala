package geotrellis.server.core.cache

import net.spy.memcached._
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.typesafe.scalalogging.LazyLogging
import com.github.blemale.scaffeine.{Cache, Scaffeine}

import scala.util.{Failure, Success}
import scala.annotation.tailrec
import scala.concurrent._
import scala.concurrent.duration._
import java.util.concurrent.Executors

class CacheClientThreadPool(cacheThreads: Int) {
  implicit lazy val ec: ExecutionContext =
    ExecutionContext.fromExecutor(
      Executors.newFixedThreadPool(
        cacheThreads,
        new ThreadFactoryBuilder().setNameFormat("cache-client-%d").build()
      )
    )
}


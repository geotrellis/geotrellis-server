package geotrellis.server.core.cache

import enumeratum._
import enumeratum.EnumEntry._


sealed trait CacheConfig extends EnumEntry

// Enum is necessary here for pureconfig to manage creation of the appropriate `Reader`
object CacheConfig extends Enum[CacheConfig] {

  val values = findValues

  case object NoCache extends CacheConfig

  case class InMemory(size: Int, maxRetries: Int, retryMillis: Int) extends CacheConfig

  case class Memcached(
    keysize: Int,
    host: String,
    port: Int,
    dynamicClient: Boolean,
    timeout: Int,
    threads: Int,
    maxRetries: Int,
    retryMillis: Int
  ) extends CacheConfig

  case class MemcachedWithLocal(memcached: Memcached, local: InMemory) extends CacheConfig

}

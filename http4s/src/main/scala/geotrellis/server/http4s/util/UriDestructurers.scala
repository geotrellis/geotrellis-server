package geotrellis.server.http4s.util

import java.util.UUID
import scala.util.Try


object UriDestructurers {
  object UuidVar {
    def unapply(str: String): Option[UUID] = {
      if (!str.isEmpty)
        Try(UUID.fromString(str)).toOption
      else
        None
    }
  }
}

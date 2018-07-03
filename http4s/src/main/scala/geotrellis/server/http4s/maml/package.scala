package geotrellis.server.http4s

import java.util.UUID
import scala.util.Try


package object maml {
  object IdVar {
    def unapply(str: String): Option[UUID] = {
      if (!str.isEmpty)
        Try(UUID.fromString(str)).toOption
      else
        None
    }
  }
}


package geotrellis.server.http4s.util

import org.http4s._

import java.util.UUID


object QueryParamDecoders {

  implicit val uuidQueryParamDecoder: QueryParamDecoder[UUID] =
    QueryParamDecoder[String].map(UUID.fromString)

}

package geotrellis.server.core

import geotrellis.server.core.maml.persistence._

import com.azavea.maml.ast.Expression
import cats._
import cats.effect.IO
import io.circe._
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap

import java.util.UUID


package object maml {
  type HashMapMamlStore = ConcurrentLinkedHashMap[UUID, Expression]
  implicit val inMemMamlStore: MamlStore[ConcurrentLinkedHashMap[UUID, Expression]] =
    new MamlStore[ConcurrentLinkedHashMap[UUID, Expression]] {
      def getMaml(self: ConcurrentLinkedHashMap[UUID, Expression], key: UUID): IO[Option[Expression]] =
        IO.pure { Option(self.get(key)) }

      def putMaml(self: ConcurrentLinkedHashMap[UUID, Expression], key: UUID, maml: Expression): IO[Unit] =
        IO.pure { self.put(key, maml) }
    }

}


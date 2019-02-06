package geotrellis.server.example

import com.azavea.maml.ast.Expression

import cats.effect.IO
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap

import java.util.UUID

package object persistence {
  type HashMapMamlStore = ConcurrentLinkedHashMap[UUID, Expression]
  implicit val inMemMamlStore: MamlStore[ConcurrentLinkedHashMap[UUID, Expression]] =
    new MamlStore[ConcurrentLinkedHashMap[UUID, Expression]] {
      def getMaml(self: ConcurrentLinkedHashMap[UUID, Expression], key: UUID): IO[Option[Expression]] =
        IO { Option(self.get(key)) }

      def putMaml(self: ConcurrentLinkedHashMap[UUID, Expression], key: UUID, maml: Expression): IO[Unit] =
        IO { self.put(key, maml) }
    }

}


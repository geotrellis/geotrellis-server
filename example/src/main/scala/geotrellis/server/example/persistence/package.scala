package geotrellis.server.example

import com.azavea.maml.ast.Expression

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import cats.effect._
import cats.implicits._

import java.util.UUID

package object persistence {
  type HashMapMamlStore = ConcurrentLinkedHashMap[UUID, Expression]
  implicit val inMemMamlStore: MamlStore[ConcurrentLinkedHashMap[UUID, Expression]] =
    new MamlStore[ConcurrentLinkedHashMap[UUID, Expression]] {
      def getMaml[F[_]](self: ConcurrentLinkedHashMap[UUID, Expression], key: UUID)(implicit F: ConcurrentEffect[F]): F[Option[Expression]] =
        F.pure { Option(self.get(key)) }

      def putMaml[F[_]](
        self: ConcurrentLinkedHashMap[UUID, Expression], key: UUID, maml: Expression
      )(implicit F: ConcurrentEffect[F]): F[Unit] =
        F.pure { self.put(key, maml) }
    }

}


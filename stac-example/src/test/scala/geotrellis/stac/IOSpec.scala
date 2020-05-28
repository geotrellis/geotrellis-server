package geotrellis.stac

import cats.effect.{ContextShift, IO, Timer}
import geotrellis.store.util.BlockingThreadPool
import org.scalatest._

trait IOSpec extends AsyncFunSpec with Assertions with Matchers {
  override implicit val executionContext      = BlockingThreadPool.executionContext
  implicit val contextShift: ContextShift[IO] = IO.contextShift(executionContext)
  implicit val timer: Timer[IO]               = IO.timer(executionContext)

  private val itWord = new ItWord

  def it(name: String)(test: => IO[Assertion]): Unit = itWord.apply(name)(test.unsafeToFuture())

  def ignore(name: String)(test: => IO[Assertion]): Unit = super.ignore(name)(test.unsafeToFuture())

  def describe(description: String)(fun: => Unit): Unit = super.describe(description)(fun)
}

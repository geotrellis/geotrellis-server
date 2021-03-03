package geotrellis

import cats.effect.{ContextShift, IO, Timer}
import geotrellis.store.util.BlockingThreadPool
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, Assertions}

trait IOSpec extends AsyncFunSpec with Assertions with Matchers {
  override implicit val executionContext      = BlockingThreadPool.executionContext
  implicit val contextShift: ContextShift[IO] = IO.contextShift(executionContext)
  implicit val timer: Timer[IO]               = IO.timer(executionContext)
  implicit val logger: Logger[IO]             = Slf4jLogger.getLogger[IO]

  private val itWord = new ItWord

  def it(name: String)(test: => IO[Assertion]): Unit = itWord.apply(name)(test.unsafeToFuture())

  def ignore(name: String)(test: => IO[Assertion]): Unit = super.ignore(name)(test.unsafeToFuture())

  def describe(description: String)(fun: => Unit): Unit = super.describe(description)(fun)
}

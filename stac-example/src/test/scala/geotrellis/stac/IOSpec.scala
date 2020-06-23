/*
 * Copyright 2020 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

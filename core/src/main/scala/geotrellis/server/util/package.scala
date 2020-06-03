package geotrellis.server

import java.io.{PrintWriter, StringWriter}

package object util {
  implicit class throwableExtensions[T <: Throwable](th: T) {
    def stackTraceString: String = {
      val writer = new StringWriter()
      th.printStackTrace(new PrintWriter(writer))
      writer.toString
    }
  }
}

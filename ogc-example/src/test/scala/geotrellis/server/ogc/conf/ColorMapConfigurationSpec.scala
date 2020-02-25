package geotrellis.server.ogc

import geotrellis.server.ogc.conf._
import geotrellis.raster.render.ColorMap

import pureconfig.ConfigSource

import org.scalatest.FunSpec
import org.scalatest._
import org.scalatest.Matchers._

class ColorMapConfigurationSpec extends FunSpec {

  describe("ColorMap Configuration") {
    it("should produce the same colormap regardless of key type") {
      val quoted =
        """{"-1.0": 0x1947B0FF,"-0.7": 0x3961B7FF,"-0.5": 0x5A7BBFFF,"-0.4": 0x7B95C6FF,"-0.3": 0x9CB0CEFF,"-0.2": 0xBDCAD5FF,"-0.1": 0xDEE4DDFF,"0": 0xFFFFE5FF,"0.1": 0xDAE4CAFF,"0.2": 0xB6C9AFFF,"0.3": 0x91AF94FF,"0.4": 0x6D9479FF,"0.5": 0x487A5EFF,"0.7": 0x245F43FF,"1.0": 0x004529FF}"""
      val unquoted =
        """{-1.0: 0x1947B0FF,-0.7: 0x3961B7FF,-0.5: 0x5A7BBFFF,-0.4: 0x7B95C6FF,-0.3: 0x9CB0CEFF,-0.2: 0xBDCAD5FF,-0.1: 0xDEE4DDFF, 0.0: 0xFFFFE5FF,0.1: 0xDAE4CAFF,0.2: 0xB6C9AFFF,0.3: 0x91AF94FF,0.4: 0x6D9479FF,0.5: 0x487A5EFF,0.7: 0x245F43FF,1.0: 0x004529FF}"""

      val quotedSource = ConfigSource.string(quoted)
      val unquotedSource = ConfigSource.string(unquoted)

      quotedSource.load[ColorMap].right.get.colors shouldBe (unquotedSource.load[ColorMap].right.get.colors)
    }
  }
}
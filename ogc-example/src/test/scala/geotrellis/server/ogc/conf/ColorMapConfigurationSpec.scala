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

package geotrellis.server.ogc

import geotrellis.server.ogc.conf._
import geotrellis.raster.render.ColorMap

import pureconfig.ConfigSource

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ColorMapConfigurationSpec extends AnyFunSpec with Matchers {

  describe("ColorMap Configuration") {
    it("should produce the same colormap regardless of key type") {
      val quoted =
        """{"-1.0": 0x1947B0FF,"-0.7": 0x3961B7FF,"-0.5": 0x5A7BBFFF,"-0.4": 0x7B95C6FF,"-0.3": 0x9CB0CEFF,"-0.2": 0xBDCAD5FF,"-0.1": 0xDEE4DDFF,"0": 0xFFFFE5FF,"0.1": 0xDAE4CAFF,"0.2": 0xB6C9AFFF,"0.3": 0x91AF94FF,"0.4": 0x6D9479FF,"0.5": 0x487A5EFF,"0.7": 0x245F43FF,"1.0": 0x004529FF}"""
      val unquoted =
        """{-1.0: 0x1947B0FF,-0.7: 0x3961B7FF,-0.5: 0x5A7BBFFF,-0.4: 0x7B95C6FF,-0.3: 0x9CB0CEFF,-0.2: 0xBDCAD5FF,-0.1: 0xDEE4DDFF, 0.0: 0xFFFFE5FF,0.1: 0xDAE4CAFF,0.2: 0xB6C9AFFF,0.3: 0x91AF94FF,0.4: 0x6D9479FF,0.5: 0x487A5EFF,0.7: 0x245F43FF,1.0: 0x004529FF}"""

      val quotedSource   = ConfigSource.string(quoted)
      val unquotedSource = ConfigSource.string(unquoted)

      quotedSource.load[ColorMap].right.get.colors shouldBe (unquotedSource.load[ColorMap].right.get.colors)
    }
  }
}

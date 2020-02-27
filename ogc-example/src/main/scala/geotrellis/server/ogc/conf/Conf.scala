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

package geotrellis.server.ogc.conf

import geotrellis.server.ogc.style.ClipDefinition
import geotrellis.server.ogc.ows
import geotrellis.server.ogc.wms.WmsParentLayerMeta

import pureconfig.generic.auto._
import pureconfig._
import scalaxb.DataRecord

/**
 * The top level configuration object for all layers and styles.
 * This object should be supplied by the various sections in the provided configuration. If
 * the application won't start because of a bad configuration, start here and recursively
 * descend through properties verifying that the configuration file provides sufficient
 * information.
 *
 * Complex types can be read with the help of [[ConfigReader]] instances. See package.scala
 *  and https://pureconfig.github.io/docs/supporting-new-types.html for more examples and
 *  explanation of ConfigReader instances.
 */
case class Conf(
  layers: Map[String, OgcSourceConf],
  wms: Option[WmsConf],
  wmts: Option[WmtsConf],
  wcs: Option[WcsConf]
)

object Conf {
  implicitly[ConfigReader[ClipDefinition]]
  lazy val conf: Conf = ConfigSource.default.loadOrThrow[Conf]
  implicit def ConfObjectToClass(obj: Conf.type): Conf = conf

  // This is a work-around to use pureconfig to read scalaxb generated case classes
  // DataRecord should never be specified from configuration, this satisfied the resolution
  // ConfigReader should be the containing class if DataRecord values need to be set
  implicit def dataRecordReader: ConfigReader[DataRecord[Any]] = null
}

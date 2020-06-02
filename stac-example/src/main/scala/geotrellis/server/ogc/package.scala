package geotrellis.server

import geotrellis.server.ogc.conf.MapAlgebraSourceConf

package object ogc {
  implicit class mapAlgebraSourceConfExtension(conf: MapAlgebraSourceConf) {
    def model(possibleSources: List[RasterOgcSource]): MapAlgebraSource = {
      val layerNames = conf.listParams(conf.algebra)
      val sourceList = layerNames.map { name =>
        val layerSrc = possibleSources.find(_.name == name).getOrElse {
          throw new Exception(
            s"MAML Layer expected but was unable to find the simple layer '$name', make sure all required layers are in the server configuration and are correctly spelled there and in all provided MAML")
        }
        name -> layerSrc.source
      }
      MapAlgebraSource(conf.name, conf.title, sourceList.toMap, conf.algebra, conf.defaultStyle, conf.styles.map(_.toStyle), conf.resampleMethod, conf.overviewStrategy)
    }
  }
}

package geotrellis.server.ogc.wms

sealed trait GetMapException extends Exception
case class GetMapBadRequest(msg: String) extends GetMapException
case class GetMapInternalServerError(msg: String) extends GetMapException

package geotrellis.server.ogc.ows

import java.net.URI

// Service-level metadata; roughly corresponds to ows service identification and service providers
case class ServiceMetadata(
  identification: Identification,
  provider: Provider
)

case class Identification(
  title: String,
  description: String,
  keywords: List[String],
  profile: List[URI],
  fees: Option[String],
  accessConstraints: List[String]
)

case class Provider(
  name: String,
  site: Option[String],
  contact: Option[ResponsiblePartySubset]
)

// corresponds roughly to opengis.ows.ResponsiblePartySubsetType
case class ResponsiblePartySubset(
  name: Option[String],
  position: Option[String],
  role: Option[String]
)

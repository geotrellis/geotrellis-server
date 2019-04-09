# OGC Server Configuration

The behavior of the OGC services is largely dependent upon a single,
static configuration file in the
[HOCON format](https://github.com/lightbend/config/blob/b782a2d701fc2b045794b4eed47a4b01e745f3a6/HOCON.md).
This file (example here: [application.conf](../src/main/resources/application.conf))
defines the sourcing of each layer and much of the associated metadata
advertised during `GetCapabilities` requests.

There's nothing magic about the mapping of a configuration to a running
server. In fact, there is a direct relation from data represented in
HOCON to the case classes in [ogc/conf](../src/main/scala/geotrellis/server/ogc/conf/).
If in doubt, confirming that the top level `Conf` case class has all of
its required information and, recursively, that its constituent pieces
have all their required (non-optional) data is useful debugging
practice.

In what follows, we'll discuss the necessary parts of this configuration.
Referring to the example configuration while reading this document is
probably a good idea. Keep in mind that the server depends upon a correctly defined
configuration file and that *the server won't run if misconfigured*.

The top level properties which require configuration are (in no
particular order):
- [layers](#layers-configuration)
- [wms](#wms-configuration)
- [wmts](#wmts-configuration)
- [wcs](#wcs-configuration)

## Layers Configuration

In the example configuration, you'll note that layers are configured
separately from OGC services and that these services use [HOCON
substitution](https://github.com/lightbend/config/blob/b782a2d701fc2b045794b4eed47a4b01e745f3a6/HOCON.md#substitutions)
to refer to the layers they want to expose.

> Hocon Substitution in the WMS configuration
```
layer-definitions = [
  ${layers.us-ned},
  ${layers.us-ned-slope},
  ${layers.us-census-median-household-income}
]
```

This is useful because it allows configurations to avoid unecessary and
dangerous repetition. The other reason for this bit of indirection is
that certain layers may need to be defined for other layers to properly
work (Map Algebra Layers refer to Simple, Avro layers). In such a case,
it might be desirable to only expose the complex (map algebra) layers
through an OGC service without any of its constituent (simple) layers.

By defining layers and later deciding which among them to display,
that's exactly what we can do. None of the layers listed under the top
level configuration property 'layers' will be displayed unless they are
referenced in the body of one of the OGC service configuration blocks.

The top-level 'layers' property is a map (to facilitate the
substitutions mentioned above) from some layer label to an
`OgcSourceConf`.

#### Defining Layers

The `OgcSourceConf` can be one of either `SimpleSourceConf` or
`MapAlgebraSourceConf`. The former source is simple because it is backed
by a single `Geotrellis` `RasterSource` whereas the latter source
consists in a `MAML` algebra which refers to neighboring
`SimpleSourceConf`s. For greater detail on how this reference works, see
the [MAML documentation](maml.md).

A simple source:
```
us-census-median-household-income = {
    type = "simplesourceconf"
    name = "us-census-median-household-income"
    title = "US Sensus Median Household Income 20??"
    source = {
        type = "geotrellis"
        catalog-uri = "s3://azavea-datahub/catalog"
        layer = "us-census-median-household-income-30m-epsg3857"
        zoom = 12
        band-count = 1
    }

    styles = []
}
```

A map algebra source (note the algebra section's reference to the simple
source above):
```
addition-house-income = {
    type = "mapalgebrasourceconf"
    name = "addition-house-income"
    title = "test addition"
    algebra = {
      "args" : [
        {
          "name" : "us-census-median-household-income",
          "symbol" : "rasterV"
        },
        {
          "name" : "us-census-median-household-income",
          "symbol" : "rasterV"
        }
      ],
      "symbol" : "+"
    }
    styles = [
        {
            type = "colorrampconf"
            name = "red-to-blue"
            title = "Red To Blue"
            colors = ${color-ramps.red-to-blue}
            stops = 64
        }
    ]
}
```

## WMS Configuration

Three pieces of configuration are expected by the top level 'wms'
property:
1. [Parent layer metadata](#wms-parent-layer-metadata)
2. [Service level metadata](#wms-service-metadata)
3. [Layer definitions](#layer-definitions)

#### WMS Parent Layer Metadata
WMS services use inheritance to group layers and this is exposed
via the configuration through 'parent-layer-meta' which corresponds to
`WmsParentLayerMeta`. This allows publishers to give a name, title, and
description to their layer collection as well as listing projections which
ought to be supported by child layers.

```
parent-layer-meta = {
  name = "Geotrellis WMS Parent Layer"
  title = "WMS Parent Title"
  description = "Top level metadata that is inherited by children layers"
  supported-projections = [
      4326,
      3410
  ]
}
```

#### WMS Service Metadata

The service-level metadata necessary for WMS allows publishers to
provide a name, title, online-resource, and keyword list as well as
contact-information within the `GetCapabilities` response.

WMS service metadata:
```
service-metadata = {
    name = "WMS"
    title = "GeoTrellis Service"
    online-resource = {}
    keyword-list = {
        keyword = ["geotrellis", "catalog"]
    }
    contact-information = {
        contact-person-primary = {
            contact-person = "Eugene Cheipesh"
            contact-organization = "Azavea"
        }
        contact-position = "Developer"
        contact-address = {
            address-type = "Office"
            address = "990 Spring Garden St."
            city = "Philadelphia"
            state-or-province = "PA",
            post-code = "19087",
            country = "USA")
        }
    }
}
```

The corresponding XML:
```xml
<Service>
  <Name>WMS</Name>
  <Title>GeoTrellis Service</Title>
  <KeywordList>
    <Keyword>geotrellis</Keyword>
    <Keyword>catalog</Keyword>
  </KeywordList>
  <OnlineResource/>
  <ContactInformation>
    <ContactPersonPrimary>
      <ContactPerson>Eugene Cheipesh</ContactPerson>
      <ContactOrganization>Azavea</ContactOrganization>
    </ContactPersonPrimary>
    <ContactPosition>Developer</ContactPosition>
    <ContactAddress>
      <AddressType>Office</AddressType>
      <Address>990 Spring Garden St.</Address>
      <City>Philadelphia</City>
      <StateOrProvince>PA</StateOrProvince>
      <PostCode>19087</PostCode>
      <Country>USA)</Country>
    </ContactAddress>
  </ContactInformation>
</Service>
```

## WCS Configuration

Three pieces of configuration are expected by the top level 'wms'
property:
1. [Service level metadata](#wcs/wmts-service-metadata)
2. [Layer definitions](#layer-definitions)


## WMTS Configuration

Three pieces of configuration are expected by the top level 'wms'
property:
1. [Service level metadata](#wcs/wmts-service-metadata)
2. [Tile matrix sets](#wmts-tile-matrix-sets)
3. [Layer definitions](#layer-definitions)


### WMTS Tile Matrix Sets

The WMTS spec requires services to advertise matrices which correspond
to the tile-pattern to be served. If you've worked with TMS services,
the conceit is familiar: different resolutions require different tile
sizes. As you zoom in and out, different imagery is loaded at discrete
levels.

Provided in the example configuration is a tile matrix set that
corresponds to the familiar TMS scheme (here, labeled
'GoogleMapsCompatible' because this is the standard name for such a
layout when working with OGC services).

Each tile matrix set should provide metadata which tells SOAP services
how to refer to them and under which projections they are applicable. In
addition, it should supply a list of tile matrices (which have an ID,
some extent of coverage, and a tile layout (tile columns, tile rows
covering said extent as well as pixel columns and pixel rows per tile).

Tile matrix set metadata:
```
identifier = "GoogleMapsCompatible"
supported-crs = 3857
title = "GoogleMapCompatible"
abstract = "Google Maps compatible tile matrix set"
well-known-scale-set = "urn:ogc:def:wkss:OGC:1.0:GoogleMapsCompatible"
```

A tile matrix (note that the extent is specified in the supported
projection!):
```
{
    identifier = "8",
    extent = [-20037508.34278925, -20037508.34278925, 20037508.34278925, 20037508.34278925],
    tile-layout = [256, 256, 256, 256]
}
```

### WCS/WMTS Service Metadata

WCS and WMTS share the same configuration options (the WMS spec
uses a slightly different schema for service metadata). This
configuration property - through its 'identification'
property - allows us to specify some of the same information
as in the WMS case (title, description, keywords) but also includes
the ability to specify a profile, fees, and access
constraints. It additionally allows specification of a provider name and
site.

Example WMTS/WCS service metadata:
```
service-metadata = {
    identification = {
        title = "WCS"
        description = "Geotrellis WCS Service"
        keywords = []
        profile = ["http://azavea.com/wcs-profile"]
        fees = []
        access-constraints = []
    }
    provider = {
        name = "Azavea"
        site = "https://www.azavea.com"
    }
}
```

XML corresponding to the identification block:
```
<ows:ServiceIdentification>
  <ows:Title>WCS</ows:Title>
  <ows:Abstract>Geotrellis WCS Service</ows:Abstract>
  <ows:ServiceType>OGS WCS</ows:ServiceType>
  <ows:ServiceTypeVersion>1.1.0</ows:ServiceTypeVersion>
  <ows:Fees>[]</ows:Fees>
  <ows:AccessConstraints>NONE</ows:AccessConstraints>
</ows:ServiceIdentification>
```

XML corresponding to the provider block:
```
<ows:ServiceProvider>
  <ows:ProviderName/>
  <ows:ProviderSite>https://www.azavea.com</ows:ProviderSite>
</ows:ServiceProvider>
```

### Layer Definitions

A required section for each of the services is a list of layers to expose via WMS.
As mentioned in the section on [layer
configuration](#layers-configuration), this portion of the conf can (and
should) use HOCON substitution to refer to the layers defined in the
top-level layer configuration.

An example layer definitions produced by referring with HOCON substitution:
```
layer-definitions = [
  ${layers.us-ned},
  ${layers.us-ned-slope},
  ${layers.us-census-median-household-income}
]
```

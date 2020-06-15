## SATC OGC Services example project

This project allows to use [STAC API](https://github.com/radiantearth/stac-api-spec) server as an input catalog for available rasters.
GeoTrellis server translates input OGC queries into STAC queries and reads
requested items assets respectively.

GeoTrellis server requires the [STAC Layer extension](#stac-layer-extension) usage. 

### Project description

List of commands available to run from this folder:

* `make run` - starts the postgres database for Franklin, applies all Franklin migrations, inserts 
the [./catalog](./catalog), starts the Franklin server and start the geotrellis-server that is configured to work
with this layer.
* `make run-geotrellis-server` - runs the GeoTrellis OGC server

For more details about available commands you can look into the [Makefile](Makefile).

### Compatible STAC Catalogs

In the [./catalog](./catalog) folder you can find a GeoTrellis server compatible static [STAC catalog](https://github.com/radiantearth/stac-spec) 
with the STAC Layer extension, that can be imported into [Franklin](https://azavea.github.io/franklin/docs/introduction).

### The new STAC configuration description

```javascript
stac-lc8-red-us = {
  type = "stacsourceconf"
  name = "stac-lc8-red-us" // OGC Layer name
  title = "Landsat LayerUS Red"
  layer = "layer-us" // STAC Layer
  asset = "B4" // STAC Asset to read
  asset-limit = 1000 // Max assets returned by the STAC search request
  source = "http://localhost:9090/" // Path to the STAC API endpoint
  default-style = "red-to-blue"
  styles = [
    {
      name = "red-to-blue"
      title = "Red To Blue"
      type = "colorrampconf"
      colors = ${color-ramps.red-to-blue}
      stops = 64
      }
  ]
}
```

Such layer can be used within the MAML expression.

### STAC Layer extension

- **Title: Layer**
- **Identifier: layer**
- **Field Name Prefix: layer**
- **Scope: Item**

This document explains the fields of the STAC Layer (layer) Extension to a STAC Item. STAC Items may
have references only to a single collection (through the `properties.collection` field).  

This extension allows items to have references to multiple named catalog which 
can be used to group items by the same layer name.

- Examples:
  - [Landsat 8 Layer catalog](catalog/landsat-stac-layers/catalog.json)
  - [Layer static representation](catalog/landsat-stac-layers/layers)

### Item fields

| Field Name     | Type     | Description |
| -------------- | ---------| ----------- |
| layer:ids      | [string] | A list of catalog identifiers which would be considered as layers |

### Item properties example

```javascript
"properties": {
  "collection": "landsat-8-l1",
  "layer:ids" : ["layer-us", "layer-pa"],
  "datetime": "2018-05-21T15:44:59.159086+00:00",
  "eo:sun_azimuth": 134.8082647,
  "eo:sun_elevation": 64.00406717,
  "eo:cloud_cover": 4,
  "eo:row": "032",
  "eo:column": "015",
  "landsat:product_id": "LC08_L1TP_015032_20180521_20180605_01_T1",
  "landsat:scene_id": "LC80150322018141LGN00",
  "landsat:processing_level": "L1TP",
  "landsat:tier": "T1",
  "eo:epsg": 32618,
  "eo:instrument": "OLI_TIRS",
  "eo:off_nadir": 0,
  "eo:platform": "landsat-8",
  "eo:gsd": 15
}
```

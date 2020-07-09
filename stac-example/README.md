## SATC OGC Services example project

This project allows to use [STAC API](https://github.com/radiantearth/stac-api-spec) server as an input catalog for available rasters.
GeoTrellis server translates input OGC queries into STAC queries and reads
requested items assets respectively.

GeoTrellis server requires the [STAC Layer extension](https://github.com/azavea/stac4s/tree/master/docs/stac-spec/extensions/layer) usage. 

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

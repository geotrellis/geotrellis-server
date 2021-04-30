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
  with-gdal = true // to use gdal to access stac assets
  title = "Landsat LayerUS Red"
  // GT Server can use the STAC Collection as a source or the STAC Layer
  layer = "layer-us" // STAC Layer
  collection = "landsat-8-l1" // STAC Collection
  asset = "B4" // STAC Asset to read
  asset-limit = 1000 // Max assets returned by the STAC search request
  source = "http://localhost:9090/" // Path to the STAC API endpoint
  default-style = "red-to-blue"
  // force time positions computation even if the range is given through the collection / layer summary
  compute-time-positions = true
  // optional field (can be added to all layers definitions)
  // the default value is "self", meaning that it would use the OGC Time in a format recieved from the source
  time-format = "{interval | positions | self}"
  ignore-time = false // work with temporal layers as with spatial layers, false by default
  // specify the default temporal rollback, oldest by default
  time-default = "{newest | oldest | ISO compatible date time}"
  // an experimental feature to use parallel mosaic
  // parallelism is controled via the geotrellis.blocking-thread-pool.threads option
  // which is set to the amount of available CPUs by default
  parallel-mosaic = {false | true}
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

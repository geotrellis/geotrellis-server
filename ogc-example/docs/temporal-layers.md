# Querying WMS Temporal Layers

Our clients are mostly interested in looking at temporal layers on the web via OpenLayers or on a desktop via QGIS so this document presents solutions using those two tools. We recommend use of WMS where possible as that's the spec that the recommended solutions explicitly support. For WCS temporal layers, consider querying the layer directly.

## Ad-Hoc Requests

WMS optionally handles a TIME parameter on GetMap requests that should be one of the valid times in the layer defined by the equivalent GetCapabilities request. If TIME is not provided and the layer supports such requests, the time returned will match the default time defined in GetCapabilities.

## OpenLayers

OpenLayers has two WMS source classes, `ImageWMS` and `TileWMS`. These sources define the parameters used for a GetMap request as a JS object, so all that's needed here is to ensure that a valid ISO string is provided in the expected WMS ISO8601 time interval format as the value of the `TIME` parameter, e.g. `TIME=2020-01-01T00:00:00Z`. The web app would be responsible for either looping valid dates based on the selected layer or sourcing a specific date from some user interface.

Here's [a complete example](https://codesandbox.io/s/wms-image-0qe6k) that renders an `ImageWMS` source on an OpenLayers map with a TIME specified:
```javascript
import { Image as ImageLayer, Tile as TileLayer } from "ol/layer";
import ImageWMS from "ol/source/ImageWMS";
import OSM from "ol/source/OSM";

var layers = [
  new TileLayer({
    source: new OSM()
  }),
  new ImageLayer({
    extent: [-13884991, 2870341, -7455066, 6338219],
    source: new ImageWMS({
      url: "http://mesonet.agron.iastate.edu/cgi-bin/wms/nexrad/n0r-t.cgi",
      params: { LAYERS: "nexrad-n0r-wmst", TIME: "2005-08-30T00:10:00Z" },
      ratio: 1,
      serverType: "geoserver"
    })
  })
];
```

## QGIS

QGis has a few plugins that claim to deal with temporal parameters. The aforementioned SentinelHub and TimeManager, and I also found [CrayFish](https://github.com/lutraconsulting/qgis-crayfish-plugin). Both Crayfish and SentinelHub are built more around specific use cases though (mesh datasets and accessing [SentinelHub](https://www.sentinel-hub.com/)), so really TimeManager remains the only available plugin that might meet our needs.

### TimeManager

https://plugins.qgis.org/plugins/timemanager/

TimeManager supports temporal queries of raster and vector layers, and I found a [blog post](https://anitagraser.com/2015/08/10/using-timemanager-for-wms-t-layers/) that walks a user through adding and animating a **WMS layer** via TimeManager. 

I followed this tutorial and was able to demonstrate the same functionality using QGIS 3.10 with TimeManager 3.4. Here's the [WMS GetCapabilities request](http://mesonet.agron.iastate.edu/cgi-bin/wms/nexrad/n0r-t.cgi?SERVICE=WMS&REQUEST=GetCapabilities) which shows the two radar layers and their valid times via `<Dimension name="time">...</Dimension>` and here's the WMS layer rendered for a specific time via TimeManager:

http://mesonet.agron.iastate.edu/cgi-bin/wms/nexrad/n0r-t.cgi?TIME=2005-08-30T00:10:00Z&SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&BBOX=33.43168637825333889,-122.7557667073972425,50,-66&CRS=EPSG:4326&WIDTH=1437&HEIGHT=420&LAYERS=nexrad-n0r-wmst&STYLES=&FORMAT=image/png&DPI=72&MAP_RESOLUTION=72&FORMAT_OPTIONS=dpi:72&TRANSPARENT=TRUE

![Screen Shot 2020-03-26 at 12 53 04 PM](https://user-images.githubusercontent.com/1818302/77673741-f4a1a800-6f60-11ea-864d-145d33254380.png)

Additionally, after addressing the bug in https://github.com/anitagraser/TimeManager/issues/171, I was able to render an animated gif for an hourly time interval (the layer claims interval support `PT5S`, or an image every 5 seconds, so any interval that matches those discrete steps will work) for the period of Hurricane Katrina's landfall:

![frames](https://user-images.githubusercontent.com/1818302/77673904-2dda1800-6f61-11ea-8c51-1ac3f20499b4.gif)

### Other Rejected Options

In the interest of completeness, I wanted to note two other potential solutions I looked at but ultimately rejected.

#### QGIS GDAL WMS

QGIS supports GDAL backed raster layers. GDAL supports the [WMS](https://gdal.org/drivers/raster/wms.html#driver-capabilities) / [WCS](https://gdal.org/drivers/raster/wcs.html) drivers. However, according to the GDAL driver docs, WCS time parameter:

> Currently time support is not available for versions other than WCS 1.0.0.

and the WMS driver makes no mention of how to include the time parameter. I rejected this solution due to incomplete docs and lack of clarity on how to proceed. [Generating a WMS XML definition](https://gdal.org/drivers/raster/wms.html#generation-of-wms-service-description-xml-file) to pass to GDAL was also non-trivial.

#### Write a QGIS Plugin

Rejected due to complexity and the need for us to write a non-trivial amount of code as soon as I was able to verify that the TimeManager plugin works for our needs.

TimeManager is open source, so our best bet would be to submit PRs for any additional features we might want to add. The [QGIS Python API](https://docs.qgis.org/3.10/en/docs/pyqgis_developer_cookbook/index.html) is pretty well-featured and has passable documentation. The [QGIS Python console](https://docs.qgis.org/3.10/en/docs/pyqgis_developer_cookbook/intro.html#scripting-in-the-python-console) allows users to use the Python API in what's essentially a REPL environment that can modify the open QGIS project.

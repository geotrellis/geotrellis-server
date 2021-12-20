## GeoTrellis Azure

### Configuration (Java native RasterSource)

* Set the env variable `AZURE_STORAGE_CONNECTION_STRING` (can be of any form) 

### Configuration (GDAL RasterSource)

* Set the env variable `AZURE_STORAGE_CONNECTION_STRING`
  * Should contain `AccountName` and `AccountKey`
* Instead of setting `AZURE_STORAGE_CONNECTION_STRING` it is possible to set 
  * `AZURE_STORAGE_SAS_TOKEN` (`AZURE_SAS`, GDAL < 3.5) to sign GDAL requests
  * `AZURE_STORAGE_ACCOUNT`

For more details, see [/vsiaz/ GDAL documentation](https://gdal.org/user/virtual_file_systems.html#vsiaz-microsoft-azure-blob-files).
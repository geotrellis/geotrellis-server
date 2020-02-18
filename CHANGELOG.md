# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Generate WCS 1.1.1 protocol using XSD data model [#188](https://github.com/geotrellis/geotrellis-server/issues/188)
- WCS 1.1.1 GetCoverage Support [#192](https://github.com/geotrellis/geotrellis-server/issues/192)

### Changed
- Included split dependencies a la GeoTrellis 3.2 for cats ecosystem libraries [\#184](https://github.com/geotrellis/geotrellis-server/pull/184)
- Dropped WCS 1.0.0 support
- Updated MAML up to 0.6.0 [#199](https://github.com/geotrellis/geotrellis-server/pull/199)

### Fixed
- Fixed color interpolation bug related to constructing a range when the step is 0 [#111](https://github.com/geotrellis/geotrellis-server/issues/111)
- Non-integer (floating point) ColorMap keys now work with or without being quoted [#187](https://github.com/geotrellis/geotrellis-server/issues/187)
- Missing `<ows:Title>` and `<ows:Abstract>` elements in WCS GetCapabilities response [#114](https://github.com/geotrellis/geotrellis-server/issues/114) 
- Layer definition elements unused in WMS GetCapabilities response [#115](https://github.com/geotrellis/geotrellis-server/issues/115)
- Bad assembly strategy [#142](https://github.com/geotrellis/geotrellis-server/issues/142)

## [4.0.1] - 2019-11-22

### Changed
- Make publishSettings accessible to aggregate modules

## [4.0.0]- 2019-11-21

### Added
- Enable artifact publishing for `opengis`, `ogc`, and `stac` subprojects [\#147](https://github.com/geotrellis/geotrellis-server/pull/147)
- Included more link types based on OGC Features API [\#176](https://github.com/geotrellis/geotrellis-server/pull/176)
- Included more OGC specs (sld, se, wfs, filter) [#186](https://github.com/geotrellis/geotrellis-server/pull/186)

### Changed
- *Breaking* Update StacItem and StacLinkType compliance and better ergonomics with labeling extension [\#145](https://github.com/geotrellis/geotrellis-server/pull/145)
- *Breaking* Changed Bbox to an ADT [\#180](https://github.com/geotrellis/geotrellis-server/pull/180)
- Publish to Sonatype Nexus via CircleCI [#138](https://github.com/geotrellis/geotrellis-server/pull/138)
- Added `Collection` `rel` type to `StackLink` [#167](https://github.com/geotrellis/geotrellis-server/pull/167)
- Fixed collision with `decoder` method name in `circe-fs2` [#178](https://github.com/geotrellis/geotrellis-server/pull/178)
- *Breaking* Upgrade to GeoTrellis 3.1.0 [#182](https://github.com/geotrellis/geotrellis-server/pull/182)

### Fixed
- Fixed optionality and StacExtent de-/serialization based on a real live STAC [#179](https://github.com/geotrellis/geotrellis-server/pull/179)
- Fixed a bug in `LayerHistogram` sampling that prevented some histograms from being generated [\#167](https://github.com/geotrellis/geotrellis-server/pull/167)

## [3.4.0] - 2019-07-18
### Added
- Add support for RGB and RGBA tiffs [#137](https://github.com/geotrellis/geotrellis-server/pull/137)

## [3.3.8] - 2019-07-10
### Changed
- Update geotrellis-contrib [#135](https://github.com/geotrellis/geotrellis-server/pull/135)

[Unreleased]: https://github.com/geotrellis/geotrellis-server/compare/4.0.1...HEAD
[4.0.1]: https://github.com/geotrellis/geotrellis-server/compare/4.0.0...4.0.1
[4.0.0]: https://github.com/geotrellis/geotrellis-server/compare/3.4.0...4.0.0
[3.4.0]: https://github.com/geotrellis/geotrellis-server/compare/3.3.8...3.4.0
[3.3.8]: https://github.com/geotrellis/geotrellis-server/compare/3.3.7...3.3.8

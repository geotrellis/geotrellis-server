clean:
	sbt "project example" clean

build:
	sbt "project example" assembly

serveOverlayExample:
	docker-compose run --service-ports overlay-example

servePersistenceExample:
	docker-compose run --service-ports persistence-example

serveNdviExample:
	docker-compose run --service-ports ndvi-example

serveGdalNdviExample:
	docker-compose run --service-ports gdal-ndvi-example

serveOgcExample:
	docker-compose run --service-ports ogc-example

serveDocs:
	docker-compose run server-microsite bash -c "cd /root/geotrellis-server && sbt 'project docs' makeMicrosite"
	docker-compose run --service-ports server-microsite

test:
	sbt test

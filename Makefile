clean:
	sbt "project example" clean
	touch docker/geotrellis-server-example.jar
	rm docker/geotrellis-server-example.jar

docker/geotrellis-server-example.jar:
	sbt "project example" assembly
	ln -f example/target/scala-2.11/geotrellis-server-example.jar docker/geotrellis-server-example.jar
	docker-compose build

rebuild:
	sbt "project example" assembly
	ln -f example/target/scala-2.11/geotrellis-server-example.jar docker/geotrellis-server-example.jar
	docker-compose build

serveOverlayExample: docker/geotrellis-server-example.jar
	docker-compose run --service-ports overlay-example

servePersistenceExample: docker/geotrellis-server-example.jar
	docker-compose run --service-ports persistence-example

serveNdviExample: docker/geotrellis-server-example.jar
	docker-compose run --service-ports ndvi-example

serveDocs:
	docker-compose run server-microsite bash -c "cd /root/geotrellis-server && sbt 'project docs' makeMicrosite"
	docker-compose run --service-ports server-microsite

test:
	sbt test


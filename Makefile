COMMIT=$(shell git rev-parse --short HEAD)

clean:
	sbt "project example" clean

docker/geotrellis-server-example.jar:
	sbt "project example" assembly
	ln -f example/target/scala-2.11/geotrellis-server-example.jar docker/geotrellis-server-example.jar
	docker-compose build

rebuild:
	sbt "project example" assembly
	ln -f example/target/scala-2.11/geotrellis-server-example.jar docker/geotrellis-server-example.jar
	docker-compose build

runOverlayExample: docker/geotrellis-server-example.jar
	docker-compose run overlay-example

runPersistenceExample: docker/geotrellis-server-example.jar
	docker-compose run persistence-example

runNdviExample: docker/geotrellis-server-example.jar
	docker-compose run ndvi-example

test:
	sbt test


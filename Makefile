COMMIT=$(shell git rev-parse --short HEAD)

clean:
	sbt "project example" clean
	touch docker/overlay-demo/geotrellis-server-example.jar
	rm docker/overlay-demo/geotrellis-server-example.jar

build: clean
	sbt "project example" assembly
	ln -f example/target/scala-2.11/geotrellis-server-example.jar docker/overlay-demo/geotrellis-server-example.jar
	docker-compose build

serve: build
	docker-compose up

test:
	sbt test


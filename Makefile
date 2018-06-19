COMMIT=$(shell git rev-parse --short HEAD)

clean:
	rm -rf **/target
	rm docker/server/geotrellis-server.jar

build:
	sbt assembly
	ln -f server/target/scala-2.11/geotrellis-server.jar docker/server/geotrellis-server.jar
	docker-compose build

serve: build
	docker-compose up

test:
	sbt test

publish:
	docker tag quay.io/geotrellis/server:latest quay.io/geotrellis/server:${COMMIT}

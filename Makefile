COMMIT=$(shell git rev-parse --short HEAD)

clean:
	rm -rf **/target
	rm docker/server/geotrellis-server-http4s.jar

build:
	sbt assembly
	ln -f http4s/target/scala-2.11/geotrellis-server-http4s.jar docker/server/geotrellis-server-http4s.jar
	docker-compose build

serve: build
	docker-compose up

test:
	sbt test

publish:
	docker tag quay.io/geotrellis/server:latest quay.io/geotrellis/server:${COMMIT}

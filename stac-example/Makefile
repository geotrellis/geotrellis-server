run:
	cd ../; ./sbt "project stac-example" run --public-url http://localhost:9000

assembly:
	cd ../; ./sbt "project stac-example" assembly

postgres:
	docker-compose up -d

migrations:
	docker run --rm \
      --network stac-example_default \
      quay.io/azavea/franklin:be7cf48 \
      migrate \
      --db-user franklin \
      --db-name franklin \
      --db-password franklin \
      --db-host database

# -e FRANKLIN_LOG_LEVEL=DEBUG
import-landsat-stac-collection:
	docker run --rm \
      --network stac-example_default \
      -e AWS_REGION=us-east-1 \
      -v ${PWD}/catalog:/opt/data/ \
      quay.io/azavea/franklin:be7cf48 \
      import-catalog \
      --db-user franklin \
      --db-name franklin \
      --db-password franklin \
      --db-host database \
      --catalog-root /opt/data/landsat-stac-collection/catalog.json

import-landsat-stac-periodic-collection:
	docker run --rm \
      --network stac-example_default \
      -e AWS_REGION=us-east-1 \
      -v ${PWD}/catalog:/opt/data/ \
      quay.io/azavea/franklin:be7cf48 \
      import-catalog \
      --db-user franklin \
      --db-name franklin \
      --db-password franklin \
      --db-host database \
      --catalog-root /opt/data/landsat-stac-periodic-collection/catalog.json

import-landsat-stac-layers:
	docker run --rm \
      --network stac-example_default \
      -e AWS_REGION=us-east-1 \
      -v ${PWD}/catalog:/opt/data/ \
      quay.io/azavea/franklin:be7cf48 \
      import-catalog \
      --db-user franklin \
      --db-name franklin \
      --db-password franklin \
      --db-host database \
      --catalog-root /opt/data/landsat-stac-layers/catalog.json

run-franklin:
	docker run --rm \
      --network stac-example_default \
      -p 9090:9090 \
      quay.io/azavea/franklin:be7cf48 \
      serve \
      --db-user franklin \
      --db-name franklin \
      --db-password franklin \
      --db-host database

run-geotrellis-server: assembly
	java -jar ${PWD}/target/scala-2.12/geotrellis-stac-example.jar --public-url http://localhost:9000

run: postgres migrations import-landsat-stac-collection run-franklin run-geotrellis-server

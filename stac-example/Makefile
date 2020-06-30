run:
	cd ../; ./sbt "project stac-example" run --public-url http://localhost:9000

assembly:
	cd ../; ./sbt "project stac-example" assembly

postgres:
	docker-compose up -d

migrations:
	docker run \
      --network stac-example_default \
      quay.io/azavea/franklin:538d079 \
      migrate \
      --db-user franklin \
      --db-name franklin \
      --db-password franklin \
      --db-host database

import-test-catalog:
	docker run \
      --network stac-example_default \
      -e AWS_REGION=us-east-1 \
      -v ${PWD}/catalog:/opt/data/ \
      quay.io/azavea/franklin:538d079 \
      import \
      --db-user franklin \
      --db-name franklin \
      --db-password franklin \
      --db-host database \
      --catalog-root /opt/data/landsat-stac-layers/catalog.json

run-franklin:
	docker run \
      --network stac-example_default \
      -p 9090:9090 \
      -d \
      quay.io/azavea/franklin:538d079 \
      serve \
      --db-user franklin \
      --db-name franklin \
      --db-password franklin \
      --db-host database

run-geotrellis-server: assembly
	java -jar ${PWD}/target/scala-2.12/geotrellis-stac-example.jar --public-url http://localhost:9000

run: postgres migrations import-test-catalog run-franklin run-geotrellis-server

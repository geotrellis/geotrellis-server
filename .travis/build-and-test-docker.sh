#!/bin/bash

docker run -it --net=host \
  -v $HOME/.ivy2:/root/.ivy2 \
  -v $HOME/.sbt:/root/.sbt \
  -v $TRAVIS_BUILD_DIR:/geotrellis-server \
  -e TRAVIS_SCALA_VERSION=$TRAVIS_SCALA_VERSION \
  -e TRAVIS_COMMIT=$TRAVIS_COMMIT \
  -e TRAVIS_JDK_VERSION=$TRAVIS_JDK_VERSION daunnc/openjdk-gdal:2.3.2 /bin/bash -c "cd /geotrellis-server; .travis/build-and-test.sh"

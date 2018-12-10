#!/bin/bash

./sbt -J-Xmx2G "++$TRAVIS_SCALA_VERSION" \
  "project core" test || { exit 1; }

./sbt -J-Xmx2G "++$TRAVIS_SCALA_VERSION" \
  "project example" test || { exit 1; }

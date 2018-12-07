#!/bin/bash
set -ex

git config --global user.email "npzimmerman@gmail.com"
git config --global user.name "moradology"
git config --global push.default simple

sbt docs/publishMicrosite

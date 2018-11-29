FROM hseeberger/scala-sbt:latest

RUN apt-get update -y
RUN apt-get install -y jekyll

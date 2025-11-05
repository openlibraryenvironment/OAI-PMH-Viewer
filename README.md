# OAI-PMH-Viewer

## Purpose
Bare bones web service for entering an OAI-PMH URL, verifying that the URL retrieves data from the intended OAI-PMH archive, and looking over some returned records for an initial format and content check.

## API
The service presents as a HTML form that responds to manual user input

## Prerequisites
- Java 21 JDK
- Maven 3.6.0

## Building
run `mvn install` from root directory

## Starting the service
run `java -jar [path-to-jar-file/]OAI-PMH-Viewer-{version}-fat.jar`

The service listens on port 8088 by default. To change the service port to, say, 8000, run:

`java -Dhttp.port=8000 -jar [path-to-jar-file/]OAI-PMH-Viewer-{version}-fat.jar`

## Running with Docker
To build the container run `docker build --build-arg VERSION="{version}" -t oai-pmh-viewer .`

To run use `docker run -p8088:8088 oai-pmh-viewer`. Pass options to java if desired using the JAVA_OPTIONS environment variable. For example, `docker run -e JAVA_OPTIONS="-Dhttp.port=8000" -p8000:8000 oai-pmh-viewer`

## CI
CI runs in github actions. See `.github/workflows` in this repo. CI performs the following:
 * mvn install on ubuntu latest
 * publish a docker image on Github's docker repo.
 * Updates the application in ID's reshare dev environment.

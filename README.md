# OAI-PMH-Viewer

## Purpose
Lightweight web service for entering an OAI-PMH URL, verify that the URL retrieves data from the intended OAI-PMH archive, and inspect a few returned records for format and content.

## API
The service presents as a HTML form that responds to manual user input

## Prerequisites
- Java 8 JDK
- Maven 3.6.0

## Building
run `mvn install` from root directory

## Starting the service
run `java -jar [path-to-jar-file/]OAI-PMH-Viewer-{version}-fat.jar` 


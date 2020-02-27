FROM openjdk:8-jre-alpine

ARG VERSION=1.1.1-SNAPSHOT

COPY entrypoint.sh /entrypoint.sh
COPY target/OAI-PMH-Viewer-${VERSION}-fat.jar /oai-viewer.jar

EXPOSE 8088

ENTRYPOINT ["/entrypoint.sh"]

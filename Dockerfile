FROM folioci/alpine-jre-openjdk21:latest

ARG VERSION=1.1.0-SNAPSHOT

COPY entrypoint.sh /entrypoint.sh
COPY target/OAI-PMH-Viewer-${VERSION}-fat.jar /oai-viewer.jar

EXPOSE 8088

ENTRYPOINT ["/entrypoint.sh"]

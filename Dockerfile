FROM folioci/alpine-jre-openjdk21:latest

ARG VERSION=1.1.0-SNAPSHOT

USER root
RUN apk upgrade --no-cache
USER folio

COPY target/OAI-PMH-Viewer-${VERSION}-fat.jar ${JAVA_APP_DIR}

EXPOSE 8088

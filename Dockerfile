FROM alpine:3.3

RUN apk add --no-cache curl wget bash py-pip py-yaml \
 && pip install -U pip docker-compose==1.5.2
 
ENV DOCKER_BUCKET get.docker.com
ENV DOCKER_VERSION 1.9.1
ENV DOCKER_SHA256 52286a92999f003e1129422e78be3e1049f963be1888afc3c9a99d5a9af04666

RUN curl -fSL "https://${DOCKER_BUCKET}/builds/Linux/x86_64/docker-$DOCKER_VERSION" -o /usr/local/bin/docker \
 && echo "${DOCKER_SHA256}  /usr/local/bin/docker" | sha256sum -c - \
 && chmod +x /usr/local/bin/docker \
 && mkdir -p /cjoc-init

COPY docker-compose.yml .
COPY entrypoint.sh /usr/local/bin/

COPY init.groovy /cjoc-init/init_00_fixed-ports_url.groovy
COPY init-disable.groovy /cjoc-init/init_99_disable.groovy

VOLUME /cjp-trial-data-joc

ENTRYPOINT ["entrypoint.sh"]
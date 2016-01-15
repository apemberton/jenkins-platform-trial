FROM alpine:3.3

RUN apk add --no-cache curl wget bash py-pip py-yaml \
 && pip install -U pip docker-compose==1.5.2
 
ENV DOCKER_BUCKET get.docker.com
ENV DOCKER_VERSION 1.9.1
ENV DOCKER_SHA256 52286a92999f003e1129422e78be3e1049f963be1888afc3c9a99d5a9af04666

RUN curl -fSL "https://${DOCKER_BUCKET}/builds/Linux/x86_64/docker-$DOCKER_VERSION" -o /usr/local/bin/docker \
 && echo "${DOCKER_SHA256}  /usr/local/bin/docker" | sha256sum -c - \
 && chmod +x /usr/local/bin/docker \
 && mkdir -p /cjoc-init/init.groovy.d \
 && mkdir -p /cjoc-init/license-activated-or-renewed-after-expiration.groovy.d \
 && mkdir -p /cje-init/init.groovy.d \
 && echo -n cjp-trial-poc > /cjoc-init/.cloudbees-referrer.txt

COPY docker-compose.yml .
COPY entrypoint.sh /usr/local/bin/

COPY ./cjoc_init/init* /cjoc-init/init.groovy.d/
COPY ./cjoc_init/license-activated/* /cjoc-init/license-activated-or-renewed-after-expiration.groovy.d/
COPY ./cje_init/init* /cje-init/init.groovy.d/

VOLUME /cjp-trial-data-cjoc
VOLUME /cjp-trial-data-cje

ENTRYPOINT ["entrypoint.sh"]
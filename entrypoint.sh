#!/bin/bash

if [ -z "JENKINS_URL" ]; then
  echo "JENKINS_URL is not defined"
else
  cp -a /cjoc-init/. /cjp-trial-data-joc
  exec docker-compose -p cjp-trial --x-networking up -d
fi
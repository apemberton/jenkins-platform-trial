#!/bin/bash

if [ -z "JENKINS_URL" ]; then
  echo "JENKINS_URL is not defined"
else
  cp -r /cjoc-init/. /cjp-trial-data-cjoc
  cp -r /cje-init/. /cjp-trial-data-cje
  exec docker-compose -p cjp-trial --x-networking up -d
fi
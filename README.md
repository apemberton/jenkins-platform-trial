# cjp-docker-trial
CJP trial kicked off via image based on this Dockerfile.

Run the images with `docker run -d -v /var/run/docker.sock:/var/run/docker.sock -v cjp-trial-data-cjoc:/cjp-trial-data-cjoc -v cjp-trial-data-cje:/cjp-trial-data-cje -e JENKINS_IP=$(docker-machine ip $(docker-machine active)) kmadel/cjp-trial:0.1`

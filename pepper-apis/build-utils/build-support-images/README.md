# Creating build and deployment-support Docker images

## Overview
Our builds and deploys are executed via CircleCi.

We have two images used in our CircleCi configuration.

The first one is there to support building and deploying the app and is built with `Dockerfile-build`.
The second one is more focused on supporting running our suite of tests and is built with `Dockerfile-java-build`

For convenience and to minimize external dependencies, we are also using one of these images to run `consul-template`
to generate all the runtime and test configuration files.


## Requirements
On Mac computer we are using:
`Docker version 20.10.14, build a224086`

## Procedure

Decide on a tag for the docker images so that we don't lose history.
Consider this one an example:

```
PEPPER_DOCKER_TAG=`date "+%Y-%m-%d%H.%M.%S"`
```


``
docker buildx create --use desktop-linux
docker buildx build --platform linux/amd64,linux/arm64 -t broadinstitute/study-server-build:$PEPPER_DOCKER_TAG --build-arg GIT_SHA=`git rev-parse --verify HEAD` -f ./Dockerfile-build --push .
``

``docker buildx build --platform linux/amd64,linux/arm64 -t broadinstitute/study-server-build:java-$PEPPER_DOCKER_TAG  --build-arg GIT_SHA=`git rev-parse --verify HEAD` -f ./Dockerfile-java-build --push .``

## Additional Notes
We are using multi-platform builds (amd64 and arm64) to create separate versions of images that can run on Intel 
processor architectures and on Apple Silicon.

The `push` option immediately pushes the images to Docker Hub. https://hub.docker.com/r/broadinstitute/study-server-build/tags

Note that in the tag part of the image name we have a specific date. Be aware that the full name of the image, including the tag is referenced
in the CircleCi YAML [config.yml](../../../.circleci/config.yml) and [main-config.yml](../../../.circleci/main-config.yml) and in the [backend configure.rb](../../configure.rb) and [frontend configure.rb](../../../pepper-cmi-angular/configure.rb) used to generate configuration files, so make sure you sync everything up if
you make changes to name.
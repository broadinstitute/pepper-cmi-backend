#!/usr/bin/env bash
#
# Script to render secrets for elasticsearch.

if (( $# < 2 )); then
  echo 'usage: render.sh ENV GCP_PROJECT'
  exit 1
fi

INPUT_DIR=config OUTPUT_DIR=output-config NO_SYSLOG=true \
  ENVIRONMENT="$1" GCP_PROJECT="$2" VERSION="v1" \
  ruby ../pepper-apis/configure.rb -y

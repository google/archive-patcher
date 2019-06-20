#!/bin/bash

set -e
set -x
cd ${KOKORO_ARTIFACTS_DIR}/github/archive-patcher
./gradlew build

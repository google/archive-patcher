#!/bin/bash

set -e
set -x

# outputs java version for debugging
java -version

cd ${KOKORO_ARTIFACTS_DIR}/github/archive-patcher
./gradlew build --info

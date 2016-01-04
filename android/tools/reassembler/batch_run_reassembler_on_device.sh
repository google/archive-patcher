#!/bin/bash
# Copyright 2016 Google Inc. All rights reserved.
# 
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http:#www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Execute run_reassembler_on_device.sh over a batch of files, sleeping between
# each run. Args are the files to process.

SCRIPT_DIR=$( cd $(dirname $0) ; pwd -P )
PROJECT_ROOT=$( cd ${SCRIPT_DIR}/../../.. ; pwd -P )
DEVICE_WORK_DIR=/mnt/sdcard/archive-patcher/reassembler

# Sleep multiplier. Sleep this long (as multiple of time spent running) between
# successive files.
archive_list=
directives_in_dir=
output_dir=
sleep_interval_multiplier=2
verbose=
verify=
while [[ $# -gt 0 ]] ;
do
    case "$1" in
      "--archive-list")
      archive_list="$2"
      shift 2
      ;;
      "--directives-in-dir")
      directives_in_dir="$2"
      shift 2
      ;;
      "--output-dir")
      output_dir="$2"
      shift 2
      ;;
      "--sleep-interval-multiplier")
      sleep_interval_multiplier="$2"
      shift 2
      ;;
      "-h" | "--help")
      echo "Usage: batch_run_reassembler_on_devices.sh --archive-list <archive_list_file> --directives-in-dir <directives_dir> --output-dir <output_dir> [--sleep-interval-multiplier <value>] [--verify] [--verbose]"
      exit 0
      ;;
      "--verify")
      verbose="verify"
      shift
      ;;
      "--verbose")
      verbose="verbose"
      shift
      ;;
      --)  # No more options
      break;
      ;;
      *)
      echo "Error: Unknown option: $1" >&2
      exit 1
      ;;
    esac
done

set -e
# Abort on missing args
[[ -z "${archive_list}" ]] && { echo "--archive-list is required" ; exit 1; }
[[ -z "${directives_in_dir}" ]] && { echo "--directives-in-dir is required" ; exit 1; }
[[ -z "${output_dir}" ]] && { echo "--output-dir is required" ; exit 1; }

# Check that args are sane
[[ ! -f "${archive_list}" ]] && { echo "archive does not exist: ${archive_list}" ; exit 1; }
[[ ! -r "${archive_list}" ]] && { echo "cannot read archive: ${archive_list}" ; exit 1; }
[[ ! -d "${directives_in_dir}" ]] && { echo "directives directory does not exist: ${directives_in_dir}" ; exit 1; }
[[ ! -r "${directives_in_dir}" ]] && { echo "cannot read directives directory: ${directives_in_dir}" ; exit 1; }

# Prepare output directory
mkdir -p ${output_dir}
[[ ! -d "${output_dir}" ]] && { echo "output dir doesn't exist and couldn't be created: ${output_dir}" ; exit 1; }
[[ ! -w "${output_dir}" ]] && { echo "output dir exists but cannot be written to: ${output_dir}" ; exit 1; }

# Prep args
verify_arg=""
if [ "${verify}" ]; then
  verify_arg="--verify"
fi
verbose_arg=""
if [ "${verbose}" ]; then
  verbose_arg="--verbose"
fi

next_sleep_interval=0
cat ${archive_list} | while read archive; do
  # Skip commands and blanks
  if [[ "${archive}" =~ [:blank:]*\#.* ]]; then
    continue # comment with optional preceding whitespace
  elif [[ ! "${archive}" ]]; then
    continue # blank
  else
    # Run.
    if [ "$next_sleep_interval" -gt "2" ]; then
      echo "Sleeping for ${next_sleep_interval} seconds to avoid thermal throttling."
      sleep ${next_sleep_interval}
    fi
    start=$SECONDS
    echo "Starting on archive ${archive} at $(date)"
    ${SCRIPT_DIR}/run_reassembler_on_device.sh --recover \
      --archive ${archive} \
      --directives-in-dir ${directives_in_dir} \
      --output-dir ${output_dir} \
      ${verbose_arg} ${verify_arg}
    now=$SECONDS
    elapsed=$(( ${now} - ${start} ))
    next_sleep_interval=$(( ${elapsed} * ${sleep_interval_multiplier} ))
    echo "Archive ${archive} processing completed at $(date)"
  fi
done
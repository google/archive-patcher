#!/bin/bash
# Copyright 2015 Google Inc. All rights reserved.
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

# Push files and run the reassembler, gathering stats.
ADB_PATH=$( which adb )
[[ -z "$ADB_PATH" ]] && { echo "adb not found on path, please add adb and try again" ; exit 1; }

SCRIPT_DIR=$( cd $(dirname $0) ; pwd -P )
PROJECT_ROOT=$( cd ${SCRIPT_DIR}/../../.. ; pwd -P )
DEVICE_WORK_DIR=/mnt/sdcard/archive-patcher/reassembler
verify="false"

while [[ $# -gt 0 ]] ;
do
    case "$1" in
      "--archive")
      archive="$2"
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
      "-h" | "--help")
      echo "Usage: run_reassembler_on_devices.sh --archive <archive> --directives-in-dir <directives_dir> --output-dir <output_dir> [--verify]"
      exit 0
      ;;
      "--verify") # Verify that reassembled archive matches original
      verify="true"
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
[[ -z "${archive}" ]] && { echo "--archive is required" ; exit 1; }
[[ -z "${directives_in_dir}" ]] && { echo "--directives-in-dir is required" ; exit 1; }
[[ -z "${output_dir}" ]] && { echo "--output-dir is required" ; exit 1; }

# Check that args are sane
[[ ! -f "${archive}" ]] && { echo "archive does not exist: ${archive}" ; exit 1; }
[[ ! -r "${archive}" ]] && { echo "cannot read archive: ${archive}" ; exit 1; }

[[ ! -d "${directives_in_dir}" ]] && { echo "directives directory does not exist: ${directives_in_dir}" ; exit 1; }
[[ ! -r "${directives_in_dir}" ]] && { echo "cannot read directives directory: ${directives_in_dir}" ; exit 1; }

# Check that directives file exists
archive_name=$( basename ${archive} )
directives_file="${directives_in_dir}/${archive_name}.directives"
[[ ! -f "${directives_file}" ]] && { echo "directives file does not exist: ${directives_file}" ; exit 1; }
[[ ! -r "${directives_file}" ]] && { echo "cannot read directives file: ${directives_file}" ; exit 1; }

# Prepare output directory
mkdir -p ${output_dir}
[[ ! -d "${output_dir}" ]] && { echo "output dir doesn't exist and couldn't be created: ${output_dir}" ; exit 1; }
[[ ! -w "${output_dir}" ]] && { echo "output dir exists but cannot be written to: ${output_dir}" ; exit 1; }

# From here on, everything should succeed. Abort on any error.
set -e

device_archive_path="${DEVICE_WORK_DIR}/${archive_name}"
device_directives_path="${DEVICE_WORK_DIR}/${archive_name}.directives"

is_msys=""
if [ "$(expr substr $(uname -s) 1 10)" == "MINGW32_NT" ]; then
    is_msys="yes"
elif [ "$(expr substr $(uname -s) 1 10)" == "MINGW64_NT" ]; then
    is_msys="yes"
fi

# Compensate for weirdness under MSYS (e.g., MINGW on Windows) where paths are
# converted in an undesirable manner.
if [ "${is_msys}" ]; then
    safe_device_archive_path="/${device_archive_path}"
    safe_device_directives_path="/${device_directives_path}"
    safe_device_work_path="/${DEVICE_WORK_DIR}"
else
    safe_device_archive_path="${device_archive_path}"
    safe_device_directives_path="${device_directives_path}"
    safe_device_work_path="${DEVICE_WORK_DIR}"
fi

# Ready to go!
if [ "${verbose}" ]; then
    if [ "${is_msys}" ]; then
        echo "Running under MSYS, paths will be prefixed to avoid problems." 
    fi
    echo "Local:"
    echo "  Archive:    ${archive}"
    echo "  Directives: ${directives_file}"
    echo "Device:"
    echo "  Work Dir:   ${DEVICE_WORK_DIR}"
    echo "  Archive:    ${device_archive_path}"
    echo "  Directives: ${device_directives_path}"
    echo "Verification requested? ${verify}"
    echo
fi

# Make working directory on the device
adb shell "mkdir -p ${DEVICE_WORK_DIR}"

# Copy files if they don't already exist
function checkFile {
  result=$(adb shell "ls $1 > /dev/null && echo -n 'found'")
  if [ "${result}" == "found" ]; then
    echo "true"
  else
    echo "false"
  fi
}

exists=$(checkFile "${safe_device_directives_path}")
if [ "$exists" == "true" ]; then
  if [ "${verbose}" ]; then echo "skipping copy of ${directives_file} because it already exists at ${device_directives_path}"; fi
else
  if [ "${verbose}" ]; then echo "copying ${directives_file} to device in ${device_directives_path}"; fi
  adb push ${directives_file} ${safe_device_directives_path}
fi

exists=$(checkFile "${safe_device_archive_path}")
if [ "$exists" == "true" ]; then
  if [ "${verbose}" ]; then echo "skipping copy of ${archive} because it already exists at ${device_archive_path}"; fi
else
  if [ "${verbose}" ]; then echo "copying ${archive} to device in ${device_archive_path}"; fi
  adb push ${archive} ${safe_device_archive_path}
fi

# The on-device stats file will be located in the work directory initially.
# Delete it if it already exists.
safe_device_stats_file="${safe_device_work_path}/${archive_name}.stats"
exists=$(checkFile "${safe_device_stats_file}")
if [ "$exists" == "true" ]; then
  if [ "${verbose}" ]; then echo "deleting old stats from device at ${safe_device_stats_file}"; fi
  adb shell rm ${safe_device_stats_file}
fi
safe_device_stats_csv_file="${safe_device_work_path}/${archive_name}.stats.csv"
exists=$(checkFile "${safe_device_stats_csv_file}")
if [ "$exists" == "true" ]; then
  if [ "${verbose}" ]; then echo "deleting old stats CSV from device at ${safe_device_stats_csv_file}"; fi
  adb shell rm ${safe_device_stats_csv_file}
fi

# Ensure the service has necessary permissions on Android M or later where there
# is no prompt at install time.
if [ "${verbose}" ]; then echo "granting read+write permissions to tool"; fi
adb shell pm grant com.google.archivepatcher.tools.reassembler android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant com.google.archivepatcher.tools.reassembler android.permission.WRITE_EXTERNAL_STORAGE

# Kill the service if it is currently running, then (re-)launch it.
stop_command="adb shell am stopservice -n com.google.archivepatcher.tools.reassembler/.ReassemblerService"
start_command="
adb shell am startservice \
  -n com.google.archivepatcher.tools.reassembler/.ReassemblerService \
  -a com.google.archivepatcher.tools.reassembler.action.REASSEMBLE \
  --es com.google.archivepatcher.tools.reassembler.extra.INPUT_ARCHIVE ${safe_device_archive_path} \
  --es com.google.archivepatcher.tools.reassembler.extra.DIRECTIVES_DIR ${safe_device_work_path} \
  --es com.google.archivepatcher.tools.reassembler.extra.OUTPUT_DIR ${safe_device_work_path} \
  --ez com.google.archivepatcher.tools.reassembler.extra.VERIFY ${verify}"

# Kill!
if [ "${verbose}" ]; then
    echo "Running: ${stop_command}"
fi
${stop_command}

# Launch!
if [ "${verbose}" ]; then
    echo "Running: ${start_command}"
fi
${start_command}

# Because we set the DEVICE output directory to the device work path, the output
# will initially be there. Poll for it.
if [ "${verbose}" ]; then echo "awaiting stats on device in ${safe_device_stats_file}"; fi
exists=$(checkFile "${safe_device_stats_file}")
while [ "$exists" == "false" ]; do
  sleep .1
  exists=$(checkFile "${safe_device_stats_file}")
done
if [ "${verbose}" ]; then echo "found stats on device in ${safe_device_stats_file}"; fi
output_stats_file="${output_dir}/${archive_name}.stats"
adb pull ${safe_device_stats_file} ${output_stats_file}

# Same for CSV stats.
if [ "${verbose}" ]; then echo "awaiting csv stats on device in ${safe_device_stats_csv_file}"; fi
exists=$(checkFile "${safe_device_stats_csv_file}")
while [ "$exists" == "false" ]; do
  sleep .1
  exists=$(checkFile "${safe_device_stats_csv_file}")
done
if [ "${verbose}" ]; then echo "found csv stats on device in ${safe_device_stats_csv_file}"; fi
output_stats_csv_file="${output_dir}/${archive_name}.stats.csv"
adb pull ${safe_device_stats_csv_file} ${output_stats_csv_file}

echo "-------------------------------------------------------------------------------"
echo "Done."
echo "Detailed results: ${output_stats_file}"
echo "Comma-separated values: ${output_stats_csv_file}"
echo "-------------------------------------------------------------------------------"
cat ${output_stats_file}
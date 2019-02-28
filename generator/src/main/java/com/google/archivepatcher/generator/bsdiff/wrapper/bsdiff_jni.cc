
// Copyright 2017 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <err.h>
#include <fcntl.h>
#include <jni.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <cstddef>
#include <cstdio>
#include <cstdlib>
#include <limits>

#include "bsdiff/bsdiff.h"
#include "bsdiff/patch_writer_factory.h"
#include "third_party/absl/memory/memory.h"
#include "third_party/java/jdk/include/jni.h"
#include "third_party/java_src/archive_patcher/com_google_archivepatcher_generator_bsdiff_wrapper_BsDiffNativePatchWriter.h"
#include "third_party/java_src/archive_patcher/com_google_archivepatcher_generator_bsdiff_wrapper_BsDiffNativePatchWriter.h"
#include "util/java/jni_helper.h"

namespace {
uint8_t* MapFile(JNIEnv* env, const char* filename, size_t* filesize) {
  int fd = open(filename, O_RDONLY);
  if (fd < 0) {
    env->ThrowNew(
        env->FindClass("com/google/archivepatcher/generator/bsdiff/"
                       "wrapper/NativeBsDiffException"),
        ("Unable to open file for mapping: " + std::string(filename)).c_str());
    return nullptr;
  }

  struct stat st;
  if (fstat(fd, &st) != 0) {
    env->ThrowNew(
        env->FindClass("com/google/archivepatcher/generator/bsdiff/"
                       "wrapper/NativeBsDiffException"),
        ("Unable to perform fstat() on file: " + std::string(filename))
            .c_str());
    return nullptr;
  }

  // Should work as long as file size fits uint64_t.
  if (static_cast<uint64_t>(st.st_size) > std::numeric_limits<size_t>::max()) {
    env->ThrowNew(env->FindClass("com/google/archivepatcher/generator/bsdiff/"
                                 "wrapper/NativeBsDiffException"),
                  ("File too large: " + std::string(filename)).c_str());
    close(fd);
    return nullptr;
  }

  *filesize = st.st_size;
  void* ret = mmap(nullptr, st.st_size, PROT_READ, MAP_SHARED, fd, 0);
  if (ret == MAP_FAILED) {
    if (close(fd) != 0) {
      env->ThrowNew(
          env->FindClass("com/google/archivepatcher/generator/bsdiff/"
                         "wrapper/NativeBsDiffException"),
          ("Unable to close file after map failed: " + std::string(filename))
              .c_str());
    }
    env->ThrowNew(
        env->FindClass("com/google/archivepatcher/generator/bsdiff/"
                       "wrapper/NativeBsDiffException"),
        ("Mapping the file has failed: " + std::string(filename)).c_str());
    return nullptr;
  }

  if (close(fd) != 0) {
    env->ThrowNew(env->FindClass("com/google/archivepatcher/generator/bsdiff/"
                                 "wrapper/NativeBsDiffException"),
                  ("Unable to close file: " + std::string(filename)).c_str());
    return nullptr;
  }

  return static_cast<uint8_t*>(ret);
}

jbyteArray GeneratePatch(JNIEnv* env, const uint8_t* old_buf, size_t old_size,
                         const uint8_t* new_buf, size_t new_size) {
  std::vector<uint8_t> patch;

  std::unique_ptr<bsdiff::PatchWriterInterface> patch_writer =
      bsdiff::CreateEndsleyPatchWriter(&patch);

  int bsdiff_return_value =
      bsdiff::bsdiff(old_buf, old_size, new_buf, new_size,
                     /* min_length= */ 16, patch_writer.get(), nullptr);

  if (bsdiff_return_value != 0) {
    env->ThrowNew(env->FindClass("com/google/archivepatcher/generator/bsdiff/"
                                 "wrapper/NativeBsDiffException"),
                  "BsDiff has failed during generation.");
    return nullptr;
  }

  jbyteArray result = env->NewByteArray(patch.size());
  env->SetByteArrayRegion(result, 0, patch.size(), (jbyte*)patch.data());

  return result;
}
}  // namespace

// nativeGeneratePatchFile() - returns a byte array with the generated patch.
// Performs a cleanup operation and releases all used resources from memory.
jbyteArray
Java_com_google_archivepatcher_generator_bsdiff_wrapper_BsDiffNativePatchWriter_nativeGeneratePatchFile(
    JNIEnv* env, jclass /*jcls*/, jstring old_filename, jstring new_filename) {
  const char* old_file = env->GetStringUTFChars(old_filename, nullptr);
  const char* new_file = env->GetStringUTFChars(new_filename, nullptr);

  if (old_file == nullptr || new_file == nullptr) {
    env->ThrowNew(env->FindClass("com/google/archivepatcher/generator/bsdiff/"
                                 "wrapper/NativeBsDiffException"),
                  "Unable to retrieve one of the diff files.");
    return nullptr;
  }

  size_t old_size, new_size;

  uint8_t* old_buf = MapFile(env, old_file, &old_size);
  uint8_t* new_buf = MapFile(env, new_file, &new_size);

  env->ReleaseStringUTFChars(old_filename, old_file);
  env->ReleaseStringUTFChars(new_filename, new_file);

  if (!old_buf) {
    if (new_buf) {
      munmap(new_buf, new_size);  // Ignore error on error.
    }
    return nullptr;
  }
  if (!new_buf) {
    if (old_buf) {
      munmap(old_buf, old_size);  // ignore error on error.
    }
    return nullptr;
  }

  jbyteArray result = GeneratePatch(env, old_buf, old_size, new_buf, new_size);

  munmap(old_buf, old_size);
  munmap(new_buf, new_size);

  return result;
}

// nativeGeneratePatchData() - returns a byte array with the generated patch.
jbyteArray
Java_com_google_archivepatcher_generator_bsdiff_wrapper_BsDiffNativePatchWriter_nativeGeneratePatchData(
    JNIEnv* env, jclass /*jcls*/, jbyteArray old_data, jbyteArray new_data) {
  jsize old_size = env->GetArrayLength(old_data);
  jsize new_size = env->GetArrayLength(new_data);

  auto old_buf = absl::make_unique<uint8_t[]>(old_size);
  auto new_buf = absl::make_unique<uint8_t[]>(new_size);

  env->GetByteArrayRegion(old_data, 0, old_size,
                          reinterpret_cast<jbyte*>(old_buf.get()));
  env->GetByteArrayRegion(new_data, 0, new_size,
                          reinterpret_cast<jbyte*>(new_buf.get()));

  return GeneratePatch(env, old_buf.get(), old_size, new_buf.get(), new_size);
}

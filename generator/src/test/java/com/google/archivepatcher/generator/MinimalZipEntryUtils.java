// Copyright 2016 Google LLC. All rights reserved.
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

package com.google.archivepatcher.generator;

import com.google.archivepatcher.shared.PatchConstants.CompressionMethod;

/** Utility class for testing with {@link MinimalZipEntry}. */
public class MinimalZipEntryUtils {

  /**
   * All fields in {@link MinimalZipEntry} are required. This is quite inconvenient for some tests
   * which only cares about some of the fields. This method returns a fake builder with all fields
   * populated so caller can override any field they care about.
   *
   * @return
   */
  public static MinimalZipEntry.Builder getFakeBuilder() {
    return MinimalZipEntry.builder()
        .compressionMethod(CompressionMethod.UNKNOWN)
        .crc32OfUncompressedData(0)
        .compressedSize(0)
        .uncompressedSize(0)
        .fileNameBytes(new byte[] {})
        .useUtf8Encoding(true)
        .fileOffsetOfLocalEntry(0)
        .fileOffsetOfCompressedData(0)
        .lengthOfLocalEntry(0);
  }
}

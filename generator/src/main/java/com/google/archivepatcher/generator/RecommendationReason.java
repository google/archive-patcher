// Copyright 2016 Google Inc. All rights reserved.
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

/**
 * Reasons for a corresponding {@link Recommendation}.
 */
public enum RecommendationReason {
  /**
   * The entry in the new file is compressed using deflate in a way that cannot be reliably
   * reproduced. This could be caused by using an unsupported version of zlib.
   */
  DEFLATE_UNSUITABLE,
  /**
   * The entry in the new file is compressed in a way that cannot be reliably reproduced (or one of
   * the entries is compressed using something other than deflate, but this is very uncommon).
   */
  UNSUITABLE,

  /**
   * Both the old and new entries are already uncompressed.
   */
  BOTH_ENTRIES_UNCOMPRESSED,

  /**
   * An entry that was uncompressed in the old file is compressed in the new file.
   */
  UNCOMPRESSED_CHANGED_TO_COMPRESSED,

  /**
   * An entry that was compressed in the old file is uncompressed in the new file.
   */
  COMPRESSED_CHANGED_TO_UNCOMPRESSED,

  /**
   * The compressed bytes in the old file do not match the compressed bytes in the new file.
   */
  COMPRESSED_BYTES_CHANGED,

  /** The compressed bytes in the old file are identical to the compressed bytes in the new file. */
  COMPRESSED_BYTES_IDENTICAL,

  /**
   * A resource constraint prohibits touching the old entry, the new entry, or both. For example,
   * there may be a limit on the total amount of temp space that will be available for applying a
   * patch or a limit on the total amount of CPU time that can be expended on recompression when
   * applying a patch, etc.
   */
  RESOURCE_CONSTRAINED;
}

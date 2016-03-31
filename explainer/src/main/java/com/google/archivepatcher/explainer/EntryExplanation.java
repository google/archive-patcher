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

package com.google.archivepatcher.explainer;

import com.google.archivepatcher.generator.ByteArrayHolder;
import com.google.archivepatcher.generator.RecommendationReason;

/**
 * The explanation for a single entry that was considered during generation of patch.
 */
public class EntryExplanation {
  /**
   * The path of the entry in the new archive.
   */
  private final ByteArrayHolder path;

  /**
   * True if the entry only exists in the new archive.
   */
  private final boolean isNew;

  /**
   * If the entry is not new, the reason for its inclusion in or exclusion from the patch.
   */
  private final RecommendationReason reasonIncludedIfNotNew;

  /**
   * The <strong>approximate</strong> size of the entry in the patch stream.
   */
  private final long compressedSizeInPatch;

  /**
   * Construct a new explanation for an entry.
   * @param path the path of the entry in the new archive
   * @param isNew true if the entry only exists in the new archive
   * @param reasonIncludedIfNotNew when isNew is false, the reason that the entry is included
   * @param compressedSizeInPatch the <strong>approximate</strong> size of the entry in the patch
   * stream
   */
  public EntryExplanation(
      ByteArrayHolder path,
      boolean isNew,
      RecommendationReason reasonIncludedIfNotNew,
      long compressedSizeInPatch) {
    super();
    this.path = path;
    this.isNew = isNew;
    this.reasonIncludedIfNotNew = reasonIncludedIfNotNew;
    this.compressedSizeInPatch = compressedSizeInPatch;
  }

  /**
   * Returns the path of the entry in the new archive.
   * @return as described
   */
  public ByteArrayHolder getPath() {
    return path;
  }

  /**
   * Returns true if the entry only exists in the new archive.
   * @return as described
   */
  public boolean isNew() {
    return isNew;
  }

  /**
   * When {@link #isNew()} is false, the reason that the entry is included.
   * @return as described
   */
  public RecommendationReason getReasonIncludedIfNotNew() {
    return reasonIncludedIfNotNew;
  }

  /**
   * Returns the <strong>approximate</strong> size of the entry in the patch stream. This number is
   * <strong>not</strong> guaranteed to be precise. Patch generation is complex, and in some cases
   * the patching process may use arbitrary bytes from arbitrary locations in the old archive to
   * populate bytes in the new archive. Other factors may also contribute to inaccuracies, such as
   * overhead in the patch format itself or in compression technology, etceteras.
   * @return as described
   */
  public long getCompressedSizeInPatch() {
    return compressedSizeInPatch;
  }
}

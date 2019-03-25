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

import java.util.Objects;

/**
 * An entry of {@link PreDiffPlan}, consisting of an {@link MinimalZipEntry} from the old file, a
 * {@link MinimalZipEntry} from the new file, a {@link ZipEntryUncompressionOption} for how to
 * uncompress the entries and a {@link UncompressionOptionExplanation} for that
 * zipEntryUncompressionOption.
 */
public class PreDiffPlanEntry {
  /**
   * The entry in the old file.
   */
  private final MinimalZipEntry oldEntry;

  /**
   * The entry in the new file.
   */
  private final MinimalZipEntry newEntry;

  /** The zipEntryUncompressionOption for how to proceed on the pair of entries. */
  private final ZipEntryUncompressionOption zipEntryUncompressionOption;

  /** The explanation for the zipEntryUncompressionOption. */
  private final UncompressionOptionExplanation explanation;

  /**
   * Construct a new qualified zipEntryUncompressionOption with the specified data.
   *
   * @param oldEntry the entry in the old file
   * @param newEntry the entry in the new file
   * @param zipEntryUncompressionOption the zipEntryUncompressionOption for this tuple of entries
   * @param explanation the explanation for the zipEntryUncompressionOption
   */
  public PreDiffPlanEntry(
      MinimalZipEntry oldEntry,
      MinimalZipEntry newEntry,
      ZipEntryUncompressionOption zipEntryUncompressionOption,
      UncompressionOptionExplanation explanation) {
    this.oldEntry = oldEntry;
    this.newEntry = newEntry;
    this.zipEntryUncompressionOption = zipEntryUncompressionOption;
    this.explanation = explanation;
  }

  /**
   * Returns the entry in the old file.
   * @return as described
   */
  public MinimalZipEntry getOldEntry() {
    return oldEntry;
  }

  /**
   * Returns the entry in the new file.
   * @return as described
   */
  public MinimalZipEntry getNewEntry() {
    return newEntry;
  }

  /**
   * Returns the zipEntryUncompressionOption for how to proceed for this tuple of entries.
   *
   * @return as described
   */
  public ZipEntryUncompressionOption getZipEntryUncompressionOption() {
    return zipEntryUncompressionOption;
  }

  /**
   * Returns the explanation for the zipEntryUncompressionOption.
   *
   * @return as described
   */
  public UncompressionOptionExplanation getExplanation() {
    return explanation;
  }

  @Override
  public int hashCode() {
    return Objects.hash(oldEntry, newEntry, zipEntryUncompressionOption, explanation);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof PreDiffPlanEntry)) {
      return false;
    }
    PreDiffPlanEntry otherEntry = (PreDiffPlanEntry) obj;
    return Objects.equals(oldEntry, otherEntry.oldEntry)
        && Objects.equals(newEntry, otherEntry.newEntry)
        && zipEntryUncompressionOption == otherEntry.zipEntryUncompressionOption
        && explanation == otherEntry.explanation;
  }

  @Override
  public String toString() {
    return "PreDiffPlanEntry [oldEntry="
        + oldEntry.getFileName()
        + ", newEntry="
        + newEntry.getFileName()
        + ", zipEntryUncompressionOption="
        + zipEntryUncompressionOption
        + ", explanation="
        + explanation
        + "]";
  }

}
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

import com.google.archivepatcher.shared.PatchConstants.DeltaFormat;
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

  /** The uncompressionOptionExplanation for the zipEntryUncompressionOption. */
  private final UncompressionOptionExplanation uncompressionOptionExplanation;

  /** The {@link DeltaFormat} to be used for this entry. */
  private final DeltaFormat deltaFormat;

  /** The explanation for {@link #deltaFormat}. */
  private final DeltaFormatExplanation deltaFormatExplanation;

  /**
   * Construct a new qualified zipEntryUncompressionOption with the specified data.
   *
   * @param oldEntry the entry in the old file
   * @param newEntry the entry in the new file
   * @param zipEntryUncompressionOption the zipEntryUncompressionOption for this tuple of entries
   * @param uncompressionOptionExplanation the uncompressionOptionExplanation for the
   * @param deltaFormat delta format for this pair of entries
   * @param deltaFormatExplanation explanation for the {@code deltaFormat}
   */
  private PreDiffPlanEntry(
      MinimalZipEntry oldEntry,
      MinimalZipEntry newEntry,
      ZipEntryUncompressionOption zipEntryUncompressionOption,
      UncompressionOptionExplanation uncompressionOptionExplanation,
      DeltaFormat deltaFormat,
      DeltaFormatExplanation deltaFormatExplanation) {
    this.oldEntry = oldEntry;
    this.newEntry = newEntry;
    this.zipEntryUncompressionOption = zipEntryUncompressionOption;
    this.uncompressionOptionExplanation = uncompressionOptionExplanation;
    this.deltaFormat = deltaFormat;
    this.deltaFormatExplanation = deltaFormatExplanation;
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
   * Returns the uncompressionOptionExplanation for the zipEntryUncompressionOption.
   *
   * @return as described
   */
  public UncompressionOptionExplanation getUncompressionOptionExplanation() {
    return uncompressionOptionExplanation;
  }

  /** Returns the delta format for this entry. */
  public DeltaFormat getDeltaFormat() {
    return deltaFormat;
  }

  /** Returns the explanation for {@link #deltaFormat}. */
  public DeltaFormatExplanation getDeltaFormatExplanation() {
    return deltaFormatExplanation;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        oldEntry,
        newEntry,
        zipEntryUncompressionOption,
        uncompressionOptionExplanation,
        deltaFormat,
        deltaFormatExplanation);
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
        && uncompressionOptionExplanation == otherEntry.uncompressionOptionExplanation
        && deltaFormat == otherEntry.deltaFormat
        && deltaFormatExplanation == otherEntry.deltaFormatExplanation;
  }

  @Override
  public String toString() {
    return "PreDiffPlanEntry [oldEntry="
        + oldEntry.getFileName()
        + ", newEntry="
        + newEntry.getFileName()
        + ", zipEntryUncompressionOption="
        + zipEntryUncompressionOption
        + ", uncompressionOptionExplanation="
        + uncompressionOptionExplanation
        + ", deltaFormat="
        + deltaFormat
        + ", deltaFormatExplanation="
        + deltaFormatExplanation
        + "]";
  }

  /** Builder for {@link PreDiffPlanEntry}. */
  public static class Builder {
    private MinimalZipEntry oldEntry;
    private MinimalZipEntry newEntry;
    private ZipEntryUncompressionOption zipEntryUncompressionOption;
    private UncompressionOptionExplanation uncompressionOptionExplanation;
    private DeltaFormat deltaFormat;
    private DeltaFormatExplanation deltaFormatExplanation;

    private Builder() {}

    /** Sets the pair of zip entries. */
    public Builder setZipEntries(MinimalZipEntry oldEntry, MinimalZipEntry newEntry) {
      this.oldEntry = oldEntry;
      this.newEntry = newEntry;
      return this;
    }

    /** Sets the uncompression option and the explanation. */
    public Builder setUncompressionOption(
        ZipEntryUncompressionOption uncompressionOption,
        UncompressionOptionExplanation explanation) {
      this.zipEntryUncompressionOption = uncompressionOption;
      this.uncompressionOptionExplanation = explanation;
      return this;
    }

    /** Sets the delta format and the explanation. */
    public Builder setDeltaFormat(DeltaFormat deltaFormat, DeltaFormatExplanation explanation) {
      this.deltaFormat = deltaFormat;
      this.deltaFormatExplanation = explanation;
      return this;
    }

    /** Builds the {@link PreDiffPlanEntry}. */
    public PreDiffPlanEntry build() {
      if (oldEntry == null || newEntry == null) {
        throw new IllegalArgumentException("Old entry and new entry cannot be null");
      }
      if (zipEntryUncompressionOption == null || uncompressionOptionExplanation == null) {
        throw new IllegalArgumentException("UncompressionOption and explanation cannot be null");
      }
      if (deltaFormat == null || deltaFormatExplanation == null) {
        throw new IllegalArgumentException("Delta format and explanation cannot be null");
      }
      return new PreDiffPlanEntry(
          oldEntry,
          newEntry,
          zipEntryUncompressionOption,
          uncompressionOptionExplanation,
          deltaFormat,
          deltaFormatExplanation);
    }
  }

  /** Returns a {@link Builder} for {@link PreDiffPlanEntry}. */
  public static Builder builder() {
    return new Builder().setDeltaFormat(DeltaFormat.BSDIFF, DeltaFormatExplanation.DEFAULT);
  }

  /** Returns a {@link Builder} with the current state. */
  public Builder toBuilder() {
    return new Builder()
        .setZipEntries(oldEntry, newEntry)
        .setUncompressionOption(zipEntryUncompressionOption, uncompressionOptionExplanation)
        .setDeltaFormat(deltaFormat, deltaFormatExplanation);
  }
}

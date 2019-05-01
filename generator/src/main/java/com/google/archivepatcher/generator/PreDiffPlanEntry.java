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

import com.google.archivepatcher.shared.PatchConstants.DeltaFormat;
import com.google.archivepatcher.shared.Range;
import com.google.auto.value.AutoValue;
import java.util.Comparator;

/**
 * An entry of {@link PreDiffPlan}, consisting of an {@link MinimalZipEntry} from the old file, a
 * {@link MinimalZipEntry} from the new file, a {@link ZipEntryUncompressionOption} for how to
 * uncompress the entries and a {@link UncompressionOptionExplanation} for that
 * zipEntryUncompressionOption.
 */
@AutoValue
public abstract class PreDiffPlanEntry {

  public static final Comparator<PreDiffPlanEntry> OLD_BLOB_OFFSET_COMPARATOR =
      (o1, o2) ->
          Range.offsetComparator()
              .compare(o1.oldEntry().localEntryRange(), o2.oldEntry().localEntryRange());

  public static final Comparator<PreDiffPlanEntry> NEW_BLOB_OFFSET_COMPARATOR =
      (o1, o2) ->
          Range.offsetComparator()
              .compare(o1.newEntry().localEntryRange(), o2.newEntry().localEntryRange());

  /** The entry in the old file. */
  public abstract MinimalZipEntry oldEntry();

  /** The entry in the new file. */
  public abstract MinimalZipEntry newEntry();

  /** The zipEntryUncompressionOption for how to proceed on the pair of entries. */
  public abstract ZipEntryUncompressionOption zipEntryUncompressionOption();

  /** The uncompressionOptionExplanation for the zipEntryUncompressionOption. */
  public abstract UncompressionOptionExplanation uncompressionOptionExplanation();

  /** The {@link DeltaFormat} to be used for this entry. */
  public abstract DeltaFormat deltaFormat();

  /** The explanation for {@link #deltaFormat}. */
  public abstract DeltaFormatExplanation deltaFormatExplanation();

  /** Builder for {@link PreDiffPlanEntry}. */
  @AutoValue.Builder
  public abstract static class Builder {

    /** @see #oldEntry() */
    abstract Builder oldEntry(MinimalZipEntry oldEntry);

    /** @see #newEntry() */
    abstract Builder newEntry(MinimalZipEntry oldEntry);

    /** @see #zipEntryUncompressionOption() */
    abstract Builder zipEntryUncompressionOption(ZipEntryUncompressionOption option);

    /** Getter for {@link #zipEntryUncompressionOption()} */
    public abstract ZipEntryUncompressionOption zipEntryUncompressionOption();

    /** @see #uncompressionOptionExplanation(UncompressionOptionExplanation) */
    abstract Builder uncompressionOptionExplanation(
        UncompressionOptionExplanation uncompressionOptionExplanation);

    /** Getter for {@link #uncompressionOptionExplanation()} ()} */
    public abstract UncompressionOptionExplanation uncompressionOptionExplanation();

    /** @see #deltaFormat() */
    abstract Builder deltaFormat(DeltaFormat deltaFormat);

    /** @see #deltaFormatExplanation() */
    abstract Builder deltaFormatExplanation(DeltaFormatExplanation deltaFormatExplanation);

    abstract PreDiffPlanEntry build();
  }

  /** Returns a {@link Builder} for {@link PreDiffPlanEntry}. */
  public static Builder builder() {
    return new AutoValue_PreDiffPlanEntry.Builder()
        .deltaFormat(DeltaFormat.BSDIFF)
        .deltaFormatExplanation(DeltaFormatExplanation.DEFAULT);
  }

  /** Returns a {@link Builder} with the current state. */
  public abstract Builder toBuilder();
}

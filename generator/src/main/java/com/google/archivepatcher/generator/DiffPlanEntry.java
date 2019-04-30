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

import com.google.archivepatcher.shared.Range;
import com.google.auto.value.AutoValue;

/**
 * A wrapper around {@link PreDiffPlanEntry} that includes extra metadata detailing the range of
 * this entry in the delta friendly blob generated to be passed to the delta generator.
 */
@AutoValue
public abstract class DiffPlanEntry {

  /** The underlying {@link PreDiffPlanEntry}. */
  public abstract PreDiffPlanEntry preDiffPlanEntry();

  /** Range of the local entry in the old delta-friendly blob. */
  public abstract Range oldDeltaFriendlyEntryRange();

  /** Range of the local entry in the old delta-friendly blob. */
  public abstract Range newDeltaFriendlyEntryRange();

  /** Creates a {@link DiffPlanEntry}. */
  public static DiffPlanEntry create(
      PreDiffPlanEntry preDiffPlanEntry,
      Range oldDeltaFriendlyEntryRange,
      Range newDeltaFriendlyEntryRange) {
    return new AutoValue_DiffPlanEntry(
        preDiffPlanEntry, oldDeltaFriendlyEntryRange, newDeltaFriendlyEntryRange);
  }

  /** Converts the current {@link DiffPlanEntry} to a {@link DeltaEntry}. */
  public DeltaEntry asDeltaEntry() {
    return DeltaEntry.builder()
        .deltaFormat(preDiffPlanEntry().deltaFormat())
        .oldBlobRange(oldDeltaFriendlyEntryRange())
        .newBlobRange(newDeltaFriendlyEntryRange())
        .build();
  }
}

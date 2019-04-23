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

/**
 * An encapsulation of delta entry in the patch generated. A {@link DeltaEntry} consists of both the
 * delta record and the delta data (see Patch Format).
 */
@AutoValue
public abstract class DeltaEntry {

  /** The {@link DeltaFormat} to use. */
  abstract DeltaFormat deltaFormat();

  /** The {@link Range} inside delta-friendly old blob to compute delta. */
  public abstract Range oldBlobRange();

  /** The {@link Range} inside delta-friendly new blob to compute delta. */
  public abstract Range newBlobRange();

  /** Builder for {@link DeltaEntry}. */
  @AutoValue.Builder
  public abstract static class Builder {
    /** See {@link #deltaFormat()}. */
    public abstract Builder deltaFormat(DeltaFormat deltaFormat);

    /** See {@link #oldBlobRange()}. */
    public abstract Builder oldBlobRange(Range oldBlobRange);

    /** See {@link #newBlobRange()}. */
    public abstract Builder newBlobRange(Range newBlobRange);

    public abstract DeltaEntry build();
  }

  public static Builder builder() {
    return new AutoValue_DeltaEntry.Builder();
  }
}

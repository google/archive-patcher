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

package com.google.archivepatcher.shared;

import com.google.auto.value.AutoValue;
import java.util.Comparator;

/** A range represented by an offset and a length. */
@AutoValue
public abstract class Range {

  /**
   * A comparator where comparison is performed based on the natural ordering of the offset field.
   */
  public static <T extends Range> Comparator<T> getOffsetCompartor() {
    return (o1, o2) -> Long.compare(o1.offset(), o2.offset());
  }

  /** Offset of the range. */
  public abstract long offset();

  /** Length of the range. */
  public abstract long length();

  /** Constructs a range from an offset and a length. */
  public static Range of(long offset, long length) {
    return new AutoValue_Range(offset, length);
  }

  public <T> TypedRange<T> withMetadata(T metadata) {
    return new TypedRange<>(this, metadata);
  }
}

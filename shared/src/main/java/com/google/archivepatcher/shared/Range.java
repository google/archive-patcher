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
  public static <T extends Range> Comparator<T> offsetComparator() {
    return (o1, o2) -> Long.compare(o1.offset(), o2.offset());
  }

  /** Offset of the range. */
  public abstract long offset();

  /** Length of the range. */
  public abstract long length();

  /** Offset of the end of the range. */
  public long endOffset() {
    return offset() + length();
  }

  /** Constructs a range from an offset and a length. */
  public static Range of(long offset, long length) {
    return new AutoValue_Range(offset, length);
  }

  /** Attaches a metadata to {@link Range} to obtain a {@link TypedRange}. */
  public <T> TypedRange<T> withMetadata(T metadata) {
    return new TypedRange<>(this, metadata);
  }

  /**
   * Combines two ranges together to form a new range.
   *
   * <p>The ranges must be adjacent to on another ({@link #isAdjacentTo(Range)} must return true).
   * An {@link IllegalArgumentException} will be thrown otherwise.
   */
  public static Range combine(Range range1, Range range2) {
    if (!range1.isAdjacentTo(range2)) {
      throw new IllegalArgumentException(
          range1 + " is not adjacent to " + range2 + " and cannot be combined");
    }
    return Range.of(Math.min(range1.offset(), range2.offset()), range1.length() + range2.length());
  }

  /**
   * Returns if the current range is adjacent to the other range.
   *
   * <p>Two ranges are adjacent if one's end offset (offset + length) is equal to the other's start
   * offset.
   */
  private boolean isAdjacentTo(Range otherRange) {
    return offset() + length() == otherRange.offset()
        || otherRange.offset() + otherRange.length() == offset();
  }
}

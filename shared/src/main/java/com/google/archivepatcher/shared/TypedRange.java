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

import java.util.Objects;

/**
 * A range, annotated with metadata, that is represented as an offset and a length.
 *
 * @param <T> the type of the metadata
 */
@SuppressWarnings("ExtendsAutoValue")
public class TypedRange<T> extends Range {

  /** Underlying range. */
  private final Range range;

  /**
   * Optional metadata associated with this range.
   */
  private final T metadata;

  /**
   * Constructs a new range with the specified parameters.
   *
   * @param range the underlying range
   * @param metadata optional metadata associated with this range
   */
  TypedRange(Range range, T metadata) {
    this.range = range;
    this.metadata = metadata;
  }

  @Override
  public long length() {
    return range.length();
  }

  @Override
  public long offset() {
    return range.offset();
  }

  /** Constructs a {@link TypedRange} from an offset, a length and metadata. */
  public static <T> TypedRange<T> of(long offset, long length, T metadata) {
    return new TypedRange<>(Range.of(offset, length), metadata);
  }

  @Override
  public String toString() {
    return "range: " + range + ", metadata: " + metadata;
  }

  /**
   * Returns the metadata associated with the range, or null if no metadata has been set.
   */
  public T getMetadata() {
    return metadata;
  }

  @Override
  public int hashCode() {
    return Objects.hash(range, metadata);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof TypedRange)) {
      return false;
    }
    TypedRange<?> other = (TypedRange<?>) obj;
    if (!range.equals(other.range)) {
      return false;
    }
    if (metadata == null) {
      if (other.metadata != null) return false;
    } else if (!metadata.equals(other.metadata)) return false;
    return true;
  }

}

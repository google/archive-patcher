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

import com.google.archivepatcher.shared.bytesource.ByteSource;
import java.io.OutputStream;
import java.util.List;

/**
 * A range represented by an offset and a length.
 *
 * <p>Conceptually, we would have {@link TypedRange} extend {@link Range}. But since {@link
 * DeltaFriendlyFile#generateDeltaFriendlyFile(List, ByteSource, OutputStream) need to copy over the
 * metadata (and thus cannot take {@link Range} arguments, we do it this way so that a list of
 * {@link Range}s can be passed to that method without being wrapped in {@link TypedRange}s.
 */
public class Range extends TypedRange<Void> {
  private Range(long offset, long length) {
    super(offset, length, null);
  }

  /** Constructs a range from an offset and a length. */
  public static Range of(long offset, long length) {
    return new Range(offset, length);
  }

  public <T> TypedRange<T> withMetadata(T metadata) {
    return TypedRange.of(getOffset(), getLength(), metadata);
  }
}

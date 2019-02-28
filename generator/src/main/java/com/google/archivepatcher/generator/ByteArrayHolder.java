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

import java.util.Arrays;

/**
 * Holds an array of bytes, implementing {@link #equals(Object)}, {@link #hashCode()} with deep
 * comparisons. This is intended primarily to allow raw, uninterpreted paths from
 * {@link MinimalZipEntry#getFileNameBytes()} to be used as map keys safely.
 */
public class ByteArrayHolder {
  /**
   * The backing byte array.
   */
  private final byte[] data;

  /**
   * Construct a new wrapper around the specified bytes.
   * @param data the byte array
   */
  public ByteArrayHolder(byte[] data) {
    this.data = data;
  }

  /**
   * Returns the actual byte array that backs the text.
   * @return the array
   */
  public byte[] getData() {
    return data;
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(data);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ByteArrayHolder other = (ByteArrayHolder) obj;
    return Arrays.equals(data, other.data);
  }
}

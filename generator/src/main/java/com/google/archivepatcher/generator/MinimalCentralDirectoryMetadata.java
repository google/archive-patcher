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

/**
 * Trivial struct containing the critical data about the central directory: the number of entries
 * it contains, the position within the file at which it starts, and its length.
 */
class MinimalCentralDirectoryMetadata {
  /**
   * The number of entries in the central directory.
   */
  private final int numEntriesInCentralDirectory;

  /**
   * The file offset of the first byte of the central directory.
   */
  private final long offsetOfCentralDirectory;

  /**
   * The length of the central directory, in bytes.
   */
  private final long lengthOfCentralDirectory;

  /**
   * Constructs a new metadata object with the specified values
   * @param numEntriesInCentralDirectory the number of entries in the central directory
   * @param offsetOfCentralDirectory the file offset of the first byte of the central directory
   * @param lengthOfCentralDirectory the length of the central directory, in bytes
   */
  MinimalCentralDirectoryMetadata(
      int numEntriesInCentralDirectory,
      long offsetOfCentralDirectory,
      long lengthOfCentralDirectory) {
    this.numEntriesInCentralDirectory = numEntriesInCentralDirectory;
    this.offsetOfCentralDirectory = offsetOfCentralDirectory;
    this.lengthOfCentralDirectory = lengthOfCentralDirectory;
  }

  /**
   * Returns the number of entries in the central directory.
   * @return as described
   */
  public final int getNumEntriesInCentralDirectory() {
    return numEntriesInCentralDirectory;
  }

  /**
   * Returns the file offset of the first byte of the central directory.
   * @return as described
   */
  public final long getOffsetOfCentralDirectory() {
    return offsetOfCentralDirectory;
  }

  /**
   * Returns the length of the central directory, in bytes.
   * @return as described
   */
  public final long getLengthOfCentralDirectory() {
    return lengthOfCentralDirectory;
  }
}

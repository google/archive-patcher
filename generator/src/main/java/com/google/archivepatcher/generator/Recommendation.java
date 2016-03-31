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
 * Recommendations for how to uncompress entries in old and new archives.
 */
public enum Recommendation {

  /**
   * Uncompress only the old entry.
   */
  UNCOMPRESS_OLD(true, false),

  /**
   * Uncompress only the new entry.
   */
  UNCOMPRESS_NEW(false, true),

  /**
   * Uncompress both the old and new entries.
   */
  UNCOMPRESS_BOTH(true, true),

  /**
   * Uncompress neither entry.
   */
  UNCOMPRESS_NEITHER(false, false);

  /**
   * True if the old entry should be uncompressed.
   */
  public final boolean uncompressOldEntry;

  /**
   * True if the new entry should be uncompressed.
   */
  public final boolean uncompressNewEntry;

  /**
   * Constructs a new recommendation with the specified behaviors.
   * @param uncompressOldEntry true if the old entry should be uncompressed
   * @param uncompressNewEntry true if the new entry should be uncompressed
   */
  private Recommendation(boolean uncompressOldEntry, boolean uncompressNewEntry) {
    this.uncompressOldEntry = uncompressOldEntry;
    this.uncompressNewEntry = uncompressNewEntry;
  }
}
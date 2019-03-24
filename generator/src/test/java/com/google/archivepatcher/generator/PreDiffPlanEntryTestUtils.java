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

/** Convenience methods for testing with {@link PreDiffPlanEntry}. */
public class PreDiffPlanEntryTestUtils {
  /**
   * Gets a {@link PreDiffPlanEntry.Builder} with compression settings corresponding to {@link
   * UncompressionOptionExplanation#COMPRESSED_CHANGED_TO_UNCOMPRESSED}
   */
  public static PreDiffPlanEntry.Builder getEntryBuilderWithCompressedToUncompressed() {
    return PreDiffPlanEntry.builder()
        .setUncompressionOption(
            ZipEntryUncompressionOption.UNCOMPRESS_OLD,
            UncompressionOptionExplanation.COMPRESSED_CHANGED_TO_UNCOMPRESSED);
  }

  /**
   * Gets a {@link PreDiffPlanEntry.Builder} with compression settings corresponding to {@link
   * UncompressionOptionExplanation#UNCOMPRESSED_CHANGED_TO_COMPRESSED}
   */
  public static PreDiffPlanEntry.Builder getEntryBuilderWithUncompressedToCompressed() {
    return PreDiffPlanEntry.builder()
        .setUncompressionOption(
            ZipEntryUncompressionOption.UNCOMPRESS_NEW,
            UncompressionOptionExplanation.UNCOMPRESSED_CHANGED_TO_COMPRESSED);
  }

  /**
   * Gets a {@link PreDiffPlanEntry.Builder} with compression settings corresponding to {@link
   * UncompressionOptionExplanation#COMPRESSED_BYTES_IDENTICAL}
   */
  public static PreDiffPlanEntry.Builder getEntryBuilderWithCompressedBytesIdentical() {
    return PreDiffPlanEntry.builder()
        .setUncompressionOption(
            ZipEntryUncompressionOption.UNCOMPRESS_NEITHER,
            UncompressionOptionExplanation.COMPRESSED_BYTES_IDENTICAL);
  }

  /**
   * Gets a {@link PreDiffPlanEntry.Builder} with compression settings corresponding to {@link
   * UncompressionOptionExplanation#BOTH_ENTRIES_UNCOMPRESSED}
   */
  public static PreDiffPlanEntry.Builder getEntryBuilderWithBothEntriesUncompressed() {
    return PreDiffPlanEntry.builder()
        .setUncompressionOption(
            ZipEntryUncompressionOption.UNCOMPRESS_NEITHER,
            UncompressionOptionExplanation.BOTH_ENTRIES_UNCOMPRESSED);
  }

  /**
   * Gets a {@link PreDiffPlanEntry.Builder} with compression settings corresponding to {@link
   * UncompressionOptionExplanation#UNSUITABLE}
   */
  public static PreDiffPlanEntry.Builder getEntryBuilderWithUnsuitable() {
    return PreDiffPlanEntry.builder()
        .setUncompressionOption(
            ZipEntryUncompressionOption.UNCOMPRESS_NEITHER,
            UncompressionOptionExplanation.UNSUITABLE);
  }

  /**
   * Gets a {@link PreDiffPlanEntry.Builder} with compression settings corresponding to {@link
   * UncompressionOptionExplanation#DEFLATE_UNSUITABLE}
   */
  public static PreDiffPlanEntry.Builder getEntryBuilderWithDeflateUnsuitable() {
    return PreDiffPlanEntry.builder()
        .setUncompressionOption(
            ZipEntryUncompressionOption.UNCOMPRESS_NEITHER,
            UncompressionOptionExplanation.DEFLATE_UNSUITABLE);
  }

  /**
   * Gets a {@link PreDiffPlanEntry.Builder} with compression settings corresponding to {@link
   * UncompressionOptionExplanation#COMPRESSED_BYTES_CHANGED}
   */
  public static PreDiffPlanEntry.Builder getEntryBuilderWithCompressedBytesChanged() {
    return PreDiffPlanEntry.builder()
        .setUncompressionOption(
            ZipEntryUncompressionOption.UNCOMPRESS_BOTH,
            UncompressionOptionExplanation.COMPRESSED_BYTES_CHANGED);
  }
}

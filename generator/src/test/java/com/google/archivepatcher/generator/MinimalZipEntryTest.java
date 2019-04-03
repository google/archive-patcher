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

import static com.google.common.truth.Truth.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link MinimalZipEntry}. */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class MinimalZipEntryTest {
  private static final int COMPRESSION_METHOD = 8; // (meaning deflate)
  private static final long CRC32 = 123;
  private static final long COMPRESSED_SIZE = 456;
  private static final long UNCOMPRESSED_SIZE = 789;
  private static final byte[] FILE_NAME_BYTES = new byte[] {'f', 'o', 'o', '.', 'b', 'a', 'r'};
  private static final boolean GENERAL_PURPOSE_BIT_FLAG_11 = true; // (meaning file name is UTF8)
  private static final long FILE_OFFSET_OF_LOCAL_ENTRY = 1337;
  private static final long FILE_OFFSET_OF_COMPRESSED_DATA = 2674;

  private MinimalZipEntry defaultEntry;
  private MinimalZipEntry clonedDefaultEntry;
  private MinimalZipEntry alteredCompressionMethod;
  private MinimalZipEntry alteredCrc32;
  private MinimalZipEntry alteredCompressedSize;
  private MinimalZipEntry alteredUncompressedSize;
  private MinimalZipEntry alteredFileNameBytes;
  private MinimalZipEntry alteredGeneralPurposeBitFlag11;
  private MinimalZipEntry alteredOffsetOfLocalEntry;
  private MinimalZipEntry alteredFileOffsetOfCompressedData;
  private List<MinimalZipEntry> allMutations;

  @Before
  public void setup() throws Exception {
    defaultEntry =
        new MinimalZipEntry(
            COMPRESSION_METHOD,
            CRC32,
            COMPRESSED_SIZE,
            UNCOMPRESSED_SIZE,
            FILE_NAME_BYTES,
            GENERAL_PURPOSE_BIT_FLAG_11,
            FILE_OFFSET_OF_LOCAL_ENTRY);
    clonedDefaultEntry =
        new MinimalZipEntry(
            COMPRESSION_METHOD,
            CRC32,
            COMPRESSED_SIZE,
            UNCOMPRESSED_SIZE,
            FILE_NAME_BYTES,
            GENERAL_PURPOSE_BIT_FLAG_11,
            FILE_OFFSET_OF_LOCAL_ENTRY);
    alteredCompressionMethod =
        new MinimalZipEntry(
            COMPRESSION_METHOD - 1,
            CRC32,
            COMPRESSED_SIZE,
            UNCOMPRESSED_SIZE,
            FILE_NAME_BYTES,
            GENERAL_PURPOSE_BIT_FLAG_11,
            FILE_OFFSET_OF_LOCAL_ENTRY);
    alteredCrc32 =
        new MinimalZipEntry(
            COMPRESSION_METHOD,
            CRC32 - 1,
            COMPRESSED_SIZE,
            UNCOMPRESSED_SIZE,
            FILE_NAME_BYTES,
            GENERAL_PURPOSE_BIT_FLAG_11,
            FILE_OFFSET_OF_LOCAL_ENTRY);
    alteredCompressedSize =
        new MinimalZipEntry(
            COMPRESSION_METHOD,
            CRC32,
            COMPRESSED_SIZE - 1,
            UNCOMPRESSED_SIZE,
            FILE_NAME_BYTES,
            GENERAL_PURPOSE_BIT_FLAG_11,
            FILE_OFFSET_OF_LOCAL_ENTRY);
    alteredUncompressedSize =
        new MinimalZipEntry(
            COMPRESSION_METHOD,
            CRC32,
            COMPRESSED_SIZE,
            UNCOMPRESSED_SIZE - 1,
            FILE_NAME_BYTES,
            GENERAL_PURPOSE_BIT_FLAG_11,
            FILE_OFFSET_OF_LOCAL_ENTRY);
    alteredFileNameBytes =
        new MinimalZipEntry(
            COMPRESSION_METHOD,
            CRC32,
            COMPRESSED_SIZE,
            UNCOMPRESSED_SIZE,
            new byte[] {'x'},
            GENERAL_PURPOSE_BIT_FLAG_11,
            FILE_OFFSET_OF_LOCAL_ENTRY);
    alteredGeneralPurposeBitFlag11 =
        new MinimalZipEntry(
            COMPRESSION_METHOD,
            CRC32,
            COMPRESSED_SIZE,
            UNCOMPRESSED_SIZE,
            FILE_NAME_BYTES,
            !GENERAL_PURPOSE_BIT_FLAG_11,
            FILE_OFFSET_OF_LOCAL_ENTRY);
    alteredOffsetOfLocalEntry =
        new MinimalZipEntry(
            COMPRESSION_METHOD,
            CRC32,
            COMPRESSED_SIZE,
            UNCOMPRESSED_SIZE,
            FILE_NAME_BYTES,
            GENERAL_PURPOSE_BIT_FLAG_11,
            FILE_OFFSET_OF_LOCAL_ENTRY - 1);
    alteredFileOffsetOfCompressedData =
        new MinimalZipEntry(
            COMPRESSION_METHOD,
            CRC32,
            COMPRESSED_SIZE,
            UNCOMPRESSED_SIZE,
            FILE_NAME_BYTES,
            GENERAL_PURPOSE_BIT_FLAG_11,
            FILE_OFFSET_OF_LOCAL_ENTRY);
    alteredFileOffsetOfCompressedData.setFileOffsetOfCompressedData(FILE_OFFSET_OF_COMPRESSED_DATA);
    allMutations =
        Collections.unmodifiableList(
            Arrays.asList(
                alteredCompressionMethod,
                alteredCrc32,
                alteredCompressedSize,
                alteredUncompressedSize,
                alteredFileNameBytes,
                alteredGeneralPurposeBitFlag11,
                alteredOffsetOfLocalEntry,
                alteredFileOffsetOfCompressedData));
  }

  @Test
  public void testGetFileName() throws Exception {
    // Make a string with some chars that are from DOS ANSI art days, these chars have different
    // binary representations in UTF8 and Cp437. We use light, medium, and dark "shade" characters
    // (0x2591, 0x2592, 0x2593 respectively) for this purpose. Go go ANSI art!
    // https://en.wikipedia.org/wiki/Code_page_437
    // https://en.wikipedia.org/wiki/Block_Elements
    String fileName = new String("\u2591\u2592\u2593AWESOME\u2593\u2592\u2591");
    byte[] utf8Bytes = fileName.getBytes("UTF8");
    byte[] cp437Bytes = fileName.getBytes("Cp437");
    assertThat(cp437Bytes).isNotEqualTo(utf8Bytes); // For test sanity

    MinimalZipEntry utf8Entry =
        new MinimalZipEntry(
            COMPRESSION_METHOD,
            CRC32,
            COMPRESSED_SIZE,
            UNCOMPRESSED_SIZE,
            utf8Bytes,
            true /* utf8 */,
            FILE_OFFSET_OF_LOCAL_ENTRY);
    assertThat(utf8Entry.getFileNameBytes()).isEqualTo(utf8Bytes);
    String fileNameFromUtf8Bytes = utf8Entry.getFileName();
    assertThat(fileNameFromUtf8Bytes).isEqualTo(fileName);

    MinimalZipEntry cp437Entry =
        new MinimalZipEntry(
            COMPRESSION_METHOD,
            CRC32,
            COMPRESSED_SIZE,
            UNCOMPRESSED_SIZE,
            cp437Bytes,
            false /* cp437 */,
            FILE_OFFSET_OF_LOCAL_ENTRY);
    assertThat(cp437Entry.getFileNameBytes()).isEqualTo(cp437Bytes);
    String fileNameFromCp437Bytes = cp437Entry.getFileName();
    assertThat(fileNameFromCp437Bytes).isEqualTo(fileName);
  }

  @Test
  public void testIsDeflateCompressed() {
    // Compression method == 8, and uncompressed size != compressed size
    assertThat(defaultEntry.isDeflateCompressed()).isTrue();
    // Compression method == 8, but uncompressed size == compressed size (ie, STOR'ed entry)
    MinimalZipEntry stored =
        new MinimalZipEntry(
            COMPRESSION_METHOD,
            CRC32,
            1000,
            1000,
            FILE_NAME_BYTES,
            GENERAL_PURPOSE_BIT_FLAG_11,
            FILE_OFFSET_OF_LOCAL_ENTRY);
    assertThat(stored.isDeflateCompressed()).isFalse();
    // Compression method != 8 (obviously not deflate)
    assertThat(alteredCompressionMethod.isDeflateCompressed()).isFalse();
  }

  @Test
  @SuppressWarnings({"EqualsIncompatibleType", "TruthSelfEquals"}) // For ErrorProne
  public void testEquals() {
    assertThat(defaultEntry).isEqualTo(defaultEntry);
    MinimalZipEntry clonedDefaultEntry =
        new MinimalZipEntry(
            COMPRESSION_METHOD,
            CRC32,
            COMPRESSED_SIZE,
            UNCOMPRESSED_SIZE,
            FILE_NAME_BYTES,
            GENERAL_PURPOSE_BIT_FLAG_11,
            FILE_OFFSET_OF_LOCAL_ENTRY);
    assertThat(clonedDefaultEntry).isEqualTo(defaultEntry);
    for (MinimalZipEntry mutation : allMutations) {
      assertThat(mutation).isNotEqualTo(defaultEntry);
    }
    assertThat(defaultEntry).isNotEqualTo(null);
    assertThat(defaultEntry).isNotEqualTo("foo");
  }

  @Test
  public void testHashCode() {
    Set<MinimalZipEntry> hashSet = new HashSet<>();
    hashSet.add(defaultEntry);
    hashSet.add(clonedDefaultEntry);
    assertThat(hashSet).hasSize(1);
    hashSet.addAll(allMutations);
    assertThat(hashSet).hasSize(1 + allMutations.size());
  }

  @Test
  public void testGetters() {
    assertThat(defaultEntry.getCompressedSize()).isEqualTo(COMPRESSED_SIZE);
    assertThat(defaultEntry.getCompressionMethod()).isEqualTo(COMPRESSION_METHOD);
    assertThat(defaultEntry.getCrc32OfUncompressedData()).isEqualTo(CRC32);
    assertThat(defaultEntry.getFileNameBytes()).isEqualTo(FILE_NAME_BYTES);
    assertThat(defaultEntry.getFileOffsetOfLocalEntry()).isEqualTo(FILE_OFFSET_OF_LOCAL_ENTRY);
    assertThat(defaultEntry.getGeneralPurposeFlagBit11()).isEqualTo(GENERAL_PURPOSE_BIT_FLAG_11);
    assertThat(defaultEntry.getUncompressedSize()).isEqualTo(UNCOMPRESSED_SIZE);

    // Special one, only alteredFileOffsetOfCompressedData has this field set...
    assertThat(defaultEntry.getFileOffsetOfCompressedData()).isEqualTo(-1);
    assertThat(alteredFileOffsetOfCompressedData.getFileOffsetOfCompressedData())
        .isEqualTo(FILE_OFFSET_OF_COMPRESSED_DATA);
  }
}

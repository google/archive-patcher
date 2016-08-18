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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
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
    Assert.assertFalse(Arrays.equals(utf8Bytes, cp437Bytes)); // For test sanity

    MinimalZipEntry utf8Entry =
        new MinimalZipEntry(
            COMPRESSION_METHOD,
            CRC32,
            COMPRESSED_SIZE,
            UNCOMPRESSED_SIZE,
            utf8Bytes,
            true /* utf8 */,
            FILE_OFFSET_OF_LOCAL_ENTRY);
    Assert.assertArrayEquals(utf8Bytes, utf8Entry.getFileNameBytes());
    String fileNameFromUtf8Bytes = utf8Entry.getFileName();
    Assert.assertEquals(fileName, fileNameFromUtf8Bytes);

    MinimalZipEntry cp437Entry =
        new MinimalZipEntry(
            COMPRESSION_METHOD,
            CRC32,
            COMPRESSED_SIZE,
            UNCOMPRESSED_SIZE,
            cp437Bytes,
            false /* cp437 */,
            FILE_OFFSET_OF_LOCAL_ENTRY);
    Assert.assertArrayEquals(cp437Bytes, cp437Entry.getFileNameBytes());
    String fileNameFromCp437Bytes = cp437Entry.getFileName();
    Assert.assertEquals(fileName, fileNameFromCp437Bytes);
  }

  @Test
  public void testIsDeflateCompressed() {
    // Compression method == 8, and uncompressed size != compressed size
    Assert.assertTrue(defaultEntry.isDeflateCompressed());
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
    Assert.assertFalse(stored.isDeflateCompressed());
    // Compression method != 8 (obviously not deflate)
    Assert.assertFalse(alteredCompressionMethod.isDeflateCompressed());
  }

  @Test
  @SuppressWarnings("EqualsIncompatibleType") // For ErrorProne
  public void testEquals() {
    Assert.assertEquals(defaultEntry, defaultEntry);
    MinimalZipEntry clonedDefaultEntry =
        new MinimalZipEntry(
            COMPRESSION_METHOD,
            CRC32,
            COMPRESSED_SIZE,
            UNCOMPRESSED_SIZE,
            FILE_NAME_BYTES,
            GENERAL_PURPOSE_BIT_FLAG_11,
            FILE_OFFSET_OF_LOCAL_ENTRY);
    Assert.assertEquals(defaultEntry, clonedDefaultEntry);
    for (MinimalZipEntry mutation : allMutations) {
      Assert.assertNotEquals(defaultEntry, mutation);
    }
    Assert.assertFalse(defaultEntry.equals(null));
    Assert.assertFalse(defaultEntry.equals("foo"));
  }

  @Test
  public void testHashCode() {
    Set<MinimalZipEntry> hashSet = new HashSet<>();
    hashSet.add(defaultEntry);
    hashSet.add(clonedDefaultEntry);
    Assert.assertEquals(1, hashSet.size());
    hashSet.addAll(allMutations);
    Assert.assertEquals(1 + allMutations.size(), hashSet.size());
  }

  @Test
  public void testGetters() {
    Assert.assertEquals(COMPRESSED_SIZE, defaultEntry.getCompressedSize());
    Assert.assertEquals(COMPRESSION_METHOD, defaultEntry.getCompressionMethod());
    Assert.assertEquals(CRC32, defaultEntry.getCrc32OfUncompressedData());
    Assert.assertArrayEquals(FILE_NAME_BYTES, defaultEntry.getFileNameBytes());
    Assert.assertEquals(FILE_OFFSET_OF_LOCAL_ENTRY, defaultEntry.getFileOffsetOfLocalEntry());
    Assert.assertEquals(GENERAL_PURPOSE_BIT_FLAG_11, defaultEntry.getGeneralPurposeFlagBit11());
    Assert.assertEquals(UNCOMPRESSED_SIZE, defaultEntry.getUncompressedSize());

    // Special one, only alteredFileOffsetOfCompressedData has this field set...
    Assert.assertEquals(-1, defaultEntry.getFileOffsetOfCompressedData());
    Assert.assertEquals(
        FILE_OFFSET_OF_COMPRESSED_DATA,
        alteredFileOffsetOfCompressedData.getFileOffsetOfCompressedData());
  }
}

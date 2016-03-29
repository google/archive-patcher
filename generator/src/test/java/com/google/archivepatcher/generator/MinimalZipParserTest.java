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

import com.google.archivepatcher.shared.RandomAccessFileInputStream;
import com.google.archivepatcher.shared.UnitTestZipArchive;
import com.google.archivepatcher.shared.UnitTestZipEntry;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Tests for {@link MinimalZipParser}.
 */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class MinimalZipParserTest {
  private byte[] unitTestZipArchive;

  @Before
  public void setup() throws Exception {
    unitTestZipArchive = UnitTestZipArchive.makeTestZip();
  }

  private void checkExpectedBytes(byte[] expectedData, int unitTestZipArchiveOffset) {
    for (int index = 0; index < 4; index++) {
      byte actualByte = unitTestZipArchive[unitTestZipArchiveOffset + index];
      Assert.assertEquals(expectedData[index], actualByte);
    }
  }

  @Test
  public void testLocateStartOfEocd_WithArray() {
    int eocdOffset = MinimalZipParser.locateStartOfEocd(unitTestZipArchive);
    checkExpectedBytes(new byte[] {0x50, 0x4b, 0x05, 0x06}, eocdOffset);
  }

  @Test
  public void testLocateStartOfEocd_WithArray_NoEocd() {
    int eocdOffset = MinimalZipParser.locateStartOfEocd(new byte[32768]);
    Assert.assertEquals(-1, eocdOffset);
  }

  @Test
  public void testLocateStartOfEocd_WithFile() throws IOException {
    // Create a temp file with some zeroes, the EOCD header, and more zeroes.
    int bytesBefore = 53754;
    int bytesAfter = 107;
    File tempFile = File.createTempFile("MinimalZipParserTest", "zip");
    tempFile.deleteOnExit();
    try {
      FileOutputStream out = new FileOutputStream(tempFile);
      out.write(new byte[bytesBefore]);
      out.write(new byte[] {0x50, 0x4b, 0x05, 0x06});
      out.write(new byte[bytesAfter]);
      out.flush();
      out.close();
    } catch (IOException e) {
      try {
        tempFile.delete();
      } catch (Exception ignored) {
        // Nothing
      }
      throw e;
    }

    // Now expect to find the EOCD at the right place.
    try (RandomAccessFileInputStream in = new RandomAccessFileInputStream(tempFile)) {
      long eocdOffset = MinimalZipParser.locateStartOfEocd(in, 32768);
      Assert.assertEquals(bytesBefore, eocdOffset);
    }
  }

  @Test
  public void testLocateStartOfEocd_WithFile_NoEocd() throws IOException {
    // Create a temp file with some zeroes and no EOCD header at all
    File tempFile = File.createTempFile("MinimalZipParserTest", "zip");
    tempFile.deleteOnExit();
    try {
      FileOutputStream out = new FileOutputStream(tempFile);
      out.write(new byte[4000]);
      out.flush();
      out.close();
    } catch (IOException e) {
      try {
        tempFile.delete();
      } catch (Exception ignored) {
        // Nothing
      }
      throw e;
    }

    // Now expect to find no EOCD.
    try (RandomAccessFileInputStream in = new RandomAccessFileInputStream(tempFile)) {
      long eocdOffset = MinimalZipParser.locateStartOfEocd(in, 4000);
      Assert.assertEquals(-1, eocdOffset);
    }
  }

  @Test
  public void testParseEocd() throws IOException {
    int eocdOffset = MinimalZipParser.locateStartOfEocd(unitTestZipArchive);
    ByteArrayInputStream in = new ByteArrayInputStream(unitTestZipArchive);
    Assert.assertEquals(eocdOffset, in.skip(eocdOffset));
    MinimalCentralDirectoryMetadata centralDirectoryMetadata = MinimalZipParser.parseEocd(in);
    Assert.assertNotNull(centralDirectoryMetadata);

    // Check that the central directory's first record is at the calculated offset
    //0x02014b50
    checkExpectedBytes(
        new byte[] {0x50, 0x4b, 0x01, 0x02},
        (int) centralDirectoryMetadata.getOffsetOfCentralDirectory());
    // Check that the central directory's length is correct, i.e. that the EOCD record follows it.
    long calculatedEndOfCentralDirectory =
        centralDirectoryMetadata.getOffsetOfCentralDirectory()
            + centralDirectoryMetadata.getLengthOfCentralDirectory();
    checkExpectedBytes(new byte[] {0x50, 0x4b, 0x05, 0x06}, (int) calculatedEndOfCentralDirectory);
    Assert.assertEquals(
        UnitTestZipArchive.allEntriesInFileOrder.size(),
        centralDirectoryMetadata.getNumEntriesInCentralDirectory());
  }

  @Test
  public void testParseCentralDirectoryEntry() throws Exception {
    ByteArrayInputStream in = new ByteArrayInputStream(unitTestZipArchive);
    in.mark(unitTestZipArchive.length);
    int eocdOffset = MinimalZipParser.locateStartOfEocd(unitTestZipArchive);
    Assert.assertEquals(eocdOffset, in.skip(eocdOffset));
    MinimalCentralDirectoryMetadata metadata = MinimalZipParser.parseEocd(in);
    in.reset();
    Assert.assertEquals(
        metadata.getOffsetOfCentralDirectory(), in.skip(metadata.getOffsetOfCentralDirectory()));

    // Read each entry and verify all fields *except* the value returned by
    // MinimalZipEntry.getFileOffsetOfCompressedData(), as that has yet to be computed.
    for (UnitTestZipEntry expectedEntry : UnitTestZipArchive.allEntriesInFileOrder) {
      MinimalZipEntry parsed = MinimalZipParser.parseCentralDirectoryEntry(in);
      Assert.assertEquals(expectedEntry.path, parsed.getFileName());

      // Verify that the local signature header is at the calculated position
      byte[] expectedSignatureBlock = new byte[] {0x50, 0x4b, 0x03, 0x04};
      for (int index = 0; index < 4; index++) {
        byte actualByte = unitTestZipArchive[((int) parsed.getFileOffsetOfLocalEntry()) + index];
        Assert.assertEquals(expectedSignatureBlock[index], actualByte);
      }

      if (expectedEntry.level > 0) {
        Assert.assertEquals(8 /* deflate */, parsed.getCompressionMethod());
      } else {
        Assert.assertEquals(0 /* store */, parsed.getCompressionMethod());
      }
      byte[] uncompressedContent = expectedEntry.getUncompressedBinaryContent();
      Assert.assertEquals(uncompressedContent.length, parsed.getUncompressedSize());
      CRC32 crc32 = new CRC32();
      crc32.update(uncompressedContent);
      Assert.assertEquals(crc32.getValue(), parsed.getCrc32OfUncompressedData());
      byte[] compressedContent = expectedEntry.getCompressedBinaryContent();
      Assert.assertEquals(compressedContent.length, parsed.getCompressedSize());
    }
  }

  @Test
  public void testParseLocalEntryAndGetCompressedDataOffset() throws Exception {
    ByteArrayInputStream in = new ByteArrayInputStream(unitTestZipArchive);
    in.mark(unitTestZipArchive.length);
    int eocdOffset = MinimalZipParser.locateStartOfEocd(unitTestZipArchive);
    Assert.assertEquals(eocdOffset, in.skip(eocdOffset));
    MinimalCentralDirectoryMetadata metadata = MinimalZipParser.parseEocd(in);
    in.reset();
    Assert.assertEquals(
        metadata.getOffsetOfCentralDirectory(), in.skip(metadata.getOffsetOfCentralDirectory()));

    // Read each entry and verify all fields *except* the value returned by
    // MinimalZipEntry.getFileOffsetOfCompressedData(), as that has yet to be computed.
    List<MinimalZipEntry> parsedEntries = new ArrayList<MinimalZipEntry>();
    for (int x = 0; x < UnitTestZipArchive.allEntriesInFileOrder.size(); x++) {
      parsedEntries.add(MinimalZipParser.parseCentralDirectoryEntry(in));
    }

    for (int x = 0; x < UnitTestZipArchive.allEntriesInFileOrder.size(); x++) {
      UnitTestZipEntry expectedEntry = UnitTestZipArchive.allEntriesInFileOrder.get(x);
      MinimalZipEntry parsedEntry = parsedEntries.get(x);
      in.reset();
      Assert.assertEquals(
          parsedEntry.getFileOffsetOfLocalEntry(),
          in.skip(parsedEntry.getFileOffsetOfLocalEntry()));
      long relativeDataOffset = MinimalZipParser.parseLocalEntryAndGetCompressedDataOffset(in);
      Assert.assertTrue(relativeDataOffset > 0);
      checkExpectedBytes(
          expectedEntry.getCompressedBinaryContent(),
          (int) (parsedEntry.getFileOffsetOfLocalEntry() + relativeDataOffset));
    }
  }
}

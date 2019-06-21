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

import static com.google.archivepatcher.shared.PatchConstants.CompressionMethod.DEFLATE;
import static com.google.archivepatcher.shared.PatchConstants.CompressionMethod.STORED;
import static com.google.common.truth.Truth.assertThat;

import com.google.archivepatcher.shared.UnitTestZipArchive;
import com.google.archivepatcher.shared.UnitTestZipEntry;
import com.google.archivepatcher.shared.bytesource.ByteSource;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import static org.junit.Assume.assumeTrue;
import com.google.archivepatcher.shared.DefaultDeflateCompatibilityWindow;

/** Tests for {@link MinimalZipParser}. */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class MinimalZipParserTest {
  private byte[] unitTestZipArchive;

  @Before
  public void setup() throws Exception {
    // TODO: fix compatibility in OpenJDK 1.8 (or higher)
    assumeTrue(new DefaultDeflateCompatibilityWindow().isCompatible());

    unitTestZipArchive = UnitTestZipArchive.makeTestZip();
  }

  private void checkExpectedBytes(byte[] expectedData, int unitTestZipArchiveOffset) {
    for (int index = 0; index < 4; index++) {
      byte actualByte = unitTestZipArchive[unitTestZipArchiveOffset + index];
      assertThat(actualByte).isEqualTo(expectedData[index]);
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
    assertThat(eocdOffset).isEqualTo(-1);
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
    try (ByteSource in = ByteSource.fromFile(tempFile)) {
      long eocdOffset = MinimalZipParser.locateStartOfEocd(in, 32768);
      assertThat(eocdOffset).isEqualTo(bytesBefore);
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
    try (ByteSource in = ByteSource.fromFile(tempFile)) {
      long eocdOffset = MinimalZipParser.locateStartOfEocd(in, 4000);
      assertThat(eocdOffset).isEqualTo(-1);
    }
  }

  @Test
  public void testParseEocd() throws IOException {
    int eocdOffset = MinimalZipParser.locateStartOfEocd(unitTestZipArchive);
    ByteArrayInputStream in = new ByteArrayInputStream(unitTestZipArchive);
    assertThat(in.skip(eocdOffset)).isEqualTo(eocdOffset);
    MinimalCentralDirectoryMetadata centralDirectoryMetadata = MinimalZipParser.parseEocd(in);
    assertThat(centralDirectoryMetadata).isNotNull();

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
    assertThat(centralDirectoryMetadata.getNumEntriesInCentralDirectory())
        .isEqualTo(UnitTestZipArchive.ALL_ENTRIES.size());
  }

  @Test
  public void testParseCentralDirectoryEntry() throws Exception {
    ByteArrayInputStream in = new ByteArrayInputStream(unitTestZipArchive);
    in.mark(unitTestZipArchive.length);
    int eocdOffset = MinimalZipParser.locateStartOfEocd(unitTestZipArchive);
    assertThat(in.skip(eocdOffset)).isEqualTo(eocdOffset);
    MinimalCentralDirectoryMetadata metadata = MinimalZipParser.parseEocd(in);
    in.reset();
    assertThat(in.skip(metadata.getOffsetOfCentralDirectory()))
        .isEqualTo(metadata.getOffsetOfCentralDirectory());

    // Read each entry and verify all fields *except* the value returned by
    // fileOffsetOfCompressedData() and getLengthOfLocalEntry, as those have yet to be computed.
    for (UnitTestZipEntry expectedEntry : UnitTestZipArchive.ALL_ENTRIES) {
      MinimalZipEntry parsed =
          MinimalZipParser.parseCentralDirectoryEntry(in)
              .fileOffsetOfCompressedData(0)
              .lengthOfLocalEntry(0)
              .build();
      assertThat(parsed.getFileName()).isEqualTo(expectedEntry.path);

      // Verify that the local signature header is at the calculated position
      byte[] expectedSignatureBlock = new byte[] {0x50, 0x4b, 0x03, 0x04};
      for (int index = 0; index < 4; index++) {
        byte actualByte = unitTestZipArchive[((int) parsed.localEntryRange().offset()) + index];
        assertThat(actualByte).isEqualTo(expectedSignatureBlock[index]);
      }

      if (expectedEntry.level > 0) {
        assertThat(parsed.compressionMethod()).isEqualTo(DEFLATE);
      } else {
        assertThat(parsed.compressionMethod()).isEqualTo(STORED);
      }
      byte[] uncompressedContent = expectedEntry.getUncompressedBinaryContent();
      assertThat(parsed.uncompressedSize()).isEqualTo(uncompressedContent.length);
      CRC32 crc32 = new CRC32();
      crc32.update(uncompressedContent);
      assertThat(parsed.crc32OfUncompressedData()).isEqualTo(crc32.getValue());
      byte[] compressedContent = expectedEntry.getCompressedBinaryContent();
      assertThat(parsed.compressedDataRange().length()).isEqualTo(compressedContent.length);
    }
  }

  @Test
  public void testParseLocalEntryAndGetCompressedDataOffset() throws Exception {
    ByteArrayInputStream in = new ByteArrayInputStream(unitTestZipArchive);
    in.mark(unitTestZipArchive.length);
    int eocdOffset = MinimalZipParser.locateStartOfEocd(unitTestZipArchive);
    assertThat(in.skip(eocdOffset)).isEqualTo(eocdOffset);
    MinimalCentralDirectoryMetadata metadata = MinimalZipParser.parseEocd(in);
    in.reset();
    assertThat(in.skip(metadata.getOffsetOfCentralDirectory()))
        .isEqualTo(metadata.getOffsetOfCentralDirectory());

    // Read each entry and verify all fields *except* the value returned by
    // MinimalZipEntry.fileOffsetOfCompressedData(), as that has yet to be computed.
    List<MinimalZipEntry.Builder> parsedEntryBuilders = new ArrayList<>();
    for (int x = 0; x < UnitTestZipArchive.ALL_ENTRIES.size(); x++) {
      parsedEntryBuilders.add(MinimalZipParser.parseCentralDirectoryEntry(in));
    }

    for (int x = 0; x < UnitTestZipArchive.ALL_ENTRIES.size(); x++) {
      UnitTestZipEntry expectedEntry = UnitTestZipArchive.ALL_ENTRIES.get(x);
      MinimalZipEntry.Builder parsedEntryBuilder = parsedEntryBuilders.get(x);
      in.reset();
      assertThat(in.skip(parsedEntryBuilder.fileOffsetOfLocalEntry()))
          .isEqualTo(parsedEntryBuilder.fileOffsetOfLocalEntry());
      long relativeDataOffset = MinimalZipParser.parseLocalEntryAndGetCompressedDataOffset(in);
      assertThat(relativeDataOffset > 0).isTrue();
      checkExpectedBytes(
          expectedEntry.getCompressedBinaryContent(),
          (int) (parsedEntryBuilder.fileOffsetOfLocalEntry() + relativeDataOffset));
    }
  }
}

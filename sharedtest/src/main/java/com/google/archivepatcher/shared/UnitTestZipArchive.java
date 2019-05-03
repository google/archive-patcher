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

import static com.google.archivepatcher.shared.bytesource.ByteStreams.copy;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * A testing construct that provides a well-known archive and metadata about it. The archive
 * contains four files, one each at compression levels 0 (stored), 1 (fastest), 6 (default), and 9
 * (maximum compression). Two of the files have comments in the central directory, two do not. Each
 * has unique content with a distinct CRC32. The archive has had its dates normalized, so the date
 * and time will be the beginning of the epoch. The goal is to provide a reasonably robust test for
 * the logic in MinimalZipParser, but other unit test code also uses this functionality to construct
 * contrived data for testing. Exotic stuff like extra data padded at the beginning or in-between
 * entries, zip64 support and so on are not present; the goal is not to exhaustively test compliance
 * with the zip spec, but rather to ensure that the code works with most common zip files that are
 * likely to be encountered in the real world.
 */
public class UnitTestZipArchive {
  /**
   * The data for the first entry in the zip file, compressed at level 1. Has no comment.
   */
  public static final UnitTestZipEntry entry1 =
      makeUnitTestZipEntry(
          "file1", // path / filename
          1, // compression level
          "This is the content of file 1, at level 1. No comment.",
          null); // comment

  /**
   * The data for the second entry in the zip file, compressed at level 6. Has no comment.
   */
  public static final UnitTestZipEntry entry2 =
      makeUnitTestZipEntry(
          "file2", // path / filename
          6, // compression level
          "Here is some content for file 2, at level 6. No comment.",
          null); // comment

  /**
   * The data for the third entry in the zip file, compressed at level 9. Has a comment.
   */
  public static final UnitTestZipEntry entry3 =
      makeUnitTestZipEntry(
          "file3", // path / filename
          9, // compression level
          "And some other content for file 3, at level 9. With comment.",
          "COMMENT3"); // comment

  /**
   * The data for the fourth entry in the zip file, stored (uncompressed / level 0). Has a comment.
   */
  public static final UnitTestZipEntry entry4 =
      makeUnitTestZipEntry(
          "file4", // path / filename
          0, // compression level
          "File 4 data here, this is stored uncompressed. With comment.",
          "COMMENT4"); // comment

  /**
   * Invokes {@link #makeUnitTestZipEntry(String, int, boolean, String, String)} with nowrap=true.
   * @param path the file path
   * @param level the level the entry is compressed with
   * @param contentPrefix the content prefix - the corpus body will be appended to this value to
   * produce the final content for the entry
   * @param comment the comment to add to the file in the central directory, if any
   * @return the newly created entry
   */
  public static final UnitTestZipEntry makeUnitTestZipEntry(
      String path, int level, String contentPrefix, String comment) {
    return makeUnitTestZipEntry(path, level, true, contentPrefix, comment);
  }

  /**
   * Makes a unit test entry using the specified parameters <em>plus</em> the corpus from
   * {@link DefaultDeflateCompatibilityWindow#getCorpus()} to provide enough data for an accurate
   * level identification.
   * @param path the file path
   * @param level the level the entry is compressed with
   * @param nowrap the value for the nowrap flag
   * @param contentPrefix the content prefix - the corpus body will be appended to this value to
   * produce the final content for the entry
   * @param comment the comment to add to the file in the central directory, if any
   * @return the newly created entry
   */
  public static final UnitTestZipEntry makeUnitTestZipEntry(
      String path, int level, boolean nowrap, String contentPrefix, String comment) {
    String corpusText;
    try {
      corpusText = new String(new DefaultDeflateCompatibilityWindow().getCorpus(), "US-ASCII");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("System doesn't support US-ASCII", e);
    }
    return new UnitTestZipEntry(path, level, nowrap, contentPrefix + corpusText, comment);
  }

  /**
   * Makes a unit test entry that corresponds to an embedded archive containing the entries in
   * {@code internalEntries}.
   */
  public static final UnitTestZipEntry makeEmbeddedZipEntry(
      String path, int level, List<UnitTestZipEntry> internalEntries, String comment) {
    byte[] zipData = makeTestZip(internalEntries);

    return new UnitTestZipEntry(path, level, true, zipData, comment);
  }

  /**
   * All of the entries in the zip file, in the order in which their local entries appear in the
   * file.
   */
  public static final ImmutableList<UnitTestZipEntry> ALL_ENTRIES =
      ImmutableList.of(entry1, entry2, entry3, entry4);

  // At class load time, ensure that it is safe to use this class for other tests.
  static {
    try {
      verifyTestZip(makeTestZip(), ALL_ENTRIES);
      verifyTestZip(
          makeEmbeddedZipEntry("/abc", 0, ALL_ENTRIES, null).getUncompressedBinaryContent(),
          ALL_ENTRIES);
    } catch (Exception e) {
      throw new RuntimeException("Core sanity test 1 has failed, unit tests are unreliable", e);
    }
  }

  /**
   * Make a test ZIP file in memory and return it as a byte array. The ZIP contains the entries
   * described by {@link #entry1}, {@link #entry2}, {@link #entry3}, and {@link #entry4}. In
   * general, unit tests should use this data for all testing.
   * @return the zip file described above, as a byte array
   */
  public static byte[] makeTestZip() {
    return makeTestZip(ALL_ENTRIES);
  }

  /**
   * Make an arbitrary zip archive in memory using the specified entries.
   * @param entriesInFileOrder the entries
   * @return the zip file described above, as a byte array
   */
  public static byte[] makeTestZip(List<UnitTestZipEntry> entriesInFileOrder) {
    try {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      ZipOutputStream zipOut = new ZipOutputStream(buffer);
      for (UnitTestZipEntry unitTestEntry : entriesInFileOrder) {
        ZipEntry zipEntry = new ZipEntry(unitTestEntry.path);
        zipOut.setLevel(unitTestEntry.level);
        CRC32 crc32 = new CRC32();
        byte[] uncompressedContent = unitTestEntry.getUncompressedBinaryContent();
        crc32.update(uncompressedContent);
        zipEntry.setCrc(crc32.getValue());
        zipEntry.setSize(uncompressedContent.length);
        if (unitTestEntry.level == 0) {
          zipOut.setMethod(ZipOutputStream.STORED);
          zipEntry.setCompressedSize(uncompressedContent.length);
        } else {
          zipOut.setMethod(ZipOutputStream.DEFLATED);
        }
        // Normalize MSDOS date/time fields to zero for reproducibility.
        zipEntry.setTime(0);
        if (unitTestEntry.comment != null) {
          zipEntry.setComment(unitTestEntry.comment);
        }
        zipOut.putNextEntry(zipEntry);
        zipOut.write(unitTestEntry.getUncompressedBinaryContent());
        zipOut.closeEntry();
      }
      zipOut.close();
      return buffer.toByteArray();
    } catch (IOException e) {
      // Should not happen as this is all in memory
      throw new RuntimeException("Unable to generate test zip!", e);
    }
  }

  /**
   * Verifies the test zip file created by {@link #makeTestZip()} or for sanity, so that the rest of
   * the tests can safely rely upon them. The outputs may be slightly different from platform to
   * platform due to, e.g., filesystem differences that affect the choice of string encoding or
   * filesystem attributes that are preserved (eg, NTFS versus POSIX).
   *
   * @param data the data to verify
   * @throws Exception if verification fails
   */
  private static void verifyTestZip(byte[] data, List<UnitTestZipEntry> entries) throws Exception {
    ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(data));
    for (UnitTestZipEntry expectedEntry : entries) {
      ZipEntry zipEntry = zipIn.getNextEntry();
      checkEntry(zipEntry, zipIn, expectedEntry);
      zipIn.closeEntry();
    }
    assertThat(zipIn.getNextEntry()).isNull();
    zipIn.close();
  }

  /**
   * Save the test archive to a file.
   * @param file the file to write to
   * @throws IOException if unable to write the file
   */
  public static void saveTestZip(File file) throws IOException {
    FileOutputStream out = new FileOutputStream(file);
    out.write(makeTestZip());
    out.flush();
    out.close();
  }

  /**
   * Check that the entry's content matches the expected content. If this is the entry that is
   * uncompressed, also asserts that it is in fact uncompressed.
   *
   * @param entry the entry to check
   * @param zipIn the input stream to read from
   * @throws IOException if anything goes wrong
   */
  private static void checkEntry(
      ZipEntry entry, ZipInputStream zipIn, UnitTestZipEntry expectedEntry) throws IOException {
    // NB: File comments cannot be verified because the comments are in the central directory, which
    // is later in the stream.
    assertThat(entry.getName()).isEqualTo(expectedEntry.path);
    if (expectedEntry.level == 0) {
      // This entry should be uncompressed. So the "compressed" size should be the same as the
      // uncompressed size.
      assertThat(entry.getMethod()).isEqualTo(0);
      assertThat(entry.getCompressedSize())
          .isEqualTo(expectedEntry.getUncompressedBinaryContent().length);
    }
    ByteArrayOutputStream uncompressedData = new ByteArrayOutputStream();
    copy(zipIn, uncompressedData);
    assertThat(uncompressedData.toByteArray())
        .isEqualTo(expectedEntry.getUncompressedBinaryContent());
  }
}

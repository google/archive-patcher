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

package com.google.archivepatcher.integrationtest;

import com.google.archivepatcher.applier.FileByFileV1DeltaApplier;
import com.google.archivepatcher.generator.FileByFileV1DeltaGenerator;
import com.google.archivepatcher.shared.UnitTestZipArchive;
import com.google.archivepatcher.shared.UnitTestZipEntry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * High-level integration tests that fully exercise the code without any mocking or subclassing.
 */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class FileByFileV1IntegrationTest {

  // Inputs to the patching system
  private File tempDir = null;
  private File oldFile = null;
  private File newFile = null;

  // A test archive. The contents are as follows:
  // PATH       OLD_FORMAT          NEW_FORMAT          NOTES
  // ---------------------------------------------------------------------------
  // /entry1    compressed (L6)     compressed (L6)     Unchanged, compressed
  // /entry2    compressed (L6)     compressed (L9)     Only compressed bytes changed
  // /entry3    compressed (L6)     compressed (L6)     Compressed and uncompressed bytes changed
  // /entry4    uncompressed        compressed (L6)     Transition from uncompressed to compressed
  // /entry5    compressed (L6)     uncompressed        Transition from compressed to uncompressed
  // /entry6    uncompressed        uncompressed        Unchanged, uncompressed
  // /entry7    uncompressed        uncompressed        Uncompressed and bytes changed
  // /entry8    uncompressed        compressed (L6)     Like /entry4 but also with changed bytes
  // /entry9    compressed (L6)     uncompressed        Like /entry5 but also with changed bytes
  // /entry10*  compressed (L6)     compressed (L6)     Renamed from /entry10A to /entry10B
  // /entry11*  uncompressed        uncompressed        Renamed from /entry11A to /entry11B
  // /entry12*  compressed (L6)     compressed (L6)     Like /entry10 but also with changed bytes
  // /entry13*  uncompressed        uncompressed        Like /entry11 but also with changed bytes
  private static final UnitTestZipEntry OLD_ENTRY1 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry1", 6, "entry 1", null);
  private static final UnitTestZipEntry NEW_ENTRY1 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry1", 6, "entry 1", null);
  private static final UnitTestZipEntry OLD_ENTRY2 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry2", 6, "entry 2", null);
  private static final UnitTestZipEntry NEW_ENTRY2 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry2", 9, "entry 2", null);
  private static final UnitTestZipEntry OLD_ENTRY3 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry3", 6, "entry 3A", null);
  private static final UnitTestZipEntry NEW_ENTRY3 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry3", 6, "entry 3B", null);
  private static final UnitTestZipEntry OLD_ENTRY4 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry4", 0, "entry 4", null);
  private static final UnitTestZipEntry NEW_ENTRY4 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry4", 6, "entry 4", null);
  private static final UnitTestZipEntry OLD_ENTRY5 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry5", 6, "entry 5", null);
  private static final UnitTestZipEntry NEW_ENTRY5 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry5", 0, "entry 5", null);
  private static final UnitTestZipEntry OLD_ENTRY6 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry6", 0, "entry 6", null);
  private static final UnitTestZipEntry NEW_ENTRY6 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry6", 0, "entry 6", null);
  private static final UnitTestZipEntry OLD_ENTRY7 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry7", 0, "entry 7A", null);
  private static final UnitTestZipEntry NEW_ENTRY7 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry7", 0, "entry 7B", null);
  private static final UnitTestZipEntry OLD_ENTRY8 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry8", 0, "entry 8A", null);
  private static final UnitTestZipEntry NEW_ENTRY8 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry8", 6, "entry 8B", null);
  private static final UnitTestZipEntry OLD_ENTRY9 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry9", 6, "entry 9A", null);
  private static final UnitTestZipEntry NEW_ENTRY9 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry9", 0, "entry 9B", null);
  private static final UnitTestZipEntry OLD_ENTRY10 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry10A", 6, "entry 10", null);
  private static final UnitTestZipEntry NEW_ENTRY10 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry10B", 6, "entry 10", null);
  private static final UnitTestZipEntry OLD_ENTRY11 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry11A", 0, "entry 11", null);
  private static final UnitTestZipEntry NEW_ENTRY11 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry11B", 0, "entry 11", null);
  private static final UnitTestZipEntry OLD_ENTRY12 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry12A", 6, "entry 12A", null);
  private static final UnitTestZipEntry NEW_ENTRY12 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry12B", 6, "entry 12B", null);
  private static final UnitTestZipEntry OLD_ENTRY13 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry13A", 0, "entry 13A", null);
  private static final UnitTestZipEntry NEW_ENTRY13 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry13B", 0, "entry 13B", null);

  @Before
  public void setUp() throws IOException {
    oldFile = File.createTempFile("fbf_test", "old");
    oldFile.deleteOnExit();
    newFile = File.createTempFile("fbf_test", "new");
    newFile.deleteOnExit();
    tempDir = oldFile.getParentFile();
  }

  @After
  public void tearDown() {
    oldFile.delete();
    newFile.delete();
  }

  private static void writeFile(File file, byte[] content) throws IOException {
    try (FileOutputStream out = new FileOutputStream(file)) {
      out.write(content);
      out.flush();
    }
  }

  /**
   * High-level integration test that covers the most common kinds of operations expected to be
   * found in the real world.
   */
  @Test
  public void testPatchAndApply() throws Exception {
    // Write the old archive to disk.
    byte[] oldArchiveBytes = UnitTestZipArchive.makeTestZip(Arrays.asList(
        OLD_ENTRY1,
        OLD_ENTRY2,
        OLD_ENTRY3,
        OLD_ENTRY4,
        OLD_ENTRY5,
        OLD_ENTRY6,
        OLD_ENTRY7,
        OLD_ENTRY8,
        OLD_ENTRY9,
        OLD_ENTRY10,
        OLD_ENTRY11,
        OLD_ENTRY12,
        OLD_ENTRY13));
    writeFile(oldFile, oldArchiveBytes);

    // Write the new archive to disk. Flip the order around to fully exercise reordering logic where
    // the offsets might otherwise be exactly the same by chance.
    List<UnitTestZipEntry> newEntries = Arrays.asList(
        NEW_ENTRY1,
        NEW_ENTRY2,
        NEW_ENTRY3,
        NEW_ENTRY4,
        NEW_ENTRY5,
        NEW_ENTRY6,
        NEW_ENTRY7,
        NEW_ENTRY8,
        NEW_ENTRY9,
        NEW_ENTRY10,
        NEW_ENTRY11,
        NEW_ENTRY12,
        NEW_ENTRY13);
    Collections.reverse(newEntries);
    byte[] newArchiveBytes = UnitTestZipArchive.makeTestZip(newEntries);
    writeFile(newFile, newArchiveBytes);

    // Generate the patch.
    ByteArrayOutputStream patchBuffer = new ByteArrayOutputStream();
    FileByFileV1DeltaGenerator generator = new FileByFileV1DeltaGenerator();
    generator.generateDelta(oldFile, newFile, patchBuffer);

    // Apply the patch.
    FileByFileV1DeltaApplier applier = new FileByFileV1DeltaApplier(tempDir);
    ByteArrayInputStream patchIn = new ByteArrayInputStream(patchBuffer.toByteArray());
    ByteArrayOutputStream newOut = new ByteArrayOutputStream();
    applier.applyDelta(oldFile, patchIn, newOut);

    // Finally, expect that the result of applying the patch is exactly the same as the new archive
    // that was written to disk.
    Assert.assertArrayEquals(newArchiveBytes, newOut.toByteArray());
  }
}

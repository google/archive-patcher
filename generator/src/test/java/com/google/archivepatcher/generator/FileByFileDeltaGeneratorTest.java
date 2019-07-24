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

import static com.google.archivepatcher.shared.PatchConstants.DeltaFormat.BSDIFF;
import static com.google.archivepatcher.shared.PatchConstants.DeltaFormat.FILE_BY_FILE;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import com.google.archivepatcher.shared.PatchConstants.DeltaFormat;
import com.google.archivepatcher.shared.UnitTestZipArchive;
import com.google.archivepatcher.shared.UnitTestZipEntry;
import com.google.archivepatcher.shared.bytesource.ByteStreams;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import com.google.archivepatcher.shared.DefaultDeflateCompatibilityWindow;

/**
 * Tests for {@link FileByFileDeltaGenerator}. This relies heavily on the correctness of {@link
 * PatchWriterTest}, which validates the patch writing process itself, {@link PreDiffPlannerTest},
 * which validates the decision making process for delta-friendly blobs, and {@link
 * PreDiffExecutorTest}, which validates the ability to create the delta-friendly blobs. The {@link
 * FileByFileDeltaGenerator} <em>itself</em> is relatively simple, combining all of these pieces of
 * functionality together to create a patch; so the tests here are just ensuring that a patch can be
 * produced.
 */
@RunWith(Parameterized.class)
@SuppressWarnings("javadoc")
public class FileByFileDeltaGeneratorTest {

  @Parameters
  public static Collection<Object[]> data() {
    // Note that the order of the parameter is important for gradle to ignore the native test.
    return Arrays.asList(
        new Object[][] {
          {true, "5aa9a44d"},
          {false, "3247bb5c"}
        });
  }

  private static final UnitTestZipEntry OLD_ENTRY1 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry1", 6, "entry 1 old", null);
  private static final UnitTestZipEntry NEW_ENTRY1 =
      UnitTestZipArchive.makeUnitTestZipEntry("/entry1", 6, "entry 1 new", null);
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
  private static final UnitTestZipEntry OLD_ARCHIVE_ENTRY_1 =
      UnitTestZipArchive.makeEmbeddedZipEntry(
          "/embedded-entry-1.zip", 0, ImmutableList.of(OLD_ENTRY1, OLD_ENTRY2), null);
  private static final UnitTestZipEntry NEW_ARCHIVE_ENTRY_1 =
      UnitTestZipArchive.makeEmbeddedZipEntry(
          "/embedded-entry-1.zip", 0, ImmutableList.of(NEW_ENTRY1, NEW_ENTRY2), null);
  private static final UnitTestZipEntry OLD_ARCHIVE_ENTRY_2 =
      UnitTestZipArchive.makeEmbeddedZipEntry(
          "/embedded-entry-2.apk", 6, ImmutableList.of(OLD_ENTRY3), null);
  private static final UnitTestZipEntry NEW_ARCHIVE_ENTRY_2 =
      UnitTestZipArchive.makeEmbeddedZipEntry(
          "/embedded-entry-2.apk", 9, ImmutableList.of(NEW_ENTRY3), null);
  private static final UnitTestZipEntry CORRUPTED_ARCHIVE_ENTRY_1 =
      UnitTestZipArchive.makeUnitTestZipEntry("/embedded-entry-1.zip", 0, "abc", null);

  private final boolean useNativeBsDiff;
  private final String expectedCrc32;

  public FileByFileDeltaGeneratorTest(boolean useNativeBsDiff, String expectedCrc32) {
    this.useNativeBsDiff = useNativeBsDiff;
    this.expectedCrc32 = expectedCrc32;
  }

  @Test
  public void generateDelta_BaseCase() throws Exception {
    byte[] input = UnitTestZipArchive.makeTestZip();

    byte[] result = generateDelta(input, input, ImmutableSet.of(BSDIFF));

    // TODO: figure out why 1.8.0_201 passes compatibility test but fail to generate
    //   identical patches on Kokoro machines
    assumeTrue(Hashing.crc32().hashBytes(input).toString().equals("5368efdc"));
    assumeTrue(new DefaultDeflateCompatibilityWindow().isCompatible());

    assertThat(Hashing.crc32().hashBytes(result).toString()).isEqualTo(expectedCrc32);
  }

  @Test
  public void generateDelta_withUnchangedEmbeddedArchive() throws Exception {
    byte[] oldArchiveBytes =
        UnitTestZipArchive.makeTestZip(
            ImmutableList.of(OLD_ENTRY1, OLD_ARCHIVE_ENTRY_1, OLD_ENTRY2, OLD_ARCHIVE_ENTRY_2));
    byte[] newArchiveBytes =
        UnitTestZipArchive.makeTestZip(
            ImmutableList.of(NEW_ENTRY1, OLD_ARCHIVE_ENTRY_1, NEW_ENTRY2, OLD_ARCHIVE_ENTRY_2));

    byte[] bsdiffOnlyDelta =
        generateDelta(oldArchiveBytes, newArchiveBytes, ImmutableSet.of(BSDIFF));
    byte[] bsdiffWithFbfDelta =
        generateDelta(oldArchiveBytes, newArchiveBytes, ImmutableSet.of(BSDIFF, FILE_BY_FILE));

    assertThat(bsdiffOnlyDelta).isEqualTo(bsdiffWithFbfDelta);
  }

  @Test
  public void generateDelta_withChangedEmbeddedArchive() throws Exception {
    // We disable this test for java-implementation because it is REALLY SLOW (~300 times slower)
    assumeTrue(useNativeBsDiff);

    // Note here we carefully constructed the archive so that the outer archive and embedded archive
    // (and other embedded archive) do not share entries as that might result in BSDIFF
    // out-performing FBF.
    // This is a design choice that we made since it should be rare in real life to have multiple
    // copies of the same file, one in outer archive and one in embedded archive.
    byte[] oldArchiveBytes =
        UnitTestZipArchive.makeTestZip(
            ImmutableList.of(OLD_ARCHIVE_ENTRY_1, OLD_ENTRY4, OLD_ARCHIVE_ENTRY_2));
    byte[] newArchiveBytes =
        UnitTestZipArchive.makeTestZip(
            ImmutableList.of(NEW_ARCHIVE_ENTRY_1, NEW_ENTRY4, NEW_ARCHIVE_ENTRY_2));

    byte[] bsdiffOnlyDelta =
        generateDelta(oldArchiveBytes, newArchiveBytes, ImmutableSet.of(BSDIFF));
    byte[] bsdiffWithFbfDelta =
        generateDelta(oldArchiveBytes, newArchiveBytes, ImmutableSet.of(BSDIFF, FILE_BY_FILE));

    // The savings in patch size is only seen after compression. The raw patch size might be larger.
    // Here we assert the exact size instead of "X isLessThan Y" just so that we can have a rough
    // estimate of the size savings.
    assertThat(getGzippedSize(bsdiffOnlyDelta)).isEqualTo(1083);
    assertThat(getGzippedSize(bsdiffWithFbfDelta)).isEqualTo(623);
  }

  @Test
  public void generateDelta_withCorruptedEmbeddedArchive_identicalToBsdiffOnly() throws Exception {
    byte[] oldArchiveBytes =
        UnitTestZipArchive.makeTestZip(ImmutableList.of(OLD_ENTRY1, OLD_ARCHIVE_ENTRY_1));
    byte[] newArchiveBytes =
        UnitTestZipArchive.makeTestZip(ImmutableList.of(NEW_ENTRY1, CORRUPTED_ARCHIVE_ENTRY_1));

    byte[] bsdiffOnlyDelta =
        generateDelta(oldArchiveBytes, newArchiveBytes, ImmutableSet.of(BSDIFF));
    byte[] bsdiffWithFbfDelta =
        generateDelta(oldArchiveBytes, newArchiveBytes, ImmutableSet.of(BSDIFF, FILE_BY_FILE));

    assertThat(bsdiffOnlyDelta).isEqualTo(bsdiffWithFbfDelta);
  }

  private byte[] generateDelta(
      byte[] oldArchiveBytes, byte[] newArchiveBytes, Set<DeltaFormat> supportedFormats)
      throws Exception {
    FileByFileDeltaGenerator generator =
        new FileByFileDeltaGenerator(
            /* preDiffPlanEntryModifiers= */ ImmutableList.of(), supportedFormats, useNativeBsDiff);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (TempBlob oldArchive = new TempBlob();
        TempBlob newArchive = new TempBlob()) {
      try (OutputStream oldOutputStream = oldArchive.openBufferedStream();
          OutputStream newOutputStream = newArchive.openBufferedStream()) {
        ByteStreams.copy(new ByteArrayInputStream(oldArchiveBytes), oldOutputStream);
        ByteStreams.copy(new ByteArrayInputStream(newArchiveBytes), newOutputStream);
      }
      generator.generateDelta(oldArchive.asByteSource(), newArchive.asByteSource(), buffer);
    }
    return buffer.toByteArray();
  }

  private static long getGzippedSize(byte[] data) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (GZIPOutputStream out = new GZIPOutputStream(buffer)) {
      out.write(data);
    }
    return buffer.toByteArray().length;
  }
}

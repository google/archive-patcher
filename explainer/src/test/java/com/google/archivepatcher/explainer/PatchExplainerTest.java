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

package com.google.archivepatcher.explainer;

import com.google.archivepatcher.generator.ByteArrayHolder;
import com.google.archivepatcher.generator.DeltaGenerator;
import com.google.archivepatcher.generator.MinimalZipArchive;
import com.google.archivepatcher.generator.MinimalZipEntry;
import com.google.archivepatcher.generator.RecommendationReason;
import com.google.archivepatcher.generator.TotalRecompressionLimiter;
import com.google.archivepatcher.shared.Compressor;
import com.google.archivepatcher.shared.UnitTestZipArchive;
import com.google.archivepatcher.shared.UnitTestZipEntry;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link PatchExplainer}.
 */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class PatchExplainerTest {

  // All the A and B entries consist of a chunk of text followed by a standard corpus of text from
  // the DefaultDeflateCompatibilityDiviner that ensures the tests will be able to discriminate
  // between any compression level. Without this additional corpus text, multiple compression levels
  // can match the entry and the unit tests would not be accurate.
  private static final UnitTestZipEntry ENTRY_A1_LEVEL_6 =
      UnitTestZipArchive.makeUnitTestZipEntry("/path A", 6, "entry A1", null);
  private static final UnitTestZipEntry ENTRY_A1_LEVEL_9 =
      UnitTestZipArchive.makeUnitTestZipEntry("/path A", 9, "entry A1", null);
  private static final UnitTestZipEntry ENTRY_A1_STORED =
      UnitTestZipArchive.makeUnitTestZipEntry("/path A", 0, "entry A", null);
  private static final UnitTestZipEntry ENTRY_A2_LEVEL_9 =
      UnitTestZipArchive.makeUnitTestZipEntry("/path A", 9, "entry A2", null);
  private static final UnitTestZipEntry ENTRY_A2_STORED =
      UnitTestZipArchive.makeUnitTestZipEntry("/path A", 0, "entry A2", null);
  private static final UnitTestZipEntry ENTRY_B_LEVEL_6 =
      UnitTestZipArchive.makeUnitTestZipEntry("/path B", 6, "entry B", null);

  /**
   * A "compressor" that always outputs the same exact string regardless of the input and asserts
   * that the input is exactly as expected.
   */
  private static class FakeCompressor implements Compressor {
    static final String OUTPUT = "fakecompressor output";
    private final byte[] expectedInput;

    public FakeCompressor(byte[] expectedInput) {
      this.expectedInput = expectedInput;
    }

    @Override
    public void compress(InputStream uncompressedIn, OutputStream compressedOut)
        throws IOException {
      byte[] readBuffer = new byte[32768];
      int numRead = 0;
      ByteArrayOutputStream actualInput = new ByteArrayOutputStream();
      while ((numRead = uncompressedIn.read(readBuffer)) >= 0) {
        actualInput.write(readBuffer, 0, numRead);
      }
      Assert.assertArrayEquals(expectedInput, actualInput.toByteArray());
      compressedOut.write(OUTPUT.getBytes("US-ASCII"));
    }
  }

  /**
   * A "delta generator" that always outputs the same exact string regardless of the inputs and
   * asserts that the input is exactly as expected.
   */
  private static class FakeDeltaGenerator implements DeltaGenerator {
    static final String OUTPUT = "fakedeltagenerator output";
    private final byte[] expectedOld;
    private final byte[] expectedNew;

    public FakeDeltaGenerator(byte[] expectedOld, byte[] expectedNew) {
      this.expectedOld = expectedOld;
      this.expectedNew = expectedNew;
    }

    @Override
    public void generateDelta(File oldBlob, File newBlob, OutputStream deltaOut)
        throws IOException {
      assertFileEquals(oldBlob, expectedOld);
      assertFileEquals(newBlob, expectedNew);
      deltaOut.write(OUTPUT.getBytes("US-ASCII"));
    }

    private final void assertFileEquals(File file, byte[] expected) throws IOException {
      byte[] actual = new byte[(int) file.length()];
      try (FileInputStream fileIn = new FileInputStream(file);
          DataInputStream dataIn = new DataInputStream(fileIn)) {
        dataIn.readFully(actual);
      }
      Assert.assertArrayEquals(expected, actual);
    }
  }

  /**
   * Temporary old file.
   */
  private File oldFile = null;

  /**
   * Temporary new file.
   */
  private File newFile = null;

  @Before
  public void setup() throws IOException {
    oldFile = File.createTempFile("patchexplainertest", "old");
    newFile = File.createTempFile("patchexplainertest", "new");
  }

  @After
  public void tearDown() {
    if (oldFile != null) {
      try {
        oldFile.delete();
      } catch (Exception ignored) {
        // Nothing
      }
    }
    if (newFile != null) {
      try {
        newFile.delete();
      } catch (Exception ignored) {
        // Nothing
      }
    }
  }

  @Test
  public void testExplainPatch_CompressedBytesIdentical() throws Exception {
    byte[] bytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A1_LEVEL_6));
    save(bytes, oldFile);
    save(bytes, newFile);
    PatchExplainer explainer = new PatchExplainer(null, null);
    List<EntryExplanation> explanations = explainer.explainPatch(oldFile, newFile);

    EntryExplanation expected =
        new EntryExplanation(
            path(ENTRY_A1_LEVEL_6), false, RecommendationReason.COMPRESSED_BYTES_IDENTICAL, 0);
    checkExplanation(explanations, expected);
  }

  @Test
  public void testExplainPatch_CompressedBytesChanged_UncompressedUnchanged() throws Exception {
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A1_LEVEL_6));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A1_LEVEL_9));
    save(oldBytes, oldFile);
    save(newBytes, newFile);
    PatchExplainer explainer = new PatchExplainer(null, null);
    List<EntryExplanation> explanations = explainer.explainPatch(oldFile, newFile);
    // The compressed bytes changed, but the uncompressed bytes are the same. Thus the patch size
    // should be zero, because the entries are actually identical in the delta-friendly files.
    // Additionally no diffing or compression should be performed.
    EntryExplanation expected =
        new EntryExplanation(
            path(ENTRY_A1_LEVEL_9), false, RecommendationReason.COMPRESSED_BYTES_CHANGED, 0L);
    checkExplanation(explanations, expected);
  }

  @Test
  public void testExplainPatch_CompressedBytesChanged_UncompressedChanged() throws Exception {
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A1_LEVEL_6));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A2_LEVEL_9));
    save(oldBytes, oldFile);
    save(newBytes, newFile);
    FakeDeltaGenerator fakeDeltaGenerator =
        new FakeDeltaGenerator(
            ENTRY_A1_LEVEL_6.getUncompressedBinaryContent(),
            ENTRY_A2_LEVEL_9.getUncompressedBinaryContent());
    FakeCompressor fakeCompressor =
        new FakeCompressor(FakeDeltaGenerator.OUTPUT.getBytes("US-ASCII"));
    PatchExplainer explainer = new PatchExplainer(fakeCompressor, fakeDeltaGenerator);
    List<EntryExplanation> explanations = explainer.explainPatch(oldFile, newFile);
    // The compressed bytes changed, and so did the uncompressed bytes. The patch size should be
    // non-zero because the entries are not identical in the delta-friendly files.
    EntryExplanation expected =
        new EntryExplanation(
            path(ENTRY_A2_LEVEL_9),
            false,
            RecommendationReason.COMPRESSED_BYTES_CHANGED,
            FakeCompressor.OUTPUT.length());
    checkExplanation(explanations, expected);
  }

  @Test
  public void testExplainPatch_CompressedBytesChanged_UncompressedChanged_Limited()
      throws Exception {
    // Just like above, but this time with a TotalRecompressionLimit that changes the result.
    TotalRecompressionLimiter limiter = new TotalRecompressionLimiter(1); // 1 byte limit!
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A1_LEVEL_6));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A2_LEVEL_9));
    save(oldBytes, oldFile);
    save(newBytes, newFile);
    // Note that we will expect a diff based on the COMPRESSED bytes, not the UNCOMPRESSED bytes,
    // because the limiter will force uncompression to be suppressed.
    FakeDeltaGenerator fakeDeltaGenerator =
        new FakeDeltaGenerator(
            ENTRY_A1_LEVEL_6.getCompressedBinaryContent(),
            ENTRY_A2_LEVEL_9.getCompressedBinaryContent());
    FakeCompressor fakeCompressor =
        new FakeCompressor(FakeDeltaGenerator.OUTPUT.getBytes("US-ASCII"));
    PatchExplainer explainer = new PatchExplainer(fakeCompressor, fakeDeltaGenerator);
    List<EntryExplanation> explanations = explainer.explainPatch(oldFile, newFile, limiter);
    // The uncompressed bytes are not the same. The patch plan will want to uncompress the entries,
    // but the limiter will prevent it.
    EntryExplanation expected =
        new EntryExplanation(
            path(ENTRY_A2_LEVEL_9),
            false,
            RecommendationReason.RESOURCE_CONSTRAINED,
            FakeCompressor.OUTPUT.length());
    checkExplanation(explanations, expected);
  }

  @Test
  public void testExplainPatch_BothEntriesUncompressed_BytesUnchanged() throws Exception {
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A1_STORED));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A1_STORED));
    save(oldBytes, oldFile);
    save(newBytes, newFile);
    PatchExplainer explainer = new PatchExplainer(null, null);
    List<EntryExplanation> explanations = explainer.explainPatch(oldFile, newFile);
    // The uncompressed bytes are the same. Thus the patch size should be zero, because the entries
    // are identical in the delta-friendly files. Additionally no diffing or compression should be
    // performed.
    EntryExplanation expected =
        new EntryExplanation(
            path(ENTRY_A1_STORED), false, RecommendationReason.BOTH_ENTRIES_UNCOMPRESSED, 0L);
    checkExplanation(explanations, expected);
  }

  @Test
  public void testExplainPatch_BothEntriesUncompressed_BytesChanged() throws Exception {
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A1_STORED));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A2_STORED));
    save(oldBytes, oldFile);
    save(newBytes, newFile);
    FakeDeltaGenerator fakeDeltaGenerator =
        new FakeDeltaGenerator(
            ENTRY_A1_STORED.getUncompressedBinaryContent(),
            ENTRY_A2_STORED.getUncompressedBinaryContent());
    FakeCompressor fakeCompressor =
        new FakeCompressor(FakeDeltaGenerator.OUTPUT.getBytes("US-ASCII"));
    PatchExplainer explainer = new PatchExplainer(fakeCompressor, fakeDeltaGenerator);
    List<EntryExplanation> explanations = explainer.explainPatch(oldFile, newFile);
    // The uncompressed bytes are not the same. Thus the patch size should be non-zero.
    EntryExplanation expected =
        new EntryExplanation(
            path(ENTRY_A2_STORED),
            false,
            RecommendationReason.BOTH_ENTRIES_UNCOMPRESSED,
            FakeCompressor.OUTPUT.length());
    checkExplanation(explanations, expected);
  }

  @Test
  public void testExplainPatch_CompressedChangedToUncompressed() throws Exception {
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A1_LEVEL_9));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A1_STORED));
    save(oldBytes, oldFile);
    save(newBytes, newFile);
    FakeDeltaGenerator fakeDeltaGenerator =
        new FakeDeltaGenerator(
            ENTRY_A1_LEVEL_9.getUncompressedBinaryContent(),
            ENTRY_A1_STORED.getUncompressedBinaryContent());
    FakeCompressor fakeCompressor =
        new FakeCompressor(FakeDeltaGenerator.OUTPUT.getBytes("US-ASCII"));
    PatchExplainer explainer = new PatchExplainer(fakeCompressor, fakeDeltaGenerator);
    List<EntryExplanation> explanations = explainer.explainPatch(oldFile, newFile);
    EntryExplanation expected =
        new EntryExplanation(
            path(ENTRY_A1_STORED),
            false,
            RecommendationReason.COMPRESSED_CHANGED_TO_UNCOMPRESSED,
            FakeCompressor.OUTPUT.length());
    checkExplanation(explanations, expected);
  }

  @Test
  public void testExplainPatch_UncompressedChangedToCompressed() throws Exception {
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A1_STORED));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A1_LEVEL_6));
    save(oldBytes, oldFile);
    save(newBytes, newFile);
    FakeDeltaGenerator fakeDeltaGenerator =
        new FakeDeltaGenerator(
            ENTRY_A1_STORED.getUncompressedBinaryContent(),
            ENTRY_A1_LEVEL_6.getUncompressedBinaryContent());
    FakeCompressor fakeCompressor =
        new FakeCompressor(FakeDeltaGenerator.OUTPUT.getBytes("US-ASCII"));
    PatchExplainer explainer = new PatchExplainer(fakeCompressor, fakeDeltaGenerator);
    List<EntryExplanation> explanations = explainer.explainPatch(oldFile, newFile);
    EntryExplanation expected =
        new EntryExplanation(
            path(ENTRY_A1_LEVEL_6),
            false,
            RecommendationReason.UNCOMPRESSED_CHANGED_TO_COMPRESSED,
            FakeCompressor.OUTPUT.length());
    checkExplanation(explanations, expected);
  }

  @Test
  public void testExplainPatch_Unsuitable() throws Exception {
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A1_STORED));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A1_LEVEL_6));
    save(oldBytes, oldFile);
    save(newBytes, newFile);

    // Corrupt the data in newFile and re-save. This will make the entry un-divinable.
    MinimalZipEntry newEntry = MinimalZipArchive.listEntries(newFile).get(0);
    newBytes[(int) newEntry.getFileOffsetOfCompressedData()] = (byte) 0xff;
    save(newBytes, newFile);
    byte[] justNewData = new byte[(int) newEntry.getCompressedSize()];
    System.arraycopy(
        newBytes,
        (int) newEntry.getFileOffsetOfCompressedData(),
        justNewData,
        0,
        (int) newEntry.getCompressedSize());

    FakeDeltaGenerator fakeDeltaGenerator =
        new FakeDeltaGenerator(ENTRY_A1_STORED.getUncompressedBinaryContent(), justNewData);
    FakeCompressor fakeCompressor =
        new FakeCompressor(FakeDeltaGenerator.OUTPUT.getBytes("US-ASCII"));
    PatchExplainer explainer = new PatchExplainer(fakeCompressor, fakeDeltaGenerator);
    List<EntryExplanation> explanations = explainer.explainPatch(oldFile, newFile);
    EntryExplanation expected =
        new EntryExplanation(
            path(ENTRY_A1_LEVEL_6),
            false,
            RecommendationReason.DEFLATE_UNSUITABLE,
            FakeCompressor.OUTPUT.length());
    checkExplanation(explanations, expected);
  }

  @Test
  public void testExplainPatch_NewFile() throws Exception {
    byte[] oldBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_A1_LEVEL_6));
    byte[] newBytes = UnitTestZipArchive.makeTestZip(Collections.singletonList(ENTRY_B_LEVEL_6));
    save(oldBytes, oldFile);
    save(newBytes, newFile);
    FakeCompressor fakeCompressor =
        new FakeCompressor(ENTRY_B_LEVEL_6.getCompressedBinaryContent());
    PatchExplainer explainer = new PatchExplainer(fakeCompressor, null);
    List<EntryExplanation> explanations = explainer.explainPatch(oldFile, newFile);
    EntryExplanation expected =
        new EntryExplanation(
            path(ENTRY_B_LEVEL_6),
            true, // isNew
            null, // recommendation reason (null because the file is new)
            FakeCompressor.OUTPUT.length());
    checkExplanation(explanations, expected);
  }

  /**
   * Check that the specified list of explanations has exactly one explanation and that it matches
   * the expected explanation.
   * @param explanations the explanations created by the {@link PatchExplainer}
   * @param expected the expected explanation
   */
  private void checkExplanation(List<EntryExplanation> explanations, EntryExplanation expected) {
    Assert.assertEquals(1, explanations.size());
    EntryExplanation actual = explanations.get(0);
    Assert.assertEquals(expected.getPath(), actual.getPath());
    Assert.assertEquals(expected.isNew(), actual.isNew());
    Assert.assertEquals(expected.getReasonIncludedIfNotNew(), actual.getReasonIncludedIfNotNew());
    Assert.assertEquals(expected.getCompressedSizeInPatch(), actual.getCompressedSizeInPatch());
  }

  /**
   * Convenience method to convert a {@link UnitTestZipEntry}'s path information into a
   * {@link ByteArrayHolder}.
   * @param entry the entry to get the path out of
   * @return the path as a {@link ByteArrayHolder}
   * @throws UnsupportedEncodingException if the system doesn't support US-ASCII. No, seriously.
   */
  private static ByteArrayHolder path(UnitTestZipEntry entry) throws UnsupportedEncodingException {
    return new ByteArrayHolder(entry.path.getBytes("US-ASCII"));
  }

  /**
   * Save the specified data to the specified file.
   * @param data the data to save
   * @param file the file to save to
   * @throws IOException if saving fails
   */
  private static void save(byte[] data, File file) throws IOException {
    try (FileOutputStream out = new FileOutputStream(file)) {
      out.write(data);
      out.flush();
    }
  }
}

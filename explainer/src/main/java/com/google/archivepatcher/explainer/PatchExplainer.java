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
import com.google.archivepatcher.generator.PreDiffExecutor;
import com.google.archivepatcher.generator.PreDiffPlan;
import com.google.archivepatcher.generator.QualifiedRecommendation;
import com.google.archivepatcher.generator.RecommendationModifier;
import com.google.archivepatcher.generator.RecommendationReason;
import com.google.archivepatcher.generator.TempFileHolder;
import com.google.archivepatcher.shared.Compressor;
import com.google.archivepatcher.shared.CountingOutputStream;
import com.google.archivepatcher.shared.DeflateUncompressor;
import com.google.archivepatcher.shared.RandomAccessFileInputStream;
import com.google.archivepatcher.shared.Uncompressor;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Explains where the data in a patch would come from. */
// TODO: Add explicit logic for renames
public class PatchExplainer {
  /**
   * A stream that discards everything written to it.
   */
  private static class NullOutputStream extends OutputStream {
    @Override
    public void write(int b) throws IOException {
      // Nothing.
    }

    @Override
    public void write(byte[] b) throws IOException {
      // Nothing.
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      // Nothing.
    }
  }

  /**
   * The compressor to use for compressing patch content.
   */
  private final Compressor compressor;

  /**
   * The delta generator to use for generating uncompressed patch content.
   */
  private final DeltaGenerator deltaGenerator;

  /**
   * Construct a new patch explainer that will use the specified {@link Compressor} to establish
   * compressed patch size estimates and the specified {@link DeltaGenerator} to generate the deltas
   * for the patch.
   * @param compressor the compressor to use
   * @param deltaGenerator the delta generator to use
   */
  public PatchExplainer(Compressor compressor, DeltaGenerator deltaGenerator) {
    this.compressor = compressor;
    this.deltaGenerator = deltaGenerator;
  }

  /**
   * Explains the patch that would be generated for the specified input files.
   *
   * @param oldFile the old file
   * @param newFile the new file
   * @param recommendationModifiers optionally, {@link RecommendationModifier}s to use during patch
   *     planning. If null, a normal patch is generated.
   * @return a list of the explanations for each entry that would be
   * @throws IOException if unable to read data
   * @throws InterruptedException if any thread interrupts this thread
   */
  public List<EntryExplanation> explainPatch(
      File oldFile, File newFile, RecommendationModifier... recommendationModifiers)
      throws IOException, InterruptedException {
    List<EntryExplanation> result = new ArrayList<>();

    // Isolate entries that are only found in the new archive.
    Map<ByteArrayHolder, MinimalZipEntry> allOldEntries = mapEntries(oldFile);
    Map<ByteArrayHolder, MinimalZipEntry> allNewEntries = mapEntries(newFile);
    Map<ByteArrayHolder, MinimalZipEntry> completelyNewEntries = new HashMap<>(allNewEntries);
    completelyNewEntries.keySet().removeAll(allOldEntries.keySet());

    // Now calculate the costs for the new files and track them in the explanations returned.
    for (Map.Entry<ByteArrayHolder, MinimalZipEntry> entry : completelyNewEntries.entrySet()) {
      long compressedSize = getCompressedSize(newFile, entry.getValue(), compressor);
      result.add(
          new EntryExplanation(
              new ByteArrayHolder(entry.getValue().getFileNameBytes()),
              true,
              null,
              compressedSize));
    }

    Uncompressor uncompressor = new DeflateUncompressor();
    PreDiffExecutor.Builder builder =
        new PreDiffExecutor.Builder().readingOriginalFiles(oldFile, newFile);
    for (RecommendationModifier modifier : recommendationModifiers) {
      builder.withRecommendationModifier(modifier);
    }
    PreDiffExecutor executor = builder.build();
    PreDiffPlan plan = executor.prepareForDiffing();
    try (TempFileHolder oldTemp = new TempFileHolder();
        TempFileHolder newTemp = new TempFileHolder();
        TempFileHolder deltaTemp = new TempFileHolder()) {
      for (QualifiedRecommendation qualifiedRecommendation : plan.getQualifiedRecommendations()) {

        // Short-circuit for identical resources.
        if (qualifiedRecommendation.getReason()
            == RecommendationReason.COMPRESSED_BYTES_IDENTICAL) {
          // Patch size should be effectively zero.
          result.add(
              new EntryExplanation(
                  new ByteArrayHolder(qualifiedRecommendation.getNewEntry().getFileNameBytes()),
                  false,
                  qualifiedRecommendation.getReason(),
                  0L));
          continue;
        }

        if (qualifiedRecommendation.getOldEntry().getCrc32OfUncompressedData()
                == qualifiedRecommendation.getNewEntry().getCrc32OfUncompressedData()
            && qualifiedRecommendation.getOldEntry().getUncompressedSize()
                == qualifiedRecommendation.getNewEntry().getUncompressedSize()) {
          // If the path, size and CRC32 are the same assume it's a match. Patch size should be
          // effectively zero.
          result.add(
              new EntryExplanation(
                  new ByteArrayHolder(qualifiedRecommendation.getNewEntry().getFileNameBytes()),
                  false,
                  qualifiedRecommendation.getReason(),
                  0L));
          continue;
        }

        // Everything past here is a resource that has changed in some way.
        // NB: This magically takes care of RecommendationReason.RESOURCE_CONSTRAINED. The logic
        // below will keep the RESOURCE_CONSTRAINED entries compressed, running the delta on their
        // compressed contents, and the resulting explanation will preserve the RESOURCE_CONSTRAINED
        // reason. This will correctly attribute the size of these blobs to the RESOURCE_CONSTRAINED
        // category.

        // Get the inputs ready for running a delta: uncompress/copy the *old* content as necessary.
        long oldOffset = qualifiedRecommendation.getOldEntry().getFileOffsetOfCompressedData();
        long oldLength = qualifiedRecommendation.getOldEntry().getCompressedSize();
        if (qualifiedRecommendation.getRecommendation().uncompressOldEntry) {
          uncompress(oldFile, oldOffset, oldLength, uncompressor, oldTemp.file);
        } else {
          extractCopy(oldFile, oldOffset, oldLength, oldTemp.file);
        }

        // Get the inputs ready for running a delta: uncompress/copy the *new* content as necessary.
        long newOffset = qualifiedRecommendation.getNewEntry().getFileOffsetOfCompressedData();
        long newLength = qualifiedRecommendation.getNewEntry().getCompressedSize();
        if (qualifiedRecommendation.getRecommendation().uncompressNewEntry) {
          uncompress(newFile, newOffset, newLength, uncompressor, newTemp.file);
        } else {
          extractCopy(newFile, newOffset, newLength, newTemp.file);
        }

        // File is actually changed (or transitioned between compressed and uncompressed forms).
        // Generate and compress a delta.
        try (FileOutputStream deltaOut = new FileOutputStream(deltaTemp.file);
            BufferedOutputStream bufferedDeltaOut = new BufferedOutputStream(deltaOut)) {
          deltaGenerator.generateDelta(oldTemp.file, newTemp.file, bufferedDeltaOut);
          bufferedDeltaOut.flush();
          long compressedDeltaSize =
              getCompressedSize(deltaTemp.file, 0, deltaTemp.file.length(), compressor);
          result.add(
              new EntryExplanation(
                  new ByteArrayHolder(qualifiedRecommendation.getOldEntry().getFileNameBytes()),
                  false,
                  qualifiedRecommendation.getReason(),
                  compressedDeltaSize));
        }
      }
    }

    return result;
  }

  /**
   * Determines the size of the entry if it were compressed with the specified compressor.
   * @param file the file to read from
   * @param entry the entry to estimate the size of
   * @param compressor the compressor to use for compressing
   * @return the size of the entry if compressed with the specified compressor
   * @throws IOException if anything goes wrong
   */
  private long getCompressedSize(File file, MinimalZipEntry entry, Compressor compressor)
      throws IOException {
    return getCompressedSize(
        file, entry.getFileOffsetOfCompressedData(), entry.getCompressedSize(), compressor);
  }

  /**
   * Uncompress the specified content to a new file.
   * @param source the file to read from
   * @param offset the offset at which to start reading
   * @param length the number of bytes to uncompress
   * @param uncompressor the uncompressor to use
   * @param dest the file to write the uncompressed bytes to
   * @throws IOException if anything goes wrong
   */
  private void uncompress(
      File source, long offset, long length, Uncompressor uncompressor, File dest)
      throws IOException {
    try (RandomAccessFileInputStream rafis =
            new RandomAccessFileInputStream(source, offset, length);
        FileOutputStream out = new FileOutputStream(dest);
        BufferedOutputStream bufferedOut = new BufferedOutputStream(out)) {
      uncompressor.uncompress(rafis, bufferedOut);
    }
  }

  /**
   * Extract a copy of the specified content to a new file.
   * @param source the file to read from
   * @param offset the offset at which to start reading
   * @param length the number of bytes to uncompress
   * @param dest the file to write the uncompressed bytes to
   * @throws IOException if anything goes wrong
   */
  private void extractCopy(File source, long offset, long length, File dest) throws IOException {
    try (RandomAccessFileInputStream rafis =
            new RandomAccessFileInputStream(source, offset, length);
        FileOutputStream out = new FileOutputStream(dest);
        BufferedOutputStream bufferedOut = new BufferedOutputStream(out)) {
      byte[] buffer = new byte[32768];
      int numRead = 0;
      while ((numRead = rafis.read(buffer)) >= 0) {
        bufferedOut.write(buffer, 0, numRead);
      }
      bufferedOut.flush();
    }
  }

  /**
   * Compresses an arbitrary range of bytes in the given file and returns the compressed size.
   * @param file the file to read from
   * @param offset the offset in the file to start reading from
   * @param length the number of bytes to read from the input file
   * @param compressor the compressor to use for compressing
   * @return the size of the entry if compressed with the specified compressor
   * @throws IOException if anything goes wrong
   */
  private long getCompressedSize(File file, long offset, long length, Compressor compressor)
      throws IOException {
    try (OutputStream sink = new NullOutputStream();
        CountingOutputStream counter = new CountingOutputStream(sink);
        RandomAccessFileInputStream rafis = new RandomAccessFileInputStream(file, offset, length)) {
      compressor.compress(rafis, counter);
      counter.flush();
      return counter.getNumBytesWritten();
    }
  }

  /**
   * Convert a file into a map whose keys are {@link ByteArrayHolder} objects containing the entry
   * paths and whose values are the corresponding {@link MinimalZipEntry} objects.
   * @param file the file to scan, which must be a valid zip archive
   * @return the mapping, as described
   * @throws IOException if anything goes wrong
   */
  private static Map<ByteArrayHolder, MinimalZipEntry> mapEntries(File file) throws IOException {
    List<MinimalZipEntry> allEntries = MinimalZipArchive.listEntries(file);
    Map<ByteArrayHolder, MinimalZipEntry> result = new HashMap<>(allEntries.size());
    for (MinimalZipEntry entry : allEntries) {
      result.put(new ByteArrayHolder(entry.getFileNameBytes()), entry);
    }
    return result;
  }
}

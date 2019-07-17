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

package com.google.archivepatcher.explainer;

import com.google.archivepatcher.generator.ByteArrayHolder;
import com.google.archivepatcher.generator.DeltaGenerator;
import com.google.archivepatcher.generator.MinimalZipArchive;
import com.google.archivepatcher.generator.MinimalZipEntry;
import com.google.archivepatcher.generator.PreDiffExecutor;
import com.google.archivepatcher.generator.PreDiffPlan;
import com.google.archivepatcher.generator.PreDiffPlanEntry;
import com.google.archivepatcher.generator.PreDiffPlanEntryModifier;
import com.google.archivepatcher.generator.TempBlob;
import com.google.archivepatcher.generator.UncompressionOptionExplanation;
import com.google.archivepatcher.shared.Compressor;
import com.google.archivepatcher.shared.CountingOutputStream;
import com.google.archivepatcher.shared.DeflateUncompressor;
import com.google.archivepatcher.shared.Range;
import com.google.archivepatcher.shared.Uncompressor;
import com.google.archivepatcher.shared.bytesource.ByteSource;
import com.google.archivepatcher.shared.bytesource.ByteStreams;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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

  /** Wrapper around {@link #explainPatch(ByteSource, ByteSource, PreDiffPlanEntryModifier...)}. */
  public List<EntryExplanation> explainPatch(
      File oldFile, File newFile, PreDiffPlanEntryModifier... preDiffPlanEntryModifiers)
      throws IOException, InterruptedException {
    try (ByteSource oldBlob = ByteSource.fromFile(oldFile);
        ByteSource newBlob = ByteSource.fromFile(newFile)) {
      return explainPatch(oldBlob, newBlob, preDiffPlanEntryModifiers);
    }
  }
  /**
   * Explains the patch that would be generated for the specified input files.
   *
   * @param oldFile the old file
   * @param newFile the new file
   * @param preDiffPlanEntryModifiers optionally, {@link PreDiffPlanEntryModifier}s to use during
   *     patch planning. If null, a normal patch is generated.
   * @return a list of the explanations for each entry that would be
   * @throws IOException if unable to read data
   * @throws InterruptedException if any thread interrupts this thread
   */
  public List<EntryExplanation> explainPatch(
      ByteSource oldFile, ByteSource newFile, PreDiffPlanEntryModifier... preDiffPlanEntryModifiers)
      throws IOException, InterruptedException {
    if (preDiffPlanEntryModifiers == null) {
      throw new IllegalArgumentException("preDiffPlanEntryModifiers cannot be null");
    }
    List<EntryExplanation> result = new ArrayList<>();

    // Isolate entries that are only found in the new archive.
    Map<ByteArrayHolder, MinimalZipEntry> allOldEntries = mapEntries(oldFile);
    Map<ByteArrayHolder, MinimalZipEntry> allNewEntries = mapEntries(newFile);
    Map<ByteArrayHolder, MinimalZipEntry> completelyNewEntries = new HashMap<>(allNewEntries);
    completelyNewEntries.keySet().removeAll(allOldEntries.keySet());

    // Now calculate the costs for the new files and track them in the explanations returned.
    for (Entry<ByteArrayHolder, MinimalZipEntry> entry : completelyNewEntries.entrySet()) {
      long compressedSize = getCompressedSize(newFile, entry.getValue(), compressor);
      result.add(
          EntryExplanation.forNew(
              new ByteArrayHolder(entry.getValue().fileNameBytes()), compressedSize));
    }

    Uncompressor uncompressor = new DeflateUncompressor();
    PreDiffExecutor executor =
        new PreDiffExecutor.Builder()
            .readingOriginalFiles(oldFile, newFile)
            .addPreDiffPlanEntryModifiers(Arrays.asList(preDiffPlanEntryModifiers))
            .build();
    PreDiffPlan plan = executor.prepareForDiffing();

    try (TempBlob oldTemp = new TempBlob();
        TempBlob newTemp = new TempBlob();
        TempBlob deltaTemp = new TempBlob()) {
      for (PreDiffPlanEntry preDiffPlanEntry : plan.getPreDiffPlanEntries()) {

        // Short-circuit for identical resources.
        if (preDiffPlanEntry.uncompressionOptionExplanation()
            == UncompressionOptionExplanation.COMPRESSED_BYTES_IDENTICAL) {
          // Patch size should be effectively zero.
          result.add(
              EntryExplanation.forOld(
                  new ByteArrayHolder(preDiffPlanEntry.newEntry().fileNameBytes()),
                  /* compressedSizeInPatch= */ 0L,
                  preDiffPlanEntry.uncompressionOptionExplanation()));
          continue;
        }

        if (preDiffPlanEntry.oldEntry().crc32OfUncompressedData()
                == preDiffPlanEntry.newEntry().crc32OfUncompressedData()
            && preDiffPlanEntry.oldEntry().uncompressedSize()
                == preDiffPlanEntry.newEntry().uncompressedSize()) {
          // If the path, size and CRC32 are the same assume it's a match. Patch size should be
          // effectively zero.
          result.add(
              EntryExplanation.forOld(
                  new ByteArrayHolder(preDiffPlanEntry.newEntry().fileNameBytes()),
                  /* compressedSizeInPatch= */ 0L,
                  preDiffPlanEntry.uncompressionOptionExplanation()));
          continue;
        }

        // Everything past here is a resource that has changed in some way.
        // NB: This magically takes care of UncompressionOptionExplanation.RESOURCE_CONSTRAINED. The
        // logic
        // below will keep the RESOURCE_CONSTRAINED entries compressed, running the delta on their
        // compressed contents, and the resulting explanation will preserve the RESOURCE_CONSTRAINED
        // reason. This will correctly attribute the size of these blobs to the RESOURCE_CONSTRAINED
        // category.

        // Get the inputs ready for running a delta: uncompress/copy the *old* content as necessary.
        if (preDiffPlanEntry.zipEntryUncompressionOption().uncompressOldEntry) {
          uncompress(
              oldFile, preDiffPlanEntry.oldEntry().compressedDataRange(), uncompressor, oldTemp);
        } else {
          oldTemp.clear();
          extractCopy(oldFile, preDiffPlanEntry.oldEntry().compressedDataRange(), oldTemp);
        }

        // Get the inputs ready for running a delta: uncompress/copy the *new* content as necessary.
        if (preDiffPlanEntry.zipEntryUncompressionOption().uncompressNewEntry) {
          uncompress(
              newFile, preDiffPlanEntry.newEntry().compressedDataRange(), uncompressor, newTemp);
        } else {
          newTemp.clear();
          extractCopy(newFile, preDiffPlanEntry.newEntry().compressedDataRange(), newTemp);
        }

        // File is actually changed (or transitioned between compressed and uncompressed forms).
        // Generate and compress a delta.
        try (OutputStream bufferedDeltaOut = deltaTemp.openBufferedStream();
            ByteSource oldTempSource = oldTemp.asByteSource();
            ByteSource newTempSource = newTemp.asByteSource()) {
          deltaGenerator.generateDelta(oldTempSource, newTempSource, bufferedDeltaOut);
        }
        try (ByteSource deltaTempSource = deltaTemp.asByteSource()) {
          long compressedDeltaSize =
              getCompressedSize(deltaTempSource, 0, deltaTempSource.length(), compressor);
          result.add(
              EntryExplanation.forOld(
                  new ByteArrayHolder(preDiffPlanEntry.oldEntry().fileNameBytes()),
                  compressedDeltaSize,
                  preDiffPlanEntry.uncompressionOptionExplanation()));
        }
      }
    }

    return result;
  }

  /**
   * Determines the size of the entry if it were compressed with the specified compressor.
   *
   * @param file the file to read from
   * @param entry the entry to estimate the size of
   * @param compressor the compressor to use for compressing
   * @return the size of the entry if compressed with the specified compressor
   * @throws IOException if anything goes wrong
   */
  private static long getCompressedSize(
      ByteSource file, MinimalZipEntry entry, Compressor compressor) throws IOException {
    return getCompressedSize(
        file,
        entry.compressedDataRange().offset(),
        entry.compressedDataRange().length(),
        compressor);
  }

  /**
   * Compresses an arbitrary range of bytes from the given blob and returns the compressed size.
   *
   * @param byteSource the byte source to read from
   * @param offset the offset in the file to start reading from
   * @param length the number of bytes to read from the input file
   * @param compressor the compressor to use for compressing
   * @return the size of the entry if compressed with the specified compressor
   * @throws IOException if anything goes wrong
   */
  private static long getCompressedSize(
      ByteSource byteSource, long offset, long length, Compressor compressor) throws IOException {
    try (OutputStream sink = new NullOutputStream();
        CountingOutputStream counter = new CountingOutputStream(sink);
        InputStream rafis = byteSource.slice(offset, length).openStream()) {
      compressor.compress(rafis, counter);
      counter.flush();
      return counter.getNumBytesWritten();
    }
  }

  /**
   * Uncompress the specified content to a new file.
   *
   * @param source the file to read from
   * @param rangeToUncompress
   * @param uncompressor the uncompressor to use
   * @param dest the file to write the uncompressed bytes to
   * @throws IOException if anything goes wrong
   */
  private static void uncompress(
      ByteSource source, Range rangeToUncompress, Uncompressor uncompressor, TempBlob dest)
      throws IOException {
    try (InputStream in = source.slice(rangeToUncompress).openStream();
        OutputStream out = dest.openBufferedStream()) {
      uncompressor.uncompress(in, out);
    }
  }

  /**
   * Extract a copy of the specified content to a new file.
   *
   * @param source the file to read from
   * @param dest the file to write the uncompressed bytes to
   * @throws IOException if anything goes wrong
   */
  private static void extractCopy(ByteSource source, Range rangeToExtract, TempBlob dest)
      throws IOException {
    try (InputStream in = source.slice(rangeToExtract).openStream();
        OutputStream out = dest.openBufferedStream()) {
      ByteStreams.copy(in, out);
    }
  }

  /**
   * Convert a file into a map whose keys are {@link ByteArrayHolder} objects containing the entry
   * paths and whose values are the corresponding {@link MinimalZipEntry} objects.
   *
   * @param file the file to scan, which must be a valid zip archive
   * @return the mapping, as described
   * @throws IOException if anything goes wrong
   */
  private static Map<ByteArrayHolder, MinimalZipEntry> mapEntries(ByteSource file)
      throws IOException {
    List<MinimalZipEntry> allEntries = MinimalZipArchive.listEntries(file);
    Map<ByteArrayHolder, MinimalZipEntry> result = new HashMap<>(allEntries.size());
    for (MinimalZipEntry entry : allEntries) {
      result.put(new ByteArrayHolder(entry.fileNameBytes()), entry);
    }
    return result;
  }
}

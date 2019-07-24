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

import static com.google.archivepatcher.generator.DeltaEntries.combineEntries;
import static com.google.archivepatcher.generator.DeltaEntries.fillGaps;
import static com.google.archivepatcher.shared.PatchConstants.USE_NATIVE_BSDIFF_BY_DEFAULT;

import com.google.archivepatcher.shared.PatchConstants.DeltaFormat;
import com.google.archivepatcher.shared.Range;
import com.google.archivepatcher.shared.bytesource.ByteSource;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Generates file-by-file patches. */
public class FileByFileDeltaGenerator extends DeltaGenerator {

  /** The default delta format to use when all other delta formats are not applicable. */
  public static final DeltaFormat DEFAULT_DELTA_FORMAT = DeltaFormat.BSDIFF;

  /** Modifiers for planning and patch generation. */
  private final List<PreDiffPlanEntryModifier> preDiffPlanEntryModifiers;

  /**
   * List of supported delta formats. For more info, see the "delta descriptor record" section of
   * the patch format spec.
   */
  private final Set<DeltaFormat> supportedDeltaFormats;

  private final DeltaGeneratorFactory deltaGeneratorFactory;

  /**
   * Constructs a new generator for File-by-File patches, using the specified configuration.
   *
   * @param preDiffPlanEntryModifiers optionally, {@link PreDiffPlanEntryModifier}s to use for
   *     modifying the planning phase of patch generation. These can be used to, e.g., limit the
   *     total amount of recompression that a patch applier needs to do. Modifiers are applied in
   *     the order they are specified.
   * @param supportedDeltaFormats the set of supported delta formats to use in the patch
   */
  public FileByFileDeltaGenerator(
      List<PreDiffPlanEntryModifier> preDiffPlanEntryModifiers,
      Set<DeltaFormat> supportedDeltaFormats) {
    this(preDiffPlanEntryModifiers, supportedDeltaFormats, USE_NATIVE_BSDIFF_BY_DEFAULT);
  }

  /**
   * Constructs a new generator for File-by-File patches, using the specified configuration.
   *
   * @param preDiffPlanEntryModifiers optionally, {@link PreDiffPlanEntryModifier}s to use for
   *     modifying the planning phase of patch generation. These can be used to, e.g., limit the
   *     total amount of recompression that a patch applier needs to do. Modifiers are applied in
   *     the order they are specified.
   * @param supportedDeltaFormats the set of supported delta formats to use in the patch
   * @param useNativeBsDiff whether to use the native implementation of BSDIFF internally
   */
  public FileByFileDeltaGenerator(
      List<PreDiffPlanEntryModifier> preDiffPlanEntryModifiers,
      Set<DeltaFormat> supportedDeltaFormats,
      boolean useNativeBsDiff) {
    this.preDiffPlanEntryModifiers = getImmutableListCopy(preDiffPlanEntryModifiers);
    this.supportedDeltaFormats = getImmutableSetCopy(supportedDeltaFormats);
    this.deltaGeneratorFactory = new DeltaGeneratorFactory(useNativeBsDiff);
  }

  /**
   * Generate a V1 patch for the specified input files and write the patch to the specified {@link
   * OutputStream}. The written patch is <em>raw</em>, i.e. it has not been compressed. Compression
   * should almost always be applied to the patch, either right in the specified {@link
   * OutputStream} or in a post-processing step, prior to transmitting the patch to the patch
   * applier
   *
   * @param oldBlob the original old file to read (will not be modified)
   * @param newBlob the original new file to read (will not be modified)
   * @param patchOut the stream to write the patch to
   * @throws IOException if unable to complete the operation due to an I/O error
   * @throws InterruptedException if any thread has interrupted the current thread
   */
  @Override
  public void generateDelta(ByteSource oldBlob, ByteSource newBlob, OutputStream patchOut)
      throws IOException, InterruptedException {
    try (TempBlob deltaFriendlyOldFile = new TempBlob();
        TempBlob deltaFriendlyNewFile = new TempBlob()) {
      PreDiffPlan preDiffPlan =
          generatePreDiffPlanAndPrepareBlobs(
              oldBlob, newBlob, deltaFriendlyOldFile, deltaFriendlyNewFile, supportedDeltaFormats);

      try (ByteSource deltaFriendlyOldBlob = deltaFriendlyOldFile.asByteSource();
          ByteSource deltaFriendlyNewBlob = deltaFriendlyNewFile.asByteSource()) {
        List<DeltaEntry> deltaEntries =
            getDeltaEntries(
                preDiffPlan.getPreDiffPlanEntries(), deltaFriendlyOldBlob, deltaFriendlyNewBlob);
        PatchWriter patchWriter =
            new PatchWriter(
                preDiffPlan,
                deltaFriendlyOldFile.length(),
                deltaEntries,
                deltaFriendlyOldBlob,
                deltaFriendlyNewBlob,
                deltaGeneratorFactory);
        patchWriter.writePatch(patchOut);
      }
    }
  }

  /**
   * Generate a V1 patch pre diffing plan.
   *
   * @param oldBlob the original old blob to read (will not be modified)
   * @param newBlob the original new blob to read (will not be modified)
   * @throws IOException if unable to complete the operation due to an I/O error
   */
  public PreDiffPlan generatePreDiffPlanAndPrepareBlobs(ByteSource oldBlob, ByteSource newBlob)
      throws IOException {
    try (TempBlob deltaFriendlyOldFile = new TempBlob();
        TempBlob deltaFriendlyNewFile = new TempBlob()) {
      return generatePreDiffPlanAndPrepareBlobs(
          oldBlob, newBlob, deltaFriendlyOldFile, deltaFriendlyNewFile, supportedDeltaFormats);
    }
  }

  private PreDiffPlan generatePreDiffPlanAndPrepareBlobs(
      ByteSource oldFile,
      ByteSource newFile,
      TempBlob deltaFriendlyOldFile,
      TempBlob deltaFriendlyNewFile,
      Set<DeltaFormat> supportedDeltaFormats)
      throws IOException {
    PreDiffExecutor executor =
        new PreDiffExecutor.Builder()
            .readingOriginalFiles(oldFile, newFile)
            .writingDeltaFriendlyFiles(deltaFriendlyOldFile, deltaFriendlyNewFile)
            .addPreDiffPlanEntryModifiers(preDiffPlanEntryModifiers)
            .addSupportedDeltaFormats(supportedDeltaFormats)
            .build();

    return executor.prepareForDiffing();
  }

  private List<DeltaEntry> getDeltaEntries(
      List<PreDiffPlanEntry> entries, ByteSource oldBlob, ByteSource newBlob) {

    // First we convert them into DiffPlanEntries to include the ranges within the delta-friendly
    // blobs that actually needs to be passed to the delta generator.
    List<DiffPlanEntry> diffPlanEntries = getDiffPlanEntries(entries);

    // Next we convert the existing DiffPlanEntries into DeltaEntries.
    // Note that since a PreDiffPlanEntry is only constructed if we can find a "pair" of entries in
    // the old and new zip archive, the ranges will not "cover" the entire archive.
    List<DeltaEntry> rawEntries = new ArrayList<>(diffPlanEntries.size());
    for (DiffPlanEntry diffPlanEntry : diffPlanEntries) {
      rawEntries.add(diffPlanEntry.asDeltaEntry());
    }

    // Generate another list of DeltaEntry such that the entire new delta-friendly blob is covered
    // by all the newBlobRanges.
    List<DeltaEntry> entriesWithoutGaps = fillGaps(rawEntries, oldBlob, newBlob);

    // Combine entries where possible.
    List<DeltaEntry> combinedEntries = combineEntries(entriesWithoutGaps, oldBlob);

    return combinedEntries;
  }

  /**
   * Generates {@link DiffPlanEntry}s from {@link PreDiffPlanEntry}s.
   *
   * <p>This method mostly includes working out the range each {@link PreDiffPlanEntry} corresponds
   * to in old and new delta friendly blobs.
   */
  private static List<DiffPlanEntry> getDiffPlanEntries(List<PreDiffPlanEntry> entries) {
    // Local entries in zip file are arranged in the following way.
    // [ metadata ] [ compressed data ] [ other metadata ]
    // In transformation to delta friendly blob, the only thing we did is uncompress the
    // "compressed data" section (if the decision is to uncompress). Hence all we need to do is to
    // keep track of how many extra bytes were written into the delta friendly file from entries
    // before.

    // Make a copy so we can sort it.
    entries = new ArrayList<>(entries);

    Map<PreDiffPlanEntry, Long> offsetsInOldBlob = new HashMap<>();

    // Go through each entry in the order of old blob range offset and compute its range in the
    // delta-friendly blob by accounting for the total extra bytes resulting from uncompressing.
    Collections.sort(entries, PreDiffPlanEntry.OLD_BLOB_OFFSET_COMPARATOR);
    long extraBytesFromPreviousEntries = 0;
    for (PreDiffPlanEntry entry : entries) {
      MinimalZipEntry zipEntry = entry.oldEntry();
      offsetsInOldBlob.put(
          entry, zipEntry.localEntryRange().offset() + extraBytesFromPreviousEntries);

      long extraBytesFromThisEntry = 0;
      if (entry.zipEntryUncompressionOption().uncompressOldEntry) {
        extraBytesFromThisEntry =
            zipEntry.uncompressedSize() - zipEntry.compressedDataRange().length();
      }

      extraBytesFromPreviousEntries += extraBytesFromThisEntry;
    }

    // Now we repeat the same thing for the new blob
    Map<PreDiffPlanEntry, Long> offsetsInNewBlob = new HashMap<>();
    Collections.sort(entries, PreDiffPlanEntry.NEW_BLOB_OFFSET_COMPARATOR);
    extraBytesFromPreviousEntries = 0;
    for (PreDiffPlanEntry entry : entries) {
      MinimalZipEntry zipEntry = entry.newEntry();
      offsetsInNewBlob.put(
          entry, zipEntry.localEntryRange().offset() + extraBytesFromPreviousEntries);

      long extraBytesFromThisEntry = 0;
      if (entry.zipEntryUncompressionOption().uncompressNewEntry) {
        extraBytesFromThisEntry =
            zipEntry.uncompressedSize() - zipEntry.compressedDataRange().length();
      }

      extraBytesFromPreviousEntries += extraBytesFromThisEntry;
    }

    List<DiffPlanEntry> diffPlanEntries = new ArrayList<>(entries.size());
    for (PreDiffPlanEntry entry : entries) {
      // Here we transform the "local header entry range" into the uncompressed data range since
      // the input to the delta generator should be raw bytes not including metadata. The "gaps"
      // created this way will be handled by the gap filling processing later.
      // Note that although this produces the same output, it might result in more invocations of
      // BSDIFF since we need to invoke them separately for the metadata. But it does allow us to
      // use vanilla FileByFileDeltaGenerator directly without any complex customisation.
      long oldUncompressedDataOffset =
          offsetsInOldBlob.get(entry)
              + (entry.oldEntry().compressedDataRange().offset()
                  - entry.oldEntry().localEntryRange().offset());
      long oldUncompressedDataLength =
          entry.zipEntryUncompressionOption().uncompressOldEntry
              ? entry.oldEntry().uncompressedSize()
              : entry.oldEntry().compressedDataRange().length();
      long newUncompressedDataOffset =
          offsetsInNewBlob.get(entry)
              + (entry.newEntry().compressedDataRange().offset()
                  - entry.newEntry().localEntryRange().offset());
      long newUncompressedDataLength =
          entry.zipEntryUncompressionOption().uncompressNewEntry
              ? entry.newEntry().uncompressedSize()
              : entry.newEntry().compressedDataRange().length();
      diffPlanEntries.add(
          DiffPlanEntry.create(
              entry,
              Range.of(oldUncompressedDataOffset, oldUncompressedDataLength),
              Range.of(newUncompressedDataOffset, newUncompressedDataLength)));
    }
    return diffPlanEntries;
  }

  private static <T> List<T> getImmutableListCopy(List<T> input) {
    if (input != null) {
      return Collections.unmodifiableList(new ArrayList<>(input));
    } else {
      return Collections.emptyList();
    }
  }

  private static <T> Set<T> getImmutableSetCopy(Set<T> input) {
    if (input != null) {
      return Collections.unmodifiableSet(new HashSet<>(input));
    } else {
      return Collections.emptySet();
    }
  }

}

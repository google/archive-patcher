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

import static com.google.archivepatcher.generator.DeltaFormatExplanation.DEFAULT;
import static com.google.archivepatcher.generator.UncompressionOptionExplanation.BOTH_ENTRIES_UNCOMPRESSED;
import static com.google.archivepatcher.generator.UncompressionOptionExplanation.COMPRESSED_BYTES_CHANGED;
import static com.google.archivepatcher.generator.UncompressionOptionExplanation.COMPRESSED_BYTES_IDENTICAL;
import static com.google.archivepatcher.generator.UncompressionOptionExplanation.COMPRESSED_CHANGED_TO_UNCOMPRESSED;
import static com.google.archivepatcher.generator.UncompressionOptionExplanation.DEFLATE_UNSUITABLE;
import static com.google.archivepatcher.generator.UncompressionOptionExplanation.UNCOMPRESSED_CHANGED_TO_COMPRESSED;
import static com.google.archivepatcher.generator.UncompressionOptionExplanation.UNSUITABLE;
import static com.google.archivepatcher.generator.ZipEntryUncompressionOption.UNCOMPRESS_BOTH;
import static com.google.archivepatcher.generator.ZipEntryUncompressionOption.UNCOMPRESS_NEITHER;
import static com.google.archivepatcher.generator.ZipEntryUncompressionOption.UNCOMPRESS_NEW;
import static com.google.archivepatcher.generator.ZipEntryUncompressionOption.UNCOMPRESS_OLD;
import static com.google.archivepatcher.shared.PatchConstants.DeltaFormat.BSDIFF;

import com.google.archivepatcher.generator.similarity.Crc32SimilarityFinder;
import com.google.archivepatcher.generator.similarity.SimilarityFinder;
import com.google.archivepatcher.shared.JreDeflateParameters;
import com.google.archivepatcher.shared.PatchConstants.DeltaFormat;
import com.google.archivepatcher.shared.TypedRange;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plans archive transformations to be made prior to differencing.
 */
class PreDiffPlanner {

  /**
   * The old archive.
   */
  private final File oldFile;

  /**
   * The new archive.
   */
  private final File newFile;

  /**
   * The entries in the old archive, with paths as keys.
   */
  private final Map<ByteArrayHolder, MinimalZipEntry> oldArchiveZipEntriesByPath;

  /**
   * The entries in the new archive, with paths as keys.
   */
  private final Map<ByteArrayHolder, MinimalZipEntry> newArchiveZipEntriesByPath;

  /**
   * The divined parameters for compression of the entries in the new archive, with paths as keys.
   */
  private final Map<ByteArrayHolder, JreDeflateParameters> newArchiveJreDeflateParametersByPath;

  /**
   * Optional {@link PreDiffPlanEntryModifier}s that will be applied after the default {@link
   * PreDiffPlanEntry}s have been made but before the {@link PreDiffPlan} is constructed.
   */
  private final List<PreDiffPlanEntryModifier> preDiffPlanEntryModifiers;

  /**
   * Constructs a new planner that will work on the specified inputs
   *
   * @param oldFile the old file, used to compare bytes between old and new entries as necessary
   * @param oldArchiveZipEntriesByPath the entries in the old archive, with paths as keys
   * @param newFile the new file, used to compare bytes between old and new entries as necessary
   * @param newArchiveZipEntriesByPath the entries in the new archive, with paths as keys
   * @param newArchiveJreDeflateParametersByPath the {@link JreDeflateParameters} for each entry in
   *     the new archive, with paths as keys
   * @param preDiffPlanEntryModifiers optionally, {@link PreDiffPlanEntryModifier}s to be applied
   *     after the default {@link PreDiffPlanEntry}s have been made but before the {@link
   *     PreDiffPlan} is generated in {@link #generatePreDiffPlan()}.
   */
  PreDiffPlanner(
      File oldFile,
      Map<ByteArrayHolder, MinimalZipEntry> oldArchiveZipEntriesByPath,
      File newFile,
      Map<ByteArrayHolder, MinimalZipEntry> newArchiveZipEntriesByPath,
      Map<ByteArrayHolder, JreDeflateParameters> newArchiveJreDeflateParametersByPath,
      List<PreDiffPlanEntryModifier> preDiffPlanEntryModifiers,
      Set<DeltaFormat> supportedDeltaFormats) {
    this.oldFile = oldFile;
    this.oldArchiveZipEntriesByPath = oldArchiveZipEntriesByPath;
    this.newFile = newFile;
    this.newArchiveZipEntriesByPath = newArchiveZipEntriesByPath;
    this.newArchiveJreDeflateParametersByPath = newArchiveJreDeflateParametersByPath;
    this.preDiffPlanEntryModifiers = preDiffPlanEntryModifiers;
  }

  /**
   * Generates and returns the plan for archive transformations to be made prior to differencing.
   * The resulting {@link PreDiffPlan} has the old and new file uncompression plans set. The
   * delta-friendly new file recompression plan is <em>not</em> set at this time.
   * @return the plan
   * @throws IOException if there are any problems reading the input files
   */
  PreDiffPlan generatePreDiffPlan() throws IOException {
    List<PreDiffPlanEntry> defaultEntries = getDefaultPreDiffPlanEntries();
    for (PreDiffPlanEntryModifier modifier : preDiffPlanEntryModifiers) {
      // Allow changing the entries base on arbitrary criteria.
      defaultEntries = modifier.getModifiedPreDiffPlanEntries(oldFile, newFile, defaultEntries);
    }

    // Process entries to extract ranges for decompression & recompression
    Set<TypedRange<Void>> oldFilePlan = new HashSet<>();
    Set<TypedRange<JreDeflateParameters>> newFilePlan = new HashSet<>();
    for (PreDiffPlanEntry entry : defaultEntries) {
      if (entry.getZipEntryUncompressionOption().uncompressOldEntry) {
        long offset = entry.getOldEntry().getFileOffsetOfCompressedData();
        long length = entry.getOldEntry().getCompressedSize();
        TypedRange<Void> range = new TypedRange<Void>(offset, length, null);
        oldFilePlan.add(range);
      }
      if (entry.getZipEntryUncompressionOption().uncompressNewEntry) {
        long offset = entry.getNewEntry().getFileOffsetOfCompressedData();
        long length = entry.getNewEntry().getCompressedSize();
        JreDeflateParameters newJreDeflateParameters =
            newArchiveJreDeflateParametersByPath.get(
                new ByteArrayHolder(entry.getNewEntry().getFileNameBytes()));
        TypedRange<JreDeflateParameters> range =
            new TypedRange<JreDeflateParameters>(offset, length, newJreDeflateParameters);
        newFilePlan.add(range);
      }
    }

    List<TypedRange<Void>> oldFilePlanList = new ArrayList<>(oldFilePlan);
    Collections.sort(oldFilePlanList);
    List<TypedRange<JreDeflateParameters>> newFilePlanList = new ArrayList<>(newFilePlan);
    Collections.sort(newFilePlanList);
    return new PreDiffPlan(
        Collections.unmodifiableList(defaultEntries),
        Collections.unmodifiableList(oldFilePlanList),
        Collections.unmodifiableList(newFilePlanList));
  }

  /**
   * Analyzes the input files and returns the default {@link PreDiffPlanEntry} for each entry in the
   * new archive.
   *
   * @return the {@link PreDiffPlanEntry}s
   * @throws IOException if anything goes wrong
   */
  private List<PreDiffPlanEntry> getDefaultPreDiffPlanEntries() throws IOException {
    List<PreDiffPlanEntry> entries = new ArrayList<>();

    // This will be used to find files that have been renamed, but not modified. This is relatively
    // cheap to construct as it just requires indexing all entries by the uncompressed CRC32, and
    // the CRC32 is already available in the ZIP headers.
    SimilarityFinder trivialRenameFinder =
        new Crc32SimilarityFinder(oldFile, oldArchiveZipEntriesByPath.values());

    // Iterate over every pair of entries and get a PreDiffPlanEntry
    for (Map.Entry<ByteArrayHolder, MinimalZipEntry> newEntry :
        newArchiveZipEntriesByPath.entrySet()) {
      ByteArrayHolder newEntryPath = newEntry.getKey();
      MinimalZipEntry oldZipEntry = oldArchiveZipEntriesByPath.get(newEntryPath);
      if (oldZipEntry == null) {
        // The path is only present in the new archive, not in the old archive. Try to find a
        // similar file in the old archive that can serve as a diff base for the new file.
        List<MinimalZipEntry> identicalEntriesInOldArchive =
            trivialRenameFinder.findSimilarFiles(newFile, newEntry.getValue());
        if (!identicalEntriesInOldArchive.isEmpty()) {
          // An identical file exists in the old archive at a different path. Use it for the
          // PreDiffPlanEntry and carry on with the normal logic.
          // All entries in the returned list are identical, so just pick the first one.
          // NB, in principle it would be optimal to select the file that required the least work
          // to apply the patch - in practice, it is unlikely that an archive will contain multiple
          // copies of the same file that are compressed differently, so don't bother with that
          // degenerate case.
          oldZipEntry = identicalEntriesInOldArchive.get(0);
        }
      }

      // If the attempt to find a suitable diff base for the new entry has failed, oldZipEntry is
      // null (nothing to do in that case). Otherwise, there is an old entry that is relevant, so
      // get a PreDiffPlanEntry for what to do.
      if (oldZipEntry != null) {
        entries.add(getPreDiffPlanEntry(oldZipEntry, newEntry.getValue()));
      }
    }
    return entries;
  }

  /**
   * Determines the right {@link PreDiffPlanEntry} for handling the (oldEntry, newEntry) tuple.
   *
   * @param oldEntry the entry in the old archive
   * @param newEntry the entry in the new archive
   * @return the {@link PreDiffPlanEntry}
   * @throws IOException if there are any problems reading the input files
   */
  private PreDiffPlanEntry getPreDiffPlanEntry(MinimalZipEntry oldEntry, MinimalZipEntry newEntry)
      throws IOException {

    PreDiffPlanEntry.Builder builder = PreDiffPlanEntry.builder().setZipEntries(oldEntry, newEntry);

    setUncompressionOption(builder, oldEntry, newEntry);

    setDeltaFormat(builder);

    return builder.build();
  }

  private void setUncompressionOption(
      PreDiffPlanEntry.Builder builder, MinimalZipEntry oldEntry, MinimalZipEntry newEntry)
      throws IOException {
    // Below we try to find the suitable uncompression settings. It generally follows this logic:
    // 1. If either old and new are unsuitable for uncompression, we leave them untouched.
    // 2. If both are uncompressed, we have nothing to do.
    // 3. Now at least one is compressed. If there is change, we uncompress accordingly.

    // 1. If either old and new are unsuitable for uncompression, we leave them untouched.
    // Reason singled out in order to monitor unsupported versions of zlib.
    if (unsuitableDeflate(newEntry)) {
      builder.setUncompressionOption(UNCOMPRESS_NEITHER, DEFLATE_UNSUITABLE);
    } else if (unsuitable(oldEntry, newEntry)) {
      builder.setUncompressionOption(UNCOMPRESS_NEITHER, UNSUITABLE);
    }

    // 2. If both are uncompressed, we have nothing to do.
    else if (bothEntriesUncompressed(oldEntry, newEntry)) {
      builder.setUncompressionOption(UNCOMPRESS_NEITHER, BOTH_ENTRIES_UNCOMPRESSED);
    }

    // 3. Now at least one is compressed. If there is change, we uncompress accordingly.
    else if (uncompressedChangedToCompressed(oldEntry, newEntry)) {
      builder.setUncompressionOption(UNCOMPRESS_NEW, UNCOMPRESSED_CHANGED_TO_COMPRESSED);
    } else if (compressedChangedToUncompressed(oldEntry, newEntry)) {
      builder.setUncompressionOption(UNCOMPRESS_OLD, COMPRESSED_CHANGED_TO_UNCOMPRESSED);
    } else if (compressedBytesIdentical(oldEntry, newEntry)) {
      builder.setUncompressionOption(UNCOMPRESS_NEITHER, COMPRESSED_BYTES_IDENTICAL);
    } else {
      // Compressed bytes not identical.
      builder.setUncompressionOption(UNCOMPRESS_BOTH, COMPRESSED_BYTES_CHANGED);
    }
  }

  private void setDeltaFormat(PreDiffPlanEntry.Builder builder) {
    builder.setDeltaFormat(BSDIFF, DEFAULT);
  }

  /**
   * Returns true if the entries are unsuitable for doing an uncompressed diff. This method returns
   * true if either of the entries is compressed in an unsupported way (a non-deflate compression
   * algorithm).
   *
   * @param oldEntry the entry in the old archive
   * @param newEntry the entry in the new archive
   * @return true if unsuitable
   */
  private boolean unsuitable(MinimalZipEntry oldEntry, MinimalZipEntry newEntry) {
    if (oldEntry.getCompressionMethod() != 0 && !oldEntry.isDeflateCompressed()) {
      // The old entry is compressed in a way that is not supported. It cannot be uncompressed, so
      // no uncompressed diff is possible; leave both old and new alone.
      return true;
    }
    if (newEntry.getCompressionMethod() != 0 && !newEntry.isDeflateCompressed()) {
      // The new entry is compressed in a way that is not supported. Same result as above.
      return true;
    }
    return false;
  }

  /**
   * Returns true if the entries are unsuitable for doing an uncompressed diff as a result of the
   * new entry being compressed via deflate, with undivinable parameters. This could be the result
   * of an unsupported version of zlib being used.
   *
   * @param newEntry the entry in the new archive
   * @return true if unsuitable
   */
  private boolean unsuitableDeflate(MinimalZipEntry newEntry) {
    JreDeflateParameters newJreDeflateParameters =
        newArchiveJreDeflateParametersByPath.get(new ByteArrayHolder(newEntry.getFileNameBytes()));
    if (newEntry.isDeflateCompressed() && newJreDeflateParameters == null) {
      // The new entry is compressed via deflate, but the parameters were undivinable. Therefore the
      // new entry cannot be recompressed, so leave both old and new alone.
      return true;
    }

    return false;
  }

  /**
   * Returns true if the entries are already optimal for doing an uncompressed diff. This method
   * returns true if both of the entries are already uncompressed, i.e. are already in the best form
   * for diffing.
   * @param oldEntry the entry in the old archive
   * @param newEntry the entry in the new archive
   * @return as described
   */
  private boolean bothEntriesUncompressed(MinimalZipEntry oldEntry, MinimalZipEntry newEntry) {
    return oldEntry.getCompressionMethod() == 0 && newEntry.getCompressionMethod() == 0;
  }

  /**
   * Returns true if the entry is uncompressed in the old archive and compressed in the new archive.
   * This method does not check whether or not the compression is reproducible. It is assumed that
   * any compressed entries encountered are reproducibly compressed.
   * @param oldEntry the entry in the old archive
   * @param newEntry the entry in the new archive
   * @return as described
   */
  private boolean uncompressedChangedToCompressed(
      MinimalZipEntry oldEntry, MinimalZipEntry newEntry) {
    return oldEntry.getCompressionMethod() == 0 && newEntry.getCompressionMethod() != 0;
  }

  /**
   * Returns true if the entry is compressed in the old archive and uncompressed in the new archive.
   * This method does not check whether or not the compression is reproducible because that
   * information is irrelevant to this decision (it does not matter whether the compression in the
   * old archive is reproducible or not, because that data does not need to be recompressed at patch
   * apply time).
   * @param oldEntry the entry in the old archive
   * @param newEntry the entry in the new archive
   * @return as described
   */
  private boolean compressedChangedToUncompressed(
      MinimalZipEntry oldEntry, MinimalZipEntry newEntry) {
    return newEntry.getCompressionMethod() == 0 && oldEntry.getCompressionMethod() != 0;
  }

  /**
   * Checks if the compressed bytes in the specified entries are identical. No attempt is made to
   * inflate, this method just examines the raw bytes that represent the content in the specified
   * entries and returns true if they are identical.
   *
   * @param oldEntry the entry in the old archive
   * @param newEntry the entry in the new archive
   * @return true as described above
   * @throws IOException if unable to read
   */
  private boolean compressedBytesIdentical(MinimalZipEntry oldEntry, MinimalZipEntry newEntry)
      throws IOException {
    if (oldEntry.getCompressedSize() != newEntry.getCompressedSize()) {
      // Length is not the same, so content cannot match.
      return false;
    }
    try (FileInputStream oldFileInputStream = new FileInputStream(oldFile);
        BufferedInputStream oldFileBufferedInputStream =
            new BufferedInputStream(oldFileInputStream);
        FileInputStream newFileInputStream = new FileInputStream(newFile);
        BufferedInputStream newFileBufferedInputStream =
            new BufferedInputStream(newFileInputStream)) {
      oldFileBufferedInputStream.skip(oldEntry.getFileOffsetOfCompressedData());
      newFileBufferedInputStream.skip(newEntry.getFileOffsetOfCompressedData());

      for (int i = 0; i < oldEntry.getCompressedSize(); ++i) {
        if (oldFileBufferedInputStream.read() != newFileBufferedInputStream.read()) {
          return false;
        }
      }
    }

    return true;
  }
}

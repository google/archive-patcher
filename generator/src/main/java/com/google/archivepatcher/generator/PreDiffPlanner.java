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

import com.google.archivepatcher.generator.similarity.Crc32SimilarityFinder;
import com.google.archivepatcher.generator.similarity.SimilarityFinder;
import com.google.archivepatcher.shared.JreDeflateParameters;
import com.google.archivepatcher.shared.RandomAccessFileInputStream;
import com.google.archivepatcher.shared.TypedRange;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
   * Optional {@link RecommendationModifier}s that will be applied after the default recommendations
   * have been made but before the {@link PreDiffPlan} is constructed.
   */
  private final List<RecommendationModifier> recommendationModifiers;

  /**
   * Constructs a new planner that will work on the specified inputs
   *
   * @param oldFile the old file, used to compare bytes between old and new entries as necessary
   * @param oldArchiveZipEntriesByPath the entries in the old archive, with paths as keys
   * @param newFile the new file, used to compare bytes between old and new entries as necessary
   * @param newArchiveZipEntriesByPath the entries in the new archive, with paths as keys
   * @param newArchiveJreDeflateParametersByPath the {@link JreDeflateParameters} for each entry in
   *     the new archive, with paths as keys
   * @param recommendationModifiers optionally, {@link RecommendationModifier}s to be applied after
   *     the default recommendations have been made but before the {@link PreDiffPlan} is generated
   *     in {@link #generatePreDiffPlan()}.
   */
  PreDiffPlanner(
      File oldFile,
      Map<ByteArrayHolder, MinimalZipEntry> oldArchiveZipEntriesByPath,
      File newFile,
      Map<ByteArrayHolder, MinimalZipEntry> newArchiveZipEntriesByPath,
      Map<ByteArrayHolder, JreDeflateParameters> newArchiveJreDeflateParametersByPath,
      RecommendationModifier... recommendationModifiers) {
    this.oldFile = oldFile;
    this.oldArchiveZipEntriesByPath = oldArchiveZipEntriesByPath;
    this.newFile = newFile;
    this.newArchiveZipEntriesByPath = newArchiveZipEntriesByPath;
    this.newArchiveJreDeflateParametersByPath = newArchiveJreDeflateParametersByPath;
    this.recommendationModifiers =
          Collections.unmodifiableList(Arrays.asList(recommendationModifiers));
  }

  /**
   * Generates and returns the plan for archive transformations to be made prior to differencing.
   * The resulting {@link PreDiffPlan} has the old and new file uncompression plans set. The
   * delta-friendly new file recompression plan is <em>not</em> set at this time.
   * @return the plan
   * @throws IOException if there are any problems reading the input files
   */
  PreDiffPlan generatePreDiffPlan() throws IOException {
    List<QualifiedRecommendation> recommendations = getDefaultRecommendations();
    for (RecommendationModifier modifier : recommendationModifiers) {
      // Allow changing the recommendations base on arbitrary criteria.
      recommendations = modifier.getModifiedRecommendations(oldFile, newFile, recommendations);
    }

    // Process recommendations to extract ranges for decompression & recompression
    Set<TypedRange<Void>> oldFilePlan = new HashSet<>();
    Set<TypedRange<JreDeflateParameters>> newFilePlan = new HashSet<>();
    for (QualifiedRecommendation recommendation : recommendations) {
      if (recommendation.getRecommendation().uncompressOldEntry) {
        long offset = recommendation.getOldEntry().getFileOffsetOfCompressedData();
        long length = recommendation.getOldEntry().getCompressedSize();
        TypedRange<Void> range = new TypedRange<Void>(offset, length, null);
        oldFilePlan.add(range);
      }
      if (recommendation.getRecommendation().uncompressNewEntry) {
        long offset = recommendation.getNewEntry().getFileOffsetOfCompressedData();
        long length = recommendation.getNewEntry().getCompressedSize();
        JreDeflateParameters newJreDeflateParameters =
            newArchiveJreDeflateParametersByPath.get(
                new ByteArrayHolder(recommendation.getNewEntry().getFileNameBytes()));
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
        Collections.unmodifiableList(recommendations),
        Collections.unmodifiableList(oldFilePlanList),
        Collections.unmodifiableList(newFilePlanList));
  }

  /**
   * Analyzes the input files and returns the default recommendations for each entry in the new
   * archive.
   *
   * @return the recommendations
   * @throws IOException if anything goes wrong
   */
  private List<QualifiedRecommendation> getDefaultRecommendations() throws IOException {
    List<QualifiedRecommendation> recommendations = new ArrayList<>();

    // This will be used to find files that have been renamed, but not modified. This is relatively
    // cheap to construct as it just requires indexing all entries by the uncompressed CRC32, and
    // the CRC32 is already available in the ZIP headers.
    SimilarityFinder trivialRenameFinder =
        new Crc32SimilarityFinder(oldFile, oldArchiveZipEntriesByPath.values());

    // Iterate over every pair of entries and get a recommendation for what to do.
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
          // recommendation and carry on with the normal logic.
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
      // get a recommendation for what to do.
      if (oldZipEntry != null) {
        recommendations.add(getRecommendation(oldZipEntry, newEntry.getValue()));
      }
    }
    return recommendations;
  }

  /**
   * Determines the right {@link QualifiedRecommendation} for handling the (oldEntry, newEntry)
   * tuple.
   * @param oldEntry the entry in the old archive
   * @param newEntry the entry in the new archive
   * @return the recommendation
   * @throws IOException if there are any problems reading the input files
   */
  private QualifiedRecommendation getRecommendation(MinimalZipEntry oldEntry, MinimalZipEntry newEntry)
      throws IOException {

    // Reject anything that is unsuitable for uncompressed diffing.
    // Reason singled out in order to monitor unsupported versions of zlib.
    if (unsuitableDeflate(newEntry)) {
      return new QualifiedRecommendation(
          oldEntry,
          newEntry,
          Recommendation.UNCOMPRESS_NEITHER,
          RecommendationReason.DEFLATE_UNSUITABLE);
    }

    // Reject anything that is unsuitable for uncompressed diffing.
    if (unsuitable(oldEntry, newEntry)) {
      return new QualifiedRecommendation(
          oldEntry,
          newEntry,
          Recommendation.UNCOMPRESS_NEITHER,
          RecommendationReason.UNSUITABLE);
    }

    // If both entries are already uncompressed there is nothing to do.
    if (bothEntriesUncompressed(oldEntry, newEntry)) {
      return new QualifiedRecommendation(
          oldEntry,
          newEntry,
          Recommendation.UNCOMPRESS_NEITHER,
          RecommendationReason.BOTH_ENTRIES_UNCOMPRESSED);
    }

    // The following are now true:
    // 1. At least one of the entries is compressed.
    // 1. The old entry is either uncompressed, or is compressed with deflate.
    // 2. The new entry is either uncompressed, or is reproducibly compressed with deflate.

    if (uncompressedChangedToCompressed(oldEntry, newEntry)) {
      return new QualifiedRecommendation(
          oldEntry,
          newEntry,
          Recommendation.UNCOMPRESS_NEW,
          RecommendationReason.UNCOMPRESSED_CHANGED_TO_COMPRESSED);
    }

    if (compressedChangedToUncompressed(oldEntry, newEntry)) {
      return new QualifiedRecommendation(
          oldEntry,
          newEntry,
          Recommendation.UNCOMPRESS_OLD,
          RecommendationReason.COMPRESSED_CHANGED_TO_UNCOMPRESSED);
    }

    // At this point, both entries must be compressed with deflate.
    if (compressedBytesChanged(oldEntry, newEntry)) {
      return new QualifiedRecommendation(
          oldEntry,
          newEntry,
          Recommendation.UNCOMPRESS_BOTH,
          RecommendationReason.COMPRESSED_BYTES_CHANGED);
    }

    // If the compressed bytes have not changed, there is no need to do anything.
    return new QualifiedRecommendation(
        oldEntry,
        newEntry,
        Recommendation.UNCOMPRESS_NEITHER,
        RecommendationReason.COMPRESSED_BYTES_IDENTICAL);
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
   * Checks if the compressed bytes in the specified entries have changed. No attempt is made to
   * inflate, this method just examines the raw bytes that represent the content in the specified
   * entries and returns true if they are different.
   * @param oldEntry the entry in the old archive
   * @param newEntry the entry in the new archive
   * @return true as described above
   * @throws IOException if unable to read
   */
  private boolean compressedBytesChanged(MinimalZipEntry oldEntry, MinimalZipEntry newEntry)
      throws IOException {
    if (oldEntry.getCompressedSize() != newEntry.getCompressedSize()) {
      // Length is not the same, so content cannot match.
      return true;
    }
    byte[] buffer = new byte[4096];
    int numRead = 0;
    try (RandomAccessFileInputStream newRafis =
            new RandomAccessFileInputStream(
                newFile, newEntry.getFileOffsetOfCompressedData(), newEntry.getCompressedSize());
        MatchingOutputStream matcher =
            new MatchingOutputStream(
                new RandomAccessFileInputStream(
                    oldFile,
                    oldEntry.getFileOffsetOfCompressedData(),
                    oldEntry.getCompressedSize()),
                4096)) {
      while ((numRead = newRafis.read(buffer)) >= 0) {
        try {
          matcher.write(buffer, 0, numRead);
        } catch (MismatchException mismatched) {
          return true;
        }
      }
    }
    return false;
  }
}

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

import java.util.Iterator;
import java.util.List;

import com.google.archivepatcher.shared.JreDeflateParameters;
import com.google.archivepatcher.shared.TypedRange;

/**
 * A plan for transforming the old and the new archive prior to running a diffing algorithm and for
 * recompressing the delta-friendly new archive afterwards.
 * <p>
 * The plan for uncompressing the old file is a {@link List} of {@link TypedRange} entries with void
 * metadata. This describes the chunks of the old file that need to be uncompressed prior to
 * diffing, in file order. The file produced by executing this plan is the "delta-friendly" old
 * archive.
 * <p>
 * The plan for uncompressing the new file is similarly a {@link List} of {@link TypedRange}
 * entries, but this time the metadata is of the type {@link JreDeflateParameters}. This describes
 * the chunks of the new file that need to be uncompressed prior to diffing, in file order. The
 * {@link JreDeflateParameters} metadata indicate the settings that need to be used to generate the
 * inverse transform (the delta friendly new file recompression plan; see below). The file produced
 * by executing this plan is the "delta-friendly" new archive.
 * <p>
 * The plan for recompressing the delta-friendly new archive is again a {@link List} of
 * {@link TypedRange} entries with {@link JreDeflateParameters} metadata. This describes the chunks
 * of the delta-friendly new file that need to be recompressed after diffing, again in file order.
 * The {@link JreDeflateParameters} metadata indicate the settings to use during recompression. The
 * file produced by executing this plan is the new archive, i.e. it reverse the transform of the
 * new file uncompression plan.
 * <p>
 * Finally, a {@link List} of all the {@link QualifiedRecommendation}s upon which all the plans are
 * based is available via {@link #getQualifiedRecommendations()}.
 */
public class PreDiffPlan {
  /**
   * The plan for uncompressing the old file, in file order.
   */
  private final List<TypedRange<Void>> oldFileUncompressionPlan;

  /**
   * The plan for uncompressing the new file, in file order.
   */
  private final List<TypedRange<JreDeflateParameters>> newFileUncompressionPlan;

  /**
   * The plan for recompressing the delta-friendly new file, in file order.
   */
  private final List<TypedRange<JreDeflateParameters>> deltaFriendlyNewFileRecompressionPlan;

  /**
   * The recommendations upon which the plans are based.
   */
  private final List<QualifiedRecommendation> qualifiedRecommendations;

  /**
   * Constructs a new plan.
   * @param qualifiedRecommendations the recommendations upon which the plans are based
   * @param oldFileUncompressionPlan the plan for uncompressing the old file, in file order
   * @param newFileUncompressionPlan the plan for uncompressing the new file, in file order
   */
  public PreDiffPlan(
      List<QualifiedRecommendation> qualifiedRecommendations,
      List<TypedRange<Void>> oldFileUncompressionPlan,
      List<TypedRange<JreDeflateParameters>> newFileUncompressionPlan) {
    this(qualifiedRecommendations, oldFileUncompressionPlan, newFileUncompressionPlan, null);
  }

  /**
   * Constructs a new plan.
   * @param qualifiedRecommendations the recommendations upon which the plans are based
   * @param oldFileUncompressionPlan the plan for uncompressing the old file, in file order
   * @param newFileUncompressionPlan the plan for uncompressing the new file, in file order
   * @param deltaFriendlyNewFileRecompressionPlan the plan for recompression the delta-friendly new
   * file, in file order
   */
  public PreDiffPlan(
      List<QualifiedRecommendation> qualifiedRecommendations,
      List<TypedRange<Void>> oldFileUncompressionPlan,
      List<TypedRange<JreDeflateParameters>> newFileUncompressionPlan,
      List<TypedRange<JreDeflateParameters>> deltaFriendlyNewFileRecompressionPlan) {
    ensureOrdered(oldFileUncompressionPlan);
    ensureOrdered(newFileUncompressionPlan);
    ensureOrdered(deltaFriendlyNewFileRecompressionPlan);
    this.qualifiedRecommendations = qualifiedRecommendations;
    this.oldFileUncompressionPlan = oldFileUncompressionPlan;
    this.newFileUncompressionPlan = newFileUncompressionPlan;
    this.deltaFriendlyNewFileRecompressionPlan = deltaFriendlyNewFileRecompressionPlan;
  }

  /**
   * Ensures that the lists passed into the constructors are ordered and throws an exception if
   * they are not. Null lists and lists whose size is less than 2 are ignored.
   * @param list the list to check
   */
  private <T> void ensureOrdered(List<TypedRange<T>> list) {
    if (list != null && list.size() >= 2) {
      Iterator<TypedRange<T>> iterator = list.iterator();
      TypedRange<T> lastEntry = iterator.next();
      while (iterator.hasNext()) {
        TypedRange<T> nextEntry = iterator.next();
        if (lastEntry.compareTo(nextEntry) > 0) {
          throw new IllegalArgumentException("List must be ordered");
        }
      }
    }
  }

  /**
   * Returns the plan for uncompressing the old file to create the delta-friendly old file.
   * @return the plan
   */
  public final List<TypedRange<Void>> getOldFileUncompressionPlan() {
    return oldFileUncompressionPlan;
  }

  /**
   * Returns the plan for uncompressing the new file to create the delta-friendly new file.
   * @return the plan
   */
  public final List<TypedRange<JreDeflateParameters>> getNewFileUncompressionPlan() {
    return newFileUncompressionPlan;
  }

  /**
   * Returns the plan for recompressing the delta-friendly new file to regenerate the original new
   * file.
   * @return the plan
   */
  public final List<TypedRange<JreDeflateParameters>> getDeltaFriendlyNewFileRecompressionPlan() {
    return deltaFriendlyNewFileRecompressionPlan;
  }

  /**
   * Returns the recommendations upon which the plans are based.
   * @return the recommendations
   */
  public final List<QualifiedRecommendation> getQualifiedRecommendations() {
    return qualifiedRecommendations;
  }
}

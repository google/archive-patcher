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

/**
 * A fully qualified recommendation, consisting of an {@link MinimalZipEntry} from the old file,
 * a {@link MinimalZipEntry} from the new file, a {@link Recommendation} for how to proceed and a
 * {@link RecommendationReason} for that recommendation.
 */
public class QualifiedRecommendation {
  /**
   * The entry in the old file.
   */
  private final MinimalZipEntry oldEntry;

  /**
   * The entry in the new file.
   */
  private final MinimalZipEntry newEntry;

  /**
   * The recommendation for how to proceed on the pair of entries.
   */
  private final Recommendation recommendation;

  /**
   * The reason for the recommendation.
   */
  private final RecommendationReason reason;

  /**
   * Construct a new qualified recommendation with the specified data.
   * @param oldEntry the entry in the old file
   * @param newEntry the entry in the new file
   * @param recommendation the recommendation for this tuple of entries
   * @param reason the reason for the recommendation
   */
  public QualifiedRecommendation(
      MinimalZipEntry oldEntry,
      MinimalZipEntry newEntry,
      Recommendation recommendation,
      RecommendationReason reason) {
    super();
    this.oldEntry = oldEntry;
    this.newEntry = newEntry;
    this.recommendation = recommendation;
    this.reason = reason;
  }

  /**
   * Returns the entry in the old file.
   * @return as described
   */
  public MinimalZipEntry getOldEntry() {
    return oldEntry;
  }

  /**
   * Returns the entry in the new file.
   * @return as described
   */
  public MinimalZipEntry getNewEntry() {
    return newEntry;
  }

  /**
   * Returns the recommendation for how to proceed for this tuple of entries.
   * @return as described
   */
  public Recommendation getRecommendation() {
    return recommendation;
  }

  /**
   * Returns the reason for the recommendation.
   * @return as described
   */
  public RecommendationReason getReason() {
    return reason;
  }
}
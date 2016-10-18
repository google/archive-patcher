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

import java.io.File;
import java.util.List;

/**
 * Provides a mechanism to review and possibly modify the {@link QualifiedRecommendation}s that will
 * be used to derive a {@link PreDiffPlan}.
 */
public interface RecommendationModifier {
  /**
   * Given a list of {@link QualifiedRecommendation} objects, returns a list of the same type that
   * has been arbitrarily adjusted as desired by the implementation. Implementations must return a
   * list of recommendations that contains the same tuples of (oldEntry, newEntry) but may change
   * the results of {@link QualifiedRecommendation#getRecommendation()} and {@link
   * QualifiedRecommendation#getReason()} to any sane values.
   *
   * @param oldFile the old file that is being diffed
   * @param newFile the new file that is being diffed
   * @param originalRecommendations the original recommendations
   * @return the updated list of recommendations
   */
  public List<QualifiedRecommendation> getModifiedRecommendations(
      File oldFile, File newFile, List<QualifiedRecommendation> originalRecommendations);
}

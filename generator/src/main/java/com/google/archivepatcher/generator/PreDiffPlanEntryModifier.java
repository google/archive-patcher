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

import com.google.archivepatcher.shared.bytesource.ByteSource;
import java.util.List;

/**
 * Provides a mechanism to review and possibly modify the {@link PreDiffPlanEntry}s that will be
 * used to derive a {@link PreDiffPlan}.
 */
public interface PreDiffPlanEntryModifier {
  /**
   * Given a list of {@link PreDiffPlanEntry} objects, returns a list of the same type that has been
   * arbitrarily adjusted as desired by the implementation. Implementations must return a list of
   * entries that contains the same tuples of (oldEntry, newEntry) but may change the results of
   * {@link PreDiffPlanEntry#zipEntryUncompressionOption()} and {@link
   * PreDiffPlanEntry#uncompressionOptionExplanation()} to any sane values.
   *
   * @param oldFile the old file that is being diffed
   * @param newFile the new file that is being diffed
   * @param originalEntries the original {@link PreDiffPlanEntry}
   * @return the updated list of {@link PreDiffPlanEntry}
   */
  List<PreDiffPlanEntry> getModifiedPreDiffPlanEntries(
      ByteSource oldFile, ByteSource newFile, List<PreDiffPlanEntry> originalEntries);
}

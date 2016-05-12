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

package com.google.archivepatcher.generator.similarity;

import java.io.File;
import java.util.Collection;
import java.util.List;

import com.google.archivepatcher.generator.MinimalZipEntry;

/**
 * A class that analyzes an archive to find files similar to a specified file.
 */
public abstract class SimilarityFinder {

  /**
   * The base archive that contains the entries to be searched.
   */
  protected final File baseArchive;

  /**
   * The entries in the base archive that are eligible to be searched.
   */
  protected final Collection<MinimalZipEntry> baseEntries;

  /**
   * Create a new instance to check for similarity of arbitrary files against the specified entries
   * in the specified archive.
   * @param baseArchive the base archive that contains the entries to be scored against
   * @param baseEntries the entries in the base archive that are eligible to be scored against.
   */
  public SimilarityFinder(File baseArchive, Collection<MinimalZipEntry> baseEntries) {
    this.baseArchive = baseArchive;
    this.baseEntries = baseEntries;
  }

  /**
   * Searches for files similar to the specified entry in the specified new archive against all of
   * the available entries in the base archive.
   * @param newArchive the new archive that contains the new entry
   * @param newEntry the new entry to compare against the entries in the base archive
   * @return a {@link List} of {@link MinimalZipEntry} entries (possibly empty but never null) from
   * the base archive that are similar to the new archive; if the list has more than one entry, the
   * entries should be in order from most similar to least similar.
   */
  public abstract List<MinimalZipEntry> findSimilarFiles(File newArchive, MinimalZipEntry newEntry);
}

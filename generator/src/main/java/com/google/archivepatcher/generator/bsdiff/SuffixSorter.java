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

package com.google.archivepatcher.generator.bsdiff;

import java.io.IOException;

/**
 * An algorithm that performs a suffix sort on a given input and returns a suffix array.
 * See https://en.wikipedia.org/wiki/Suffix_array
 */
public interface SuffixSorter {

  /**
   * Perform a "suffix sort". Note: the returned {@link RandomAccessObject} should be closed by the
   * caller.
   *
   * @param data the data to sort
   * @return the suffix array, as a {@link RandomAccessObject}
   * @throws IOException if unable to read data
   * @throws InterruptedException if any thread interrupts this thread
   */
  RandomAccessObject suffixSort(RandomAccessObject data) throws IOException, InterruptedException;
}


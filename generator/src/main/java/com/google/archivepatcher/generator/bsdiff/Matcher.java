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
 * Helper class which iterates through |newData| finding the longest valid exact matches between
 * |newData| and |oldData|. The interface exists for the sake of testing.
 */
interface Matcher {
  /**
   * Determine the range for the next match, and store it in member state.
   * @return a {@link NextMatch} describing the result
   */
  NextMatch next() throws IOException, InterruptedException;

  /**
   * Contains a boolean which indicates whether a match was found, the old position (if a match was
   * found), and the new position (if a match was found).
   */
  static class NextMatch {
    final boolean didFindMatch;
    final int oldPosition;
    final int newPosition;

    static NextMatch of(boolean didFindMatch, int oldPosition, int newPosition) {
      return new NextMatch(didFindMatch, oldPosition, newPosition);
    }

    private NextMatch(boolean didFindMatch, int oldPosition, int newPosition) {
      this.didFindMatch = didFindMatch;
      this.oldPosition = oldPosition;
      this.newPosition = newPosition;
    }
  }
}

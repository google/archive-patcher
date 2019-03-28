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

package com.google.archivepatcher.shared;

import java.io.Closeable;
import java.io.DataInput;
import java.io.File;
import java.io.IOException;

/** A {@link DataInput} that supports operations like {@link #length()} */
public interface SeekableDataInput extends DataInput, Closeable {

  /**
   * Returns the length of data input.
   *
   * @throws IOException if unable to determine the length of the input
   */
  long length() throws IOException;

  /**
   * Seeks to a specified position, in bytes, into the data input.
   *
   * @param pos the position to seek to
   * @throws IOException if seeking fails
   */
  void seek(long pos) throws IOException;

  /**
   * Returns the current relative position in the data input.
   *
   * @throws IOException if failed to get the position.
   */
  long getPosition() throws IOException;

  static SeekableDataInput fromFile(File file) throws IOException {
    return new SeekableFileInput(file);
  }
}

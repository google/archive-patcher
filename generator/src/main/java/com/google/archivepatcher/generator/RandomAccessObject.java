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

import java.io.Closeable;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

// TODO: clean up the implementations, we only really need two and they can be in
// separate files.

/**
 * Interface which offers random access interface. This class exists to allow delta generators to
 * use either a RandomAccessFile for disk-based io, or a byte[] for fully in-memory operation.
 */
public interface RandomAccessObject extends DataInput, DataOutput, Closeable {

  /**
   * Returns the length of the file or byte array associated with the RandomAccessObject.
   *
   * @return the length of the file or byte array associated with the RandomAccessObject
   * @throws IOException if unable to determine the length of the file, when backed by a file
   */
  long length() throws IOException;

  /**
   * Seeks to a specified position, in bytes, into the file or byte array.
   *
   * @param pos the position to seek to
   * @throws IOException if seeking fails
   */
  void seek(long pos) throws IOException;

  /**
   * Seek to a specified integer-aligned position in the associated file or byte array. For example,
   * seekToIntAligned(5) will seek to the beginning of the 5th integer, or in other words the 20th
   * byte. In general, seekToIntAligned(n) will seek to byte 4n.
   *
   * @param pos the position to seek to
   * @throws IOException if seeking fails
   */
  void seekToIntAligned(long pos) throws IOException;
}

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

package com.google.archivepatcher.applier.gdiff;

import com.google.archivepatcher.applier.PatchFormatException;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;

/** Clean implementation of http://www.w3.org/TR/NOTE-gdiff-19970901 */
public class Gdiff {

  /** Magic bytes at start of file */
  private static final int GDIFF_FILE_MAGIC = 0xD1FFD1FF;
  /** Version code at start of file */
  private static final int GDIFF_FILE_VERSION = 4;

  /** The end of the patch */
  private static final int EOF = 0;
  /** Codes 1..246 represent inline streams of 1..246 bytes */
  // private static final int DATA_MIN = 1;
  // private static final int DATA_MAX = 246;
  /** Copy inline data. The next two bytes are the number of bytes to copy */
  private static final int DATA_USHORT = 247;
  /** Copy inline data. The next four bytes are the number of bytes to copy */
  private static final int DATA_INT = 248;
  /**
   * The copy commands are defined as follows: The first argument is the offset in the original
   * file, and the second argument is the number of bytes to copy to the new file.
   */
  private static final int COPY_USHORT_UBYTE = 249;

  private static final int COPY_USHORT_USHORT = 250;
  private static final int COPY_USHORT_INT = 251;
  private static final int COPY_INT_UBYTE = 252;
  private static final int COPY_INT_USHORT = 253;
  private static final int COPY_INT_INT = 254;
  private static final int COPY_LONG_INT = 255;

  /**
   * We're patching APKs which are big. We might as well use a big buffer. TODO: 64k? This would
   * allow us to do the USHORT copies in a single pass.
   */
  private static final int COPY_BUFFER_SIZE = 16 * 1024;

  /**
   * The patch is typically compressed and the input stream is decompressing on-the-fly. A small
   * buffer greatly improves efficiency on complicated patches with lots of short directives. See
   * b/21109650 for more information.
   */
  private static final int PATCH_STREAM_BUFFER_SIZE = 4 * 1024;

  /**
   * Apply a patch to a file.
   *
   * @param inputFile base file
   * @param patchFile patch file
   * @param output output stream to write the file to
   * @param expectedOutputSize expected size of the output.
   * @throws IOException on file I/O as well as when patch under/over run happens.
   */
  public static long patch(
      RandomAccessFile inputFile,
      InputStream patchFile,
      OutputStream output,
      long expectedOutputSize)
      throws IOException {
    byte[] buffer = new byte[COPY_BUFFER_SIZE];
    long outputSize = 0;

    // Wrap patchfile with a small buffer to cushion the 1,2,4,8 byte reads
    patchFile = new BufferedInputStream(patchFile, PATCH_STREAM_BUFFER_SIZE);
    // Use DataInputStream to help with big-endian data
    DataInputStream patchDataStream = new DataInputStream(patchFile);
    // Confirm first 4 bytes are magic signature.
    int magic = patchDataStream.readInt();
    if (magic != GDIFF_FILE_MAGIC) {
      throw new PatchFormatException("Unexpected magic=" + String.format("%x", magic));
    }
    // Confirm patch format version
    int version = patchDataStream.read();
    if (version != GDIFF_FILE_VERSION) {
      throw new PatchFormatException("Unexpected version=" + version);
    }

    try {
      // Start copying
      while (true) {
        int copyLength;
        long copyOffset;
        long maxCopyLength = expectedOutputSize - outputSize;
        int command = patchDataStream.read();
        switch (command) {
          case -1:
            throw new IOException("Patch file overrun");
          case EOF:
            return outputSize;
          case DATA_USHORT:
            copyLength = patchDataStream.readUnsignedShort();
            copyFromPatch(buffer, patchDataStream, output, copyLength, maxCopyLength);
            break;
          case DATA_INT:
            copyLength = patchDataStream.readInt();
            copyFromPatch(buffer, patchDataStream, output, copyLength, maxCopyLength);
            break;
          case COPY_USHORT_UBYTE:
            copyOffset = patchDataStream.readUnsignedShort();
            copyLength = patchDataStream.read();
            if (copyLength == -1) {
              throw new IOException("Unexpected end of patch");
            }
            copyFromOriginal(buffer, inputFile, output, copyOffset, copyLength, maxCopyLength);
            break;
          case COPY_USHORT_USHORT:
            copyOffset = patchDataStream.readUnsignedShort();
            copyLength = patchDataStream.readUnsignedShort();
            copyFromOriginal(buffer, inputFile, output, copyOffset, copyLength, maxCopyLength);
            break;
          case COPY_USHORT_INT:
            copyOffset = patchDataStream.readUnsignedShort();
            copyLength = patchDataStream.readInt();
            copyFromOriginal(buffer, inputFile, output, copyOffset, copyLength, maxCopyLength);
            break;
          case COPY_INT_UBYTE:
            copyOffset = patchDataStream.readInt();
            copyLength = patchDataStream.read();
            if (copyLength == -1) {
              throw new IOException("Unexpected end of patch");
            }
            copyFromOriginal(buffer, inputFile, output, copyOffset, copyLength, maxCopyLength);
            break;
          case COPY_INT_USHORT:
            copyOffset = patchDataStream.readInt();
            copyLength = patchDataStream.readUnsignedShort();
            copyFromOriginal(buffer, inputFile, output, copyOffset, copyLength, maxCopyLength);
            break;
          case COPY_INT_INT:
            copyOffset = patchDataStream.readInt();
            copyLength = patchDataStream.readInt();
            copyFromOriginal(buffer, inputFile, output, copyOffset, copyLength, maxCopyLength);
            break;
          case COPY_LONG_INT:
            copyOffset = patchDataStream.readLong();
            copyLength = patchDataStream.readInt();
            copyFromOriginal(buffer, inputFile, output, copyOffset, copyLength, maxCopyLength);
            break;
          default:
            // The only possible bytes remaining are DATA_MIN through DATA_MAX,
            // barring any programming error.
            copyLength = command;
            copyFromPatch(buffer, patchDataStream, output, copyLength, maxCopyLength);
            break;
        }
        outputSize += copyLength;
      }
    } finally {
      output.flush();
    }
  }

  /** Copy a series of inline bytes from the patch file to the output file */
  private static void copyFromPatch(
      byte[] buffer,
      DataInputStream patchDataStream,
      OutputStream output,
      int copyLength,
      long maxCopyLength)
      throws IOException {
    if (copyLength < 0) {
      throw new IOException("copyLength negative");
    }
    if (copyLength > maxCopyLength) {
      throw new IOException("Output length overrun");
    }
    try {
      while (copyLength > 0) {
        int spanLength = (copyLength < COPY_BUFFER_SIZE) ? copyLength : COPY_BUFFER_SIZE;
        patchDataStream.readFully(buffer, 0, spanLength);
        output.write(buffer, 0, spanLength);
        copyLength -= spanLength;
      }
    } catch (EOFException e) {
      throw new IOException("patch underrun");
    }
  }

  /** Copy a series of bytes from the input (original) file to the output file */
  private static void copyFromOriginal(
      byte[] buffer,
      RandomAccessFile inputFile,
      OutputStream output,
      long inputOffset,
      int copyLength,
      long maxCopyLength)
      throws IOException {
    if (copyLength < 0) {
      throw new IOException("copyLength negative");
    }
    if (inputOffset < 0) {
      throw new IOException("inputOffset negative");
    }
    if (copyLength > maxCopyLength) {
      throw new IOException("Output length overrun");
    }
    try {
      inputFile.seek(inputOffset);
      while (copyLength > 0) {
        int spanLength = (copyLength < COPY_BUFFER_SIZE) ? copyLength : COPY_BUFFER_SIZE;
        inputFile.readFully(buffer, 0, spanLength);
        output.write(buffer, 0, spanLength);
        copyLength -= spanLength;
      }
    } catch (EOFException e) {
      throw new IOException("patch underrun", e);
    }
  }
}

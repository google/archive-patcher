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

package com.google.archivepatcher.shared.bytesource;

import java.io.File;

/** A {@link ByteSource} backed by a {@link File}. */
public abstract class FileByteSource extends ByteSource {

  private final File file;
  private final long length;

  FileByteSource(File file) {
    this.file = file;
    this.length = file.length();
  }

  @Override
  public long length() {
    return length;
  }

  /**
   * Getter for the underlying file for cases where we absolutely needs it, e.g., passing file name
   * to native API.
   */
  public File getFile() {
    return file;
  }
}

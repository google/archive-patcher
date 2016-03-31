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
import java.io.File;
import java.io.IOException;

/**
 * A closeable container for a temp file that deletes itself on {@link #close()}. This is convenient
 * for try-with-resources constructs that need to use temp files in scope.
 */
public class TempFileHolder implements Closeable {
  /**
   * The file that is wrapped by this holder.
   */
  public final File file;

  /**
   * Create a new temp file and wrap it in an instance of this class. The file is automatically
   * scheduled for deletion on JVM termination, so it is a serious error to rely on this file path
   * being a durable artifact.
   * @throws IOException if unable to create the file
   */
  public TempFileHolder() throws IOException {
    file = File.createTempFile("archive_patcher", "tmp");
    file.deleteOnExit();
  }

  @Override
  public void close() throws IOException {
    file.delete();
  }
}
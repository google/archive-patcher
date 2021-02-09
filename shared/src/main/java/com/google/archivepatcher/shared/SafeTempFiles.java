// Copyright 2021 Google LLC. All rights reserved.
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

import java.io.File;
import java.io.IOException;

/** Creates temp files with read-write permissions only for the owner */
public abstract class SafeTempFiles {

  public static File createTempFile(String prefix, String suffix, File parent) throws IOException {
    File file = File.createTempFile(prefix, suffix, parent);
    setTempFilePermissions(file);
    return file;
  }

  public static File createTempFile(String prefix, String suffix) throws IOException {
    File file = File.createTempFile(prefix, suffix);
    setTempFilePermissions(file);
    return file;
  }

  private static void setTempFilePermissions(File file) {
    file.setReadable(false, false); // remove read permissions for everyone
    file.setReadable(true, true); // add read perissions only for the owner.

    file.setWritable(false, false); // remove write permissions for everyone
    file.setWritable(true, true); // writable only by the owner.

    file.setExecutable(false, false); // remove execute permissions for everyone
  }
}

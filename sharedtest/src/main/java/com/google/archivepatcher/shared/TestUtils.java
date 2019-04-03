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

package com.google.archivepatcher.shared;

import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/** This class contains static helper classes/methods for use in tests. */
public class TestUtils {

  public interface ThrowingRunnable {
    void run() throws Throwable;
  }

  public static final <T extends Throwable> void assertThrows(
      Class<T> throwable, ThrowingRunnable runnable) {
    try {
      runnable.run();
    } catch (Throwable t) {
      if (!throwable.isAssignableFrom(t.getClass())) {
        assertWithMessage(
                String.format(
                    "Wrong type of throwable caught. Expected type: %s. Actual: %s", throwable, t))
            .fail();
      }
      return;
    }
    assertWithMessage(String.format("No throwables caught. Expected throwable: %s", throwable))
        .fail();
  }

  public static File storeInTempFile(InputStream content) throws Exception {
    File tmpFile = null;
    try {
      tmpFile = File.createTempFile("archive_patcher_test", "temp");
      tmpFile.deleteOnExit();
      Files.copy(content, tmpFile.toPath(), REPLACE_EXISTING);
      return tmpFile;
    } catch (IOException e) {
      if (tmpFile != null) {
        // Attempt immediate cleanup.
        tmpFile.delete();
      }
      throw e;
    }
  }
}

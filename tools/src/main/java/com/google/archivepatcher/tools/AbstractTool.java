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

package com.google.archivepatcher.tools;

import java.io.File;
import java.util.Iterator;

/**
 * Simple base class for tools. Minimal standalone functionality free of third-party argument parser
 * dependencies.
 */
public abstract class AbstractTool {

  /**
   * Pop an argument from the argument iterator or exit with a usage message about the expected
   * type of argument that was supposed to be found.
   * @param iterator the iterator to take an element from if available
   * @param expectedType description for the thing that was supposed to be in the iterator, for
   * error messages
   * @return the element retrieved from the iterator
   */
  protected String popOrDie(Iterator<String> iterator, String expectedType) {
    if (!iterator.hasNext()) {
      exitWithUsage("missing argument for " + expectedType);
    }
    return iterator.next();
  }

  /**
   * Find and return a readable file if it exists, exit with a usage message if it does not.
   * @param path the path to check and get a {@link File} for
   * @param description what the file represents, for error messages
   * @return a {@link File} representing the path, which exists and is readable
   */
  protected File getRequiredFileOrDie(String path, String description) {
    File result = new File(path);
    if (!result.exists() || !result.canRead()) {
      exitWithUsage(description + " does not exist or cannot be read: " + path);
    }
    return result;
  }

  /**
   * Terminate the program with an error message and usage instructions.
   * @param message the error message to give to the user prior to the usage instructions
   */
  protected void exitWithUsage(String message) {
    System.err.println("Error: " + message);
    System.err.println(getUsage());
    System.exit(1);
  }

  /**
   * Returns a string describing the usage for this tool.
   * @return the string
   */
  protected abstract String getUsage();
}

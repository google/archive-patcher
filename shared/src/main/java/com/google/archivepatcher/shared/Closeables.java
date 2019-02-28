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

import java.io.InputStream;

/** Helper to handle Closeables. */
public class Closeables {

  /** Close input stream quietly after use. All exceptions are swallowed. */
  static void closeQuitely(InputStream inputStream) {
    if (inputStream == null) {
      return;
    }
    try {
      inputStream.close();
    } catch (Exception ignore) {
      // Ignore
    }
  }
}

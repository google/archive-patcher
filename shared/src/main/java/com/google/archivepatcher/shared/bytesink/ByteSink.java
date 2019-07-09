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

package com.google.archivepatcher.shared.bytesink;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

/** A destination to which bytes can be written, such as a file. */
public abstract class ByteSink implements Closeable {

  /** Returns an {@link OutputStream} to write to this ByteSink. */
  public abstract OutputStream openStream() throws IOException;

  /** Returns the content written to this sink as a byte array. */
  public abstract byte[] toByteArray() throws IOException;
}

// Copyright 2015 Google LLC. All rights reserved.
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

import java.util.zip.Inflater;

/**
 * Wrapper around {@link java.util.zip.Inflater} to deal with the issue of {@link
 * java.util.zip.InflaterInputStream} closing inflaters on some JDKs.
 *
 * <p>For details, see https://issuetracker.google.com/issues/137525057.
 */
public class InflaterWrapper extends Inflater {

  public InflaterWrapper(boolean nowrap) {
    super(nowrap);
  }

  public InflaterWrapper() {
    super();
  }

  @Override
  public void end() {
    // Do nothing here.
  }

  @Override
  protected void finalize() {
    endInternal();
  }

  /**
   * Ends this inflater instance.
   *
   * @see Inflater#end()
   */
  public void endInternal() {
    super.end();
  }
}

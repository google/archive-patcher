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

import static com.google.archivepatcher.shared.TestUtils.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test for {@link com.google.archivepatcher.shared.InflaterWrapper} */
@RunWith(JUnit4.class)
public class InflaterWrapperTest {

  @Test
  public void end() {
    InflaterWrapper inflaterWrapper = new InflaterWrapper();

    inflaterWrapper.end();

    // Inflater.ended() is not visible here. We use "reset" to indirectly detect if the Inflater is
    // ended.
    inflaterWrapper.reset();
  }

  @Test
  public void endInternal() {
    InflaterWrapper inflaterWrapper = new InflaterWrapper();

    inflaterWrapper.endInternal();

    // Inflater.ended() is not visible here. We use "reset" to indirectly detect if the Inflater is
    // ended.
    assertThrows(NullPointerException.class, () -> inflaterWrapper.reset());
  }
}

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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link DefaultDeflateCompatibilityWindow}.
 */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class JreDeflateParametersTest {

  @Test
  public void testConstructor_AllValidValues() {
    for (int level = 1; level <= 9; level++) {
      for (int strategy = 0; strategy <= 2; strategy++) {
        for (boolean nowrap : new boolean[] {true, false}) {
          new JreDeflateParameters(level, strategy, nowrap);
        }
      }
    }
  }

  private void assertIllegalArgumentException(int level, int strategy, boolean nowrap) {
    try {
      new JreDeflateParameters(level, strategy, nowrap);
      Assert.fail("Invalid configuration allowed");
    } catch (IllegalArgumentException expected) {
      // Pass
    }
  }

  @Test
  public void testConstructor_InvalidValues() {
    // All of these should fail.
    assertIllegalArgumentException(0, 0, true); // Bad compression level (store)
    assertIllegalArgumentException(-1, 0, true); // Bad cmpressiong level (insane value < 0)
    assertIllegalArgumentException(10, 0, true); // Bad cmpressiong level (insane value > 9)
    assertIllegalArgumentException(1, -1, true); // Bad strategy (insane value < 0)
    assertIllegalArgumentException(1, 3, true); // Bad strategy (valid in zlib, unsupported in JRE)
  }

  @Test
  public void testHashCode() {
    int hash1 = new JreDeflateParameters(1, 0, true).hashCode();
    int hash2 = new JreDeflateParameters(2, 0, true).hashCode();
    Assert.assertNotEquals(0, hash1);
    Assert.assertNotEquals(0, hash2);
    Assert.assertNotEquals(hash1, hash2); // Ensure two non-equivalent objects don't share a hash
    int hash1b = new JreDeflateParameters(1, 0, true).hashCode();
    Assert.assertEquals(hash1, hash1b); // Ensure two separate but equivalent objects share a hash
  }

  @Test
  public void testEquals() {
    JreDeflateParameters params = new JreDeflateParameters(1, 0, true);
    Assert.assertEquals(params, params); // Identity case
    Assert.assertEquals(params, new JreDeflateParameters(1, 0, true)); // Equivalence case
    Assert.assertNotEquals(params, new JreDeflateParameters(2, 0, true)); // Different level
    Assert.assertNotEquals(params, new JreDeflateParameters(1, 1, true)); // Different strategy
    Assert.assertNotEquals(params, new JreDeflateParameters(1, 0, false)); // Different nowrap
  }

  @Test
  public void testToString() {
    // Ensure that toString() doesn't crash and produces a non-empty string.
    Assert.assertTrue(new JreDeflateParameters(1, 0, true).toString().length() > 0);
  }

  @Test
  public void testParseString() {
    for (int level = 1; level <= 9; level++) {
      for (int strategy = 0; strategy <= 2; strategy++) {
        for (boolean nowrap : new boolean[] {true, false}) {
          JreDeflateParameters params = new JreDeflateParameters(level, strategy, nowrap);
          String asString = params.toString();
          JreDeflateParameters fromString = JreDeflateParameters.parseString(asString);
          Assert.assertEquals(params, fromString);
        }
      }
    }
  }
}

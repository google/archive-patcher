// Copyright 2014 Google Inc. All rights reserved.
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

package com.google.archivepatcher.bsdiff;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link BsDiff} and {@link BsPatch}.
 */
@SuppressWarnings("javadoc")
public class BsDiffTest {

    private final static String CONTENT1 =
        "This is version 1 of a test file.";
    private final static String CONTENT2 =
        "This is version 2 of a test file.";
    private final static String CONTENT3 =
        "ugwj4o9ghv95-tm2y,045uy98w45spy9tbu47by0-m4vY%^*WUWB#V%YMO^%JYL%KJU^Y";
    private ByteArrayOutputStream patchBuffer;

    private final static byte[] content1() {
        return CONTENT1.getBytes(Charset.forName("UTF8"));
    }

    private final static byte[] content2() {
        return CONTENT2.getBytes(Charset.forName("UTF8"));
    }

    private final static byte[] content3() {
        return CONTENT3.getBytes(Charset.forName("UTF8"));
    }

    @Before
    public void setUp() {
        patchBuffer = new ByteArrayOutputStream();
    }

    @Test
    public void testIdentityPatch() throws Exception {
        // Generate diff
        BsDiff.generatePatch(content1(), content1(), patchBuffer);
        // Verify it is non-empty
        assertTrue(patchBuffer.size() > 0);

        // Now we must read the patch in. Stream the generated patch back in...
        ByteArrayInputStream in = new ByteArrayInputStream(patchBuffer.toByteArray());
        // And we obviously know the size we need for the output buffer...
        byte[] patchResult = new byte[content1().length];
        // Apply the patch
        BsPatch.applyPatch(content1(), patchResult, in);
        // Verify
        assertTrue(Arrays.equals(content1(), patchResult));
    }

    @Test
    public void testTrivialPatch() throws Exception {
        // Generate diff
        BsDiff.generatePatch(content1(), content2(), patchBuffer);
        // Verify it is non-empty
        assertTrue(patchBuffer.size() > 0);

        // Now we must read the patch in. Stream the generated patch back in...
        ByteArrayInputStream in = new ByteArrayInputStream(patchBuffer.toByteArray());
        // And we obviously know the size we need for the output buffer...
        byte[] patchResult = new byte[content2().length];
        // Apply the patch
        BsPatch.applyPatch(content1(), patchResult, in);
        // Verify
        assertTrue(Arrays.equals(content2(), patchResult));
    }

    @Test
    public void testDrasticChangesPatch() throws Exception {
        // Generate diff
        BsDiff.generatePatch(content1(), content3(), patchBuffer);
        // Verify it is non-empty
        assertTrue(patchBuffer.size() > 0);

        // Now we must read the patch in. Stream the generated patch back in...
        ByteArrayInputStream in = new ByteArrayInputStream(patchBuffer.toByteArray());
        // And we obviously know the size we need for the output buffer...
        byte[] patchResult = new byte[content3().length];
        // Apply the patch
        BsPatch.applyPatch(content1(), patchResult, in);
        // Verify
        assertTrue(Arrays.equals(content3(), patchResult));
    }

}
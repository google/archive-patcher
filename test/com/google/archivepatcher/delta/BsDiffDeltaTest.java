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

package com.google.archivepatcher.delta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import com.google.archivepatcher.Archive;
import com.google.archivepatcher.PatchApplier;
import com.google.archivepatcher.PatchGenerator;
import com.google.archivepatcher.bsdiff.BsDiff;
import com.google.archivepatcher.bsdiff.BsPatch;
import com.google.archivepatcher.patcher.PatchCommand;
import com.google.archivepatcher.testutil.ObservablePatchParser;
import com.google.archivepatcher.testutil.TestFile;
import com.google.archivepatcher.util.DescriptiveComparator;
import com.google.archivepatcher.util.SimpleArchive;

/**
 * Tests for {@link BsDiff} and {@link BsPatch}.
 */
@SuppressWarnings("javadoc")
public class BsDiffDeltaTest {
    private final static long NOWISH = System.currentTimeMillis();
    private final static byte[] CONTENT1;
    private final static byte[] CONTENT2;
    private final static int CONTENT1_LENGTH = 50000;
    private final static int CONTENT2_LENGTH = 60000;
    private final static int COMMON_PREFIX_LENGTH = 10000;
    private final static int COMMON_CENTER_OFFSET = 20000;
    private final static int COMMON_CENTER_LENGTH = 11000;
    static {
        // Set up two pieces of content (CONTENT1 and CONTENT2) that have a
        // few sections of common data that the bsdiff algorithm should be able
        // to efficiently compress. Surround them with random data, to simulate
        // the results of compilation of different versions of software. This
        // is obviously naive, but sufficient for testing basic functionality.

        // First, a bunch of random junk
        Random random = new Random(1337L);
        CONTENT1 = new byte[CONTENT1_LENGTH];
        random.nextBytes(CONTENT1);
        CONTENT2 = new byte[CONTENT2_LENGTH];
        random.nextBytes(CONTENT2);

        System.arraycopy(CONTENT1, 0, CONTENT2, 0, COMMON_PREFIX_LENGTH);
        System.arraycopy(CONTENT1, COMMON_CENTER_OFFSET, CONTENT2, COMMON_CENTER_OFFSET, COMMON_CENTER_LENGTH);
    }

    private Archive archive1;
    private Archive archive2;
    private ByteArrayOutputStream outBuffer;
    private DataOutput out;
    private BsDiffDeltaGenerator deltaGenerator;
    private BsDiffDeltaApplier deltaApplier;

    private static Archive makeUncompressedArchive(TestFile... files) throws IOException {
        SimpleArchive archive = new SimpleArchive();
        for (TestFile tf : files) {
            archive.add(tf.file, NOWISH, tf.getInputStream(), false);
        }
        archive.finishArchive();
        return archive;
    }

    @Before
    public void setUp() throws IOException {
        archive1 = makeUncompressedArchive(
            new TestFile("file", CONTENT1));
        archive2 = makeUncompressedArchive(
            new TestFile("file", CONTENT2));
        outBuffer = new ByteArrayOutputStream();
        out = new DataOutputStream(outBuffer);
        deltaGenerator = new BsDiffDeltaGenerator();
        deltaApplier = new BsDiffDeltaApplier();
    }

    private byte[] toByteArray(Archive archive) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        archive.writeArchive(out);
        return out.toByteArray();
    }

    @Test
    public void testGenerateAndApply() throws Exception {
        PatchGenerator generator = new PatchGenerator(archive1, archive2, out, deltaGenerator);
        generator.generateAll();
        byte[] patch = outBuffer.toByteArray();
        assertTrue(patch.length < CONTENT1_LENGTH);
        ByteArrayInputStream in = new ByteArrayInputStream(patch);
        DataInputStream dataIn = new DataInputStream(in);
        ObservablePatchParser parser = new ObservablePatchParser(dataIn);
        PatchApplier applier = new PatchApplier(archive1, parser, deltaApplier);
        Archive patchedArchive = applier.applyPatch();
        assertTrue(parser.initInvoactionCount > 0);
        assertEquals(2, parser.directivesRead.size());
        assertEquals(PatchCommand.BEGIN, parser.directivesRead.get(0).getCommand());
        assertEquals(PatchCommand.PATCH, parser.directivesRead.get(1).getCommand());
        DescriptiveComparator comparator = new DescriptiveComparator();
        //comparator.setLogWhenNotDifferent(true);
        assertTrue(comparator.compare(archive2, "file", patchedArchive, "file"));
        assertEquals(archive2, patchedArchive);
        byte[] binaryArchive2 = toByteArray(archive2);
        byte[] binaryPatchedArchive = toByteArray(patchedArchive);
        assertTrue(Arrays.equals(binaryArchive2, binaryPatchedArchive));
    }

}

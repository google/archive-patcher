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

package com.google.archivepatcher.patcher;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import com.google.archivepatcher.compression.BuiltInCompressionEngine;
import com.google.archivepatcher.compression.Compressor;
import com.google.archivepatcher.compression.DeflateCompressor;

/**
 * Tests for a {@link PatchParser}.
 */
public class PatchParserTest {
    private ByteArrayOutputStream writeBuffer;
    private DataOutput writeOut;
    private PatchWriter writer;
    private PatchTestData td;
    private final static int TEST_DELTA_GENERATOR_ID = 17;

    @Before
    @SuppressWarnings("javadoc")
    public void setUp() throws IOException {
        td = new PatchTestData();
        writeBuffer = new ByteArrayOutputStream();
        writeOut = new DataOutputStream(writeBuffer);
        writer = new PatchWriter(writeOut);
        writer.init();
    }

    private void assertExpected(PatchDirective directive) throws IOException {
        writer.write(directive);
        ByteArrayInputStream bais = new ByteArrayInputStream(
            writeBuffer.toByteArray());
        PatchParser parser = new PatchParser(new DataInputStream(bais));
        parser.init();
        PatchDirective result = parser.read();
        assertEquals(directive, result);
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testNew() throws IOException {
        final NewMetadata part = new NewMetadata(td.lf, null,
            BuiltInCompressionEngine.NONE.getId(), td.fd.getData());
        assertExpected(PatchDirective.NEW(part));
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testNewWithCompressedData() throws IOException {
        Compressor compressor = new DeflateCompressor();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        compressor.compress(new ByteArrayInputStream(td.fd.getData()), compressed);
        final NewMetadata part = new NewMetadata(td.lf, null,
            BuiltInCompressionEngine.DEFLATE.getId(), compressed.toByteArray());
        assertExpected(PatchDirective.NEW(part));
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testCopy() throws IOException {
        assertExpected(PatchDirective.COPY(17));
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testRefresh() throws IOException {
        final RefreshMetadata part = new RefreshMetadata(td.lf, null);
        assertExpected(PatchDirective.REFRESH(1, part));
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testBegin() throws IOException {
        final BeginMetadata part = new BeginMetadata(td.cds);
        assertExpected(PatchDirective.BEGIN(part));
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testPatch() throws IOException {
        final byte[] patchData = "bar".getBytes("UTF-8");
        final PatchMetadata part = new PatchMetadata(
            td.lf,
            null,
            TEST_DELTA_GENERATOR_ID,
            BuiltInCompressionEngine.NONE.getId(),
            patchData);
        assertExpected(PatchDirective.PATCH(1, part));
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testPatchWithCompressedData() throws IOException {
        final byte[] patchData = "bar".getBytes("UTF-8");
        Compressor compressor = new DeflateCompressor();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        compressor.compress(new ByteArrayInputStream(patchData), compressed);

        final PatchMetadata part = new PatchMetadata(
            td.lf,
            null,
            TEST_DELTA_GENERATOR_ID,
            BuiltInCompressionEngine.DEFLATE.getId(),
            patchData);
        assertExpected(PatchDirective.PATCH(1, part));
    }
}
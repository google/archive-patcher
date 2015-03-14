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
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

import com.google.archivepatcher.compression.BuiltInCompressionEngine;
import com.google.archivepatcher.compression.Compressor;
import com.google.archivepatcher.compression.DeflateCompressor;
import com.google.archivepatcher.util.IOUtils;

/**
 * Tests for the {@link PatchWriter} class.
 */
public class PatchWriterTest {

    private ByteArrayOutputStream actualOutBuffer;
    private DataOutput actualOut;
    private PatchWriter pw;
    private ByteArrayOutputStream expectedOutBuffer;
    private DataOutput expectedOut;
    private PatchTestData td;
    private final static int TEST_DELTA_GENERATOR_ID = 17;

    private byte[] getActualData() {
        return actualOutBuffer.toByteArray();
    }

    private byte[] getExpectedData() {
        return expectedOutBuffer.toByteArray();
    }

    private void assertOutputMatch() {
        byte[] expected = getExpectedData(); // declared for debugger's benefit
        byte[] actual = getActualData(); // declared for debugger's benefit
        assertTrue(Arrays.equals(expected, actual));
    }

    @Before
    @SuppressWarnings("javadoc")
    public void setUp() throws Exception {
        td = new PatchTestData();
        actualOutBuffer = new ByteArrayOutputStream();
        actualOut = new DataOutputStream(actualOutBuffer);
        pw = new PatchWriter(actualOut);
        pw.init();
        expectedOutBuffer = new ByteArrayOutputStream();
        expectedOut = new DataOutputStream(expectedOutBuffer);
        IOUtils.writeUTF8(expectedOut, PatchMagic.getStandardHeader());
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testEmptyPatch() {
        assertOutputMatch();
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testCopy() throws IOException {
        assertEquals(5, pw.write(PatchDirective.COPY(1)));
        expectedOut.write(PatchCommand.COPY.signature);
        IOUtils.writeUnsignedInt(expectedOut, 1);
        assertOutputMatch();
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testNew() throws IOException {
        final NewMetadata part = new NewMetadata(td.lf, null,
            BuiltInCompressionEngine.NONE.getId(), td.fd.getData());
        assertEquals(1+4+part.getStructureLength(),
            pw.write(PatchDirective.NEW(part)));
        expectedOut.write(PatchCommand.NEW.signature);
        IOUtils.writeUnsignedInt(expectedOut, part.getStructureLength());
        part.write(expectedOut);
        assertOutputMatch();
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testNewWithCompressedData() throws IOException {
        Compressor compressor = new DeflateCompressor();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        compressor.compress(new ByteArrayInputStream(td.fd.getData()), compressed);
        final NewMetadata part = new NewMetadata(td.lf, null,
            BuiltInCompressionEngine.NONE.getId(), compressed.toByteArray());
        assertEquals(1+4+part.getStructureLength(),
            pw.write(PatchDirective.NEW(part)));
        expectedOut.write(PatchCommand.NEW.signature);
        IOUtils.writeUnsignedInt(expectedOut, part.getStructureLength());
        part.write(expectedOut);
        assertOutputMatch();
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testRefresh() throws IOException {
        final RefreshMetadata part = new RefreshMetadata(td.lf, null);
        assertEquals(1+4+4+part.getStructureLength(),
            pw.write(PatchDirective.REFRESH(1, part)));
        expectedOut.write(PatchCommand.REFRESH.signature);
        IOUtils.writeUnsignedInt(expectedOut, 1);
        IOUtils.writeUnsignedInt(expectedOut, part.getStructureLength());
        part.write(expectedOut);
        assertOutputMatch();
    }
    
    @Test
    @SuppressWarnings("javadoc")
    public void testBegin() throws IOException {
        final BeginMetadata part = new BeginMetadata(td.cds);
        assertEquals(1+4+part.getStructureLength(),
            pw.write(PatchDirective.BEGIN(part)));
        expectedOut.write(PatchCommand.BEGIN.signature);
        IOUtils.writeUnsignedInt(expectedOut, part.getStructureLength());
        part.write(expectedOut);
        assertOutputMatch();
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testPatch() throws IOException {
        final byte[] patchData = "bar".getBytes("UTF-8");
        final PatchMetadata part = new PatchMetadata(td.lf, null,
            TEST_DELTA_GENERATOR_ID, BuiltInCompressionEngine.NONE.getId(),
            patchData);
        assertEquals(1+4+4+part.getStructureLength(),
            pw.write(PatchDirective.PATCH(1, part)));
        expectedOut.write(PatchCommand.PATCH.signature);
        IOUtils.writeUnsignedInt(expectedOut, 1);
        IOUtils.writeUnsignedInt(expectedOut, part.getStructureLength());
        part.write(expectedOut);
        assertOutputMatch();
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testPatchWithCompressedData() throws IOException {
        final byte[] patchData = "bar".getBytes("UTF-8");
        Compressor compressor = new DeflateCompressor();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        compressor.compress(new ByteArrayInputStream(patchData), compressed);
        final PatchMetadata part = new PatchMetadata(td.lf, null,
            TEST_DELTA_GENERATOR_ID, BuiltInCompressionEngine.DEFLATE.getId(),
            patchData);
        assertEquals(1+4+4+part.getStructureLength(),
            pw.write(PatchDirective.PATCH(1, part)));
        expectedOut.write(PatchCommand.PATCH.signature);
        IOUtils.writeUnsignedInt(expectedOut, 1);
        IOUtils.writeUnsignedInt(expectedOut, part.getStructureLength());
        part.write(expectedOut);
        assertOutputMatch();
    }

}
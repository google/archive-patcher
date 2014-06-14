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

import com.google.archivepatcher.patcher.PatchCommand;
import com.google.archivepatcher.patcher.PatchDirective;
import com.google.archivepatcher.patcher.PatchMagic;
import com.google.archivepatcher.patcher.PatchWriter;
import com.google.archivepatcher.util.IOUtils;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class PatchWriterTest {
    private final static BinaryPartForTest PART1 = new BinaryPartForTest("PART1");

    private ByteArrayOutputStream actualOutBuffer;
    private DataOutput actualOut;
    private PatchWriter pw;
    private ByteArrayOutputStream expectedOutBuffer;
    private DataOutput expectedOut;

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
    public void setUp() throws Exception {
        actualOutBuffer = new ByteArrayOutputStream();
        actualOut = new DataOutputStream(actualOutBuffer);
        pw = new PatchWriter(actualOut);
        pw.init();
        expectedOutBuffer = new ByteArrayOutputStream();
        expectedOut = new DataOutputStream(expectedOutBuffer);
        IOUtils.writeUTF8(expectedOut, PatchMagic.getStandardHeader());
    }

    @Test
    public void testEmptyPatch() {
        assertOutputMatch();
    }

    @Test
    public void testCopy() throws IOException {
        assertEquals(5, pw.write(PatchDirective.COPY(1)));
        expectedOut.write(PatchCommand.COPY.binaryFormat);
        IOUtils.writeUnsignedInt(expectedOut, 1);
        assertOutputMatch();
    }

    @Test
    public void testNew() throws IOException {
        assertEquals(1+4+PART1.getStructureLength(), pw.write(PatchDirective.NEW(PART1)));
        expectedOut.write(PatchCommand.NEW.binaryFormat);
        IOUtils.writeUnsignedInt(expectedOut, PART1.content.length);
        expectedOut.write(PART1.content);
        assertOutputMatch();
    }

    @Test
    public void testRefresh() throws IOException {
        assertEquals(1+4+4+PART1.getStructureLength(), pw.write(PatchDirective.REFRESH(1, PART1)));
        expectedOut.write(PatchCommand.REFRESH.binaryFormat);
        IOUtils.writeUnsignedInt(expectedOut, 1);
        IOUtils.writeUnsignedInt(expectedOut, PART1.content.length);
        expectedOut.write(PART1.content);
        assertOutputMatch();
    }
    
    @Test
    public void testEnd() throws IOException {
        assertEquals(1+4+PART1.getStructureLength(), pw.write(PatchDirective.BEGIN(PART1)));
        expectedOut.write(PatchCommand.BEGIN.binaryFormat);
        IOUtils.writeUnsignedInt(expectedOut, PART1.content.length);
        expectedOut.write(PART1.content);
        assertOutputMatch();
    }

    @Test
    public void testPatch() throws IOException {
        // The patch directive's internal format includes the length of the
        // patch. This is necessary so that the PatchMetadata object knows how
        // much data to read from the input stream.
        // Prepend the length here.
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(buffer);
        IOUtils.writeUnsignedInt(dos, PART1.content.length);
        dos.write(PART1.content);
        BinaryPartForTest patchPart = new BinaryPartForTest(buffer.toByteArray());

        // byte mark + offset + part length + patch
        assertEquals(1+4+4+patchPart.getStructureLength(), pw.write(PatchDirective.PATCH(1, patchPart)));
        expectedOut.write(PatchCommand.PATCH.binaryFormat);
        IOUtils.writeUnsignedInt(expectedOut, 1);
        IOUtils.writeUnsignedInt(expectedOut, PART1.content.length + 4);
        IOUtils.writeUnsignedInt(expectedOut, PART1.content.length);
        expectedOut.write(PART1.content);
        assertOutputMatch();
    }
}
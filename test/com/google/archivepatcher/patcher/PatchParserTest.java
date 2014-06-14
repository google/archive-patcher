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

import com.google.archivepatcher.parts.Part;
import com.google.archivepatcher.patcher.PatchCommand;
import com.google.archivepatcher.patcher.PatchDirective;
import com.google.archivepatcher.patcher.PatchParser;
import com.google.archivepatcher.patcher.PatchWriter;
import com.google.archivepatcher.patcher.PatchParser.PartResolver;
import com.google.archivepatcher.util.IOUtils;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

public class PatchParserTest {
    private final static BinaryPartForTest PART1 = new BinaryPartForTest("PART1");
    private ByteArrayOutputStream writeBuffer;
    private DataOutput writeOut;
    private PatchWriter writer;

    @Before
    public void setUp() throws IOException {
        writeBuffer = new ByteArrayOutputStream();
        writeOut = new DataOutputStream(writeBuffer);
        writer = new PatchWriter(writeOut);
        writer.init();
    }

    private static class CommandSpecificPartResolver implements PartResolver {
        private final PatchCommand allowedCommand;
        private final int expectedSize;
        CommandSpecificPartResolver(PatchCommand allowedCommand, int expectedSize) {
            this.allowedCommand = allowedCommand;
            this.expectedSize = expectedSize;
        }
        @Override
        public Part partFor(PatchCommand command) {
            if (command != allowedCommand) {
                throw new IllegalArgumentException(
                        "wrong command type: " + command);
            }
            return new BinaryPartForTest(expectedSize);
        }
        
    }

    private PatchParser getParserForCommand(PatchCommand command,
            final int expectedSize) {
        ByteArrayInputStream bais = new ByteArrayInputStream(
                writeBuffer.toByteArray());
        PartResolver resolver = new CommandSpecificPartResolver(command, expectedSize);
        return new PatchParser(new DataInputStream(bais), resolver);
    }

    private void assertExpected(PatchDirective directive, final int expectedSize) throws IOException {
        writer.write(directive);
        PatchParser parser = getParserForCommand(directive.getCommand(), expectedSize);
        parser.init();
        PatchDirective result = parser.read();
        assertEquals(directive, result);
    }

    @Test
    public void testNew() throws IOException {
        assertExpected(PatchDirective.NEW(PART1), PART1.content.length);
    }

    @Test
    public void testCopy() throws IOException {
        assertExpected(PatchDirective.COPY(17), 0);
    }

    @Test
    public void testRefresh() throws IOException {
        assertExpected(PatchDirective.REFRESH(1, PART1), PART1.content.length);
    }

    @Test
    public void testEnd() throws IOException {
        assertExpected(PatchDirective.BEGIN(PART1), PART1.content.length);
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

        assertExpected(PatchDirective.PATCH(1, patchPart), patchPart.content.length);
    }
}
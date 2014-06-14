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

package com.google.archivepatcher.parts;

import static org.junit.Assert.assertEquals;

import com.google.archivepatcher.parts.DataDescriptor;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

public class DataDescriptorTest {
    private final static int COMPRESSED_SIZE = 120000;
    private final static int CRC32 = 0x72051312;
    private final static int UNCOMPRESSED_SIZE = 634585;

    private final static int EXPECTED_STRUCTURE_LENGTH_WITH_SIG =
            4 + // signature
            4 + // compressed size
            4 + // crc32
            4; // uncompressed size
    private final static int EXPECTED_STRUCTURE_LENGTH_WITHOUT_SIG =
            EXPECTED_STRUCTURE_LENGTH_WITH_SIG - 4;

    private DataDescriptor testObj;

    @Before
    public void setUp() throws Exception {
        testObj = new DataDescriptor();
        testObj.setHasSignature(false);
        testObj.setCompressedSize_32bit(COMPRESSED_SIZE);
        testObj.setCrc32_32bit(CRC32);
        testObj.setUncompressedSize_32bit(UNCOMPRESSED_SIZE);
    }

    private void testInitialization(final boolean hasSignature) {
        assertEquals(hasSignature, testObj.hasSignature());
        assertEquals(COMPRESSED_SIZE, testObj.getCompressedSize_32bit());
        assertEquals(CRC32, testObj.getCrc32_32bit());
        assertEquals(UNCOMPRESSED_SIZE, testObj.getUncompressedSize_32bit());
    }

    @Test
    public void testInitialization() {
        testInitialization(false);
        testObj.setHasSignature(true);
        testInitialization(true);
    }

    @Test
    public void testGetStructureLength() {
        assertEquals(EXPECTED_STRUCTURE_LENGTH_WITHOUT_SIG, testObj.getStructureLength());
        testObj.setHasSignature(true);
        assertEquals(EXPECTED_STRUCTURE_LENGTH_WITH_SIG, testObj.getStructureLength());
    }

    private void testWriteAndRead(final boolean hasSignature) throws Exception {
        final int expectedLength = hasSignature ? EXPECTED_STRUCTURE_LENGTH_WITH_SIG : EXPECTED_STRUCTURE_LENGTH_WITHOUT_SIG;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        testObj.write(dos);
        assertEquals(expectedLength, baos.size());
        byte[] asArray = baos.toByteArray();
        assertEquals(expectedLength, asArray.length);
        ByteArrayInputStream bais = new ByteArrayInputStream(asArray);
        DataInputStream dis = new DataInputStream(bais);
        DataDescriptor read = new DataDescriptor();
        read.read(dis);
        testObj = read;
        testInitialization(hasSignature);
    }

    @Test
    public void testWriteAndRead() throws Exception {
        testWriteAndRead(false);
        testObj.setHasSignature(true);
        testWriteAndRead(true);
    }
}

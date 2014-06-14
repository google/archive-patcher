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
import static org.junit.Assert.assertTrue;

import com.google.archivepatcher.meta.CompressionMethod;
import com.google.archivepatcher.parts.CentralDirectoryFile;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Arrays;

public class CentralDirectoryFileTest {
    private final static int COMPRESSED_SIZE = 440;
    private final static int COMPRESSION_METHOD = CompressionMethod.DEFLATED.value;
    private final static int CRC32 = 0x0BAA0BAA;
    private final static int DISK_NUM_START = 15;
    private final static int EXTERNAL_ATTRS = 0x45;
    private final static byte[] EXTRA_FIELD = new byte[]{0x01, 0x02, 0x03};
    private final static String FILE_NAME = "/some/file";
    private final static String FILE_COMMENT = "its a comment";
    private final static int FLAGS = 1377;
    private final static int INTERNAL_ATTRS = 0x11;
    private final static int LAST_MODIFIED_DATE = 789;
    private final static int LAST_MODIFIED_TIME = 222;
    private final static int RELATIVE_OFFSET = 314159;
    private final static int UNCOMPRESSED_SIZE = 10555;
    private final static int VERSION_MADE_BY = 77;
    private final static int VERSION_TO_EXTRACT = 91;

    private final static int EXPECTED_STRUCTURE_LENGTH =
            4 + // signature
            4 + // compressed size
            2 + // compression method
            4 + // crc32
            2 + // disk number of start
            4 + // external file attributes
            2 + // extra field length (recorded)
            2 + // file comment length (recorded)
            2 + // file name length (recorded)
            2 + // general purpose flags
            2 + // internal file attributes
            2 + // last modified date
            2 + // last modified time
            4 + // relative offset
            4 + // uncompressed size
            2 + // version made by
            2 + // version to extract
            EXTRA_FIELD.length +
            FILE_COMMENT.length() +
            FILE_NAME.length();

    private CentralDirectoryFile testObj;

    @Before
    public void setUp() throws Exception {
        testObj = new CentralDirectoryFile();
        testObj.setCompressedSize_32bit(COMPRESSED_SIZE);
        testObj.setCompressionMethod_16bit(COMPRESSION_METHOD);
        testObj.setCrc32_32bit(CRC32);
        testObj.setDiskNumberStart_16bit(DISK_NUM_START);
        testObj.setExternalFileAttributes_32bit(EXTERNAL_ATTRS);
        testObj.setExtraField(EXTRA_FIELD);
        testObj.setFileComment(FILE_COMMENT);
        testObj.setFileName(FILE_NAME);
        testObj.setGeneralPurposeBitFlag_16bit(FLAGS);
        testObj.setInternalFileAttributes_16bit(INTERNAL_ATTRS);
        testObj.setLastModifiedFileDate_16bit(LAST_MODIFIED_DATE);
        testObj.setLastModifiedFileTime_16bit(LAST_MODIFIED_TIME);
        testObj.setRelativeOffsetOfLocalHeader_32bit(RELATIVE_OFFSET);
        testObj.setUncompressedSize_32bit(UNCOMPRESSED_SIZE);
        testObj.setVersionMadeBy_16bit(VERSION_MADE_BY);
        testObj.setVersionNeededToExtract_16bit(VERSION_TO_EXTRACT);
    }

    @Test
    public void testInitialization() {
        assertEquals(COMPRESSED_SIZE, testObj.getCompressedSize_32bit());
        assertEquals(COMPRESSION_METHOD, testObj.getCompressionMethod_16bit());
        assertEquals(COMPRESSION_METHOD, testObj.getCompressionMethod().value);
        assertEquals(CRC32, testObj.getCrc32_32bit());
        assertEquals(DISK_NUM_START, testObj.getDiskNumberStart_16bit());
        assertEquals(EXTERNAL_ATTRS, testObj.getExternalFileAttributes_32bit());
        assertTrue(Arrays.equals(EXTRA_FIELD, testObj.getExtraField()));
        assertEquals(EXTRA_FIELD.length, testObj.getExtraFieldLength_16bit());
        assertEquals(FILE_COMMENT, testObj.getFileComment());
        assertEquals(FILE_COMMENT.length(), testObj.getFileCommentLength_16bit());
        assertEquals(FILE_NAME, testObj.getFileName());
        assertEquals(FILE_NAME.length(), testObj.getFileNameLength_16bit());
        assertEquals(FLAGS, testObj.getGeneralPurposeBitFlag_16bit());
        assertEquals(INTERNAL_ATTRS, testObj.getInternalFileAttributes_16bit());
        assertEquals(LAST_MODIFIED_DATE, testObj.getLastModifiedFileDate_16bit());
        assertEquals(LAST_MODIFIED_TIME, testObj.getLastModifiedFileTime_16bit());
        assertEquals(RELATIVE_OFFSET, testObj.getRelativeOffsetOfLocalHeader_32bit());
        assertEquals(UNCOMPRESSED_SIZE, testObj.getUncompressedSize_32bit());
        assertEquals(VERSION_MADE_BY, testObj.getVersionMadeBy_16bit());
        assertEquals(VERSION_TO_EXTRACT, testObj.getVersionNeededToExtract_16bit());
    }

    @Test
    public void testGetStructureLength() {
        assertEquals(EXPECTED_STRUCTURE_LENGTH, testObj.getStructureLength());
    }

    @Test
    public void testWriteAndRead() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        testObj.write(dos);
        assertEquals(EXPECTED_STRUCTURE_LENGTH, baos.size());
        byte[] asArray = baos.toByteArray();
        assertEquals(EXPECTED_STRUCTURE_LENGTH, asArray.length);
        ByteArrayInputStream bais = new ByteArrayInputStream(asArray);
        DataInputStream dis = new DataInputStream(bais);
        CentralDirectoryFile read = new CentralDirectoryFile();
        read.read(dis);
        testObj = read;
        testInitialization();
    }

}

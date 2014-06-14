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

import static org.junit.Assert.*;

import com.google.archivepatcher.parts.EndOfCentralDirectory;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

public class EndOfCentralDirectoryTest {
    private final static int DISK_NUM = 33;
    private final static int DISK_NUM_OF_CD_START = 44;
    private final static int LENGTH_OF_CD = 55;
    private final static int NUM_ENTRIES_IN_CD = 77;
    private final static int NUM_ENTRIES_IN_CD_THIS_DISK = 66;
    private final static int OFFSET_OF_CD = 88;
    private final static String COMMENT = "happy comment";
    private final static int EXPECTED_STRUCTURE_LENGTH =
            4 + // signature
            2 + // disk num
            2 + // disk num of CD start
            4 + // length of CD
            2 + // num entries in CD
            2 + // num entries in CD this disk
            4 + // offset of CD
            2 + // comment length
            COMMENT.length();

    private EndOfCentralDirectory testObj;

    @Before
    public void setUp() {
        testObj = new EndOfCentralDirectory();
        testObj.setDiskNumber_16bit(DISK_NUM);
        testObj.setDiskNumberOfStartOfCentralDirectory_16bit(DISK_NUM_OF_CD_START);
        testObj.setLengthOfCentralDirectory_32bit(LENGTH_OF_CD);
        testObj.setNumEntriesInCentralDir_16bit(NUM_ENTRIES_IN_CD);
        testObj.setNumEntriesInCentralDirThisDisk_16bit(NUM_ENTRIES_IN_CD_THIS_DISK);
        testObj.setOffsetOfStartOfCentralDirectoryRelativeToDisk_32bit(OFFSET_OF_CD);
        testObj.setZipFileComment(COMMENT);
    }

    @Test
    public void testInitialization() {
        assertEquals(DISK_NUM, testObj.getDiskNumber_16bit());
        assertEquals(DISK_NUM_OF_CD_START, testObj.getDiskNumberOfStartOfCentralDirectory_16bit());
        assertEquals(LENGTH_OF_CD, testObj.getLengthOfCentralDirectory_32bit());
        assertEquals(NUM_ENTRIES_IN_CD, testObj.getNumEntriesInCentralDir_16bit());
        assertEquals(NUM_ENTRIES_IN_CD_THIS_DISK, testObj.getNumEntriesInCentralDirThisDisk_16bit());
        assertEquals(OFFSET_OF_CD, testObj.getOffsetOfStartOfCentralDirectoryRelativeToDisk_32bit());
        assertEquals(COMMENT, testObj.getZipFileComment());
        assertEquals(COMMENT.length(), testObj.getZipFileCommentLength_16bit());
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
        EndOfCentralDirectory read = new EndOfCentralDirectory();
        read.read(dis);
        testObj = read;
        testInitialization();
    }
}
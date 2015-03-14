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

package com.google.archivepatcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.Before;
import org.junit.Test;

import com.google.archivepatcher.parts.EndOfCentralDirectory;
import com.google.archivepatcher.util.SimpleArchive;

/**
 * Tests for the {@link Archive} class.
 */
public class ArchiveTest {
    private final static String PATH1 = "file1.txt";
    private final static String PATH2 = "file2.txt";
    private final static String CONTENT_STRING1 = "file1 content: this is a test string long enough to be deflated its totally rad really super cool i love it";
    private final static String CONTENT_STRING2 = "file2 content: this is another string long enough to be deflated its totally rad really super cool i love it";
    private final static long LAST_MODIFIED_MILLIS = 1418418038000L; // 2014-12-12 at 21:00:38 GMT

    private InputStream contentIn1;
    private InputStream contentIn2;

    @Before
    @SuppressWarnings("javadoc")
    public void setUp() throws Exception {
        byte[] content1 = CONTENT_STRING1.getBytes(Charset.forName("UTF8"));
        contentIn1 = new ByteArrayInputStream(content1);

        byte[] content2 = CONTENT_STRING2.getBytes(Charset.forName("UTF8"));
        contentIn2 = new ByteArrayInputStream(content2);
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testWriteArchive_1File() throws IOException {
        final String ARCHIVE = "test/UNITTEST_1File.zip";
        SimpleArchive archive = new SimpleArchive();
        archive.add(PATH1, LAST_MODIFIED_MILLIS, contentIn1, true);
        FileOutputStream out = new FileOutputStream(ARCHIVE);
        archive.writeArchive(out);
        out.close();

        Archive readArchive = Archive.fromFile(ARCHIVE);
        EndOfCentralDirectory eocd =
            readArchive.getCentralDirectory().getEocd();
        assertEquals(1, eocd.getNumEntriesInCentralDir_16bit());
        assertEquals(1, eocd.getNumEntriesInCentralDirThisDisk_16bit());
        // TODO: Should these be zero or one? Is it zero-based or one-based?
        assertEquals(0, eocd.getDiskNumber_16bit());
        assertEquals(0, eocd.getDiskNumberOfStartOfCentralDirectory_16bit());
        assertEquals(0, eocd.getZipFileCommentLength_16bit());

        // Verification phase
        ZipFile written = new ZipFile(ARCHIVE);
        @SuppressWarnings("unchecked")
        Enumeration<ZipEntry> entries = (Enumeration<ZipEntry>) written.entries();
        ZipEntry entry = entries.nextElement();
        assertFalse(entries.hasMoreElements());
        assertEquals(PATH1, entry.getName());
        assertFalse(entry.isDirectory());
        assertContentEquivalence(written, entry, CONTENT_STRING1);
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testWriteArchive_2Files() throws IOException {
        final String ARCHIVE = "test/UNITTEST_2Files.zip";
        SimpleArchive archive = new SimpleArchive();
        archive.add(PATH1, LAST_MODIFIED_MILLIS, contentIn1, true);
        archive.add(PATH2, LAST_MODIFIED_MILLIS, contentIn2, true);
        FileOutputStream out = new FileOutputStream(ARCHIVE);
        archive.writeArchive(out);
        out.close();

        // Verification phase
        ZipFile written = new ZipFile(ARCHIVE);
        @SuppressWarnings("unchecked")
        Enumeration<ZipEntry> entries = (Enumeration<ZipEntry>) written.entries();
        ZipEntry entry1 = entries.nextElement();
        ZipEntry entry2 = entries.nextElement();
        assertFalse(entries.hasMoreElements());
        assertEquals(PATH1, entry1.getName());
        assertEquals(PATH2, entry2.getName());
        assertFalse(entry1.isDirectory());
        assertFalse(entry2.isDirectory());
        assertContentEquivalence(written, entry1, CONTENT_STRING1);
        assertContentEquivalence(written, entry2, CONTENT_STRING2);
    }

    /**
     * @param written
     * @param entry1
     * @param expectedString
     * @throws IOException
     */
    private void assertContentEquivalence(ZipFile written, ZipEntry entry1,
            final String expectedString) throws IOException {
        InputStream writtenIn = written.getInputStream(entry1);
        byte[] buffer = new byte[4096];
        int numRead = 0;
        String result = "";
        while ( (numRead = writtenIn.read(buffer)) != -1 ) {
            result += new String(buffer, 0, numRead, Charset.forName("UTF8"));
        }
        assertEquals(result, expectedString);
    }

}
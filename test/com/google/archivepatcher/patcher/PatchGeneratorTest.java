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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.google.archivepatcher.Archive;
import com.google.archivepatcher.PatchApplier;
import com.google.archivepatcher.PatchGenerator;
import com.google.archivepatcher.compression.BuiltInCompressionEngine;
import com.google.archivepatcher.compression.Compressor;
import com.google.archivepatcher.compression.DeflateCompressor;
import com.google.archivepatcher.delta.BsDiffDeltaGenerator;
import com.google.archivepatcher.delta.DeltaGenerator;
import com.google.archivepatcher.delta.JxdDeltaGenerator;
import com.google.archivepatcher.parts.CentralDirectoryFile;
import com.google.archivepatcher.parts.LocalFile;
import com.google.archivepatcher.parts.LocalSectionParts;
import com.google.archivepatcher.reporting.PatchGenerationReport;
import com.google.archivepatcher.testutil.TestFile;
import com.google.archivepatcher.util.MsDosDate;
import com.google.archivepatcher.util.MsDosTime;
import com.google.archivepatcher.util.SimpleArchive;

/**
 * Tests for a {@link PatchGenerator}.
 */
public class PatchGeneratorTest {
    private final static long NOWISH = System.currentTimeMillis();
    @SuppressWarnings("javadoc")
    public final static String FILE1 = "file1";
    @SuppressWarnings("javadoc")
    public final static String FILE2 = "file2";
    @SuppressWarnings("javadoc")
    public final static String FILE3 = "file3";
    @SuppressWarnings("javadoc")
    public final static String FILE4 = "file4";

    @SuppressWarnings("javadoc")
    public final static byte[] CONTENT1 = "Have you ever considered an archive-aware patch system? Such a system could conceivably be much more efficient than vanilla diff.".getBytes(Charset.forName("UTF8"));
    @SuppressWarnings("javadoc")
    public final static byte[] CONTENT2 = "Effectively, such a tool determines the differences between entries in an archive. Each entry is considered on its own.".getBytes(Charset.forName("UTF8"));
    @SuppressWarnings("javadoc")
    public final static byte[] CONTENT3 = "In theory, this can lead to a very efficient binary patcher. By comparing data and metadata separately, such a patcher can even mitigate the effects of non-data changes in archives".getBytes(Charset.forName("UTF8"));
    @SuppressWarnings("javadoc")
    public final static byte[] CONTENT4 = "By comparing CRC-32s, the patcher can even efficiently find possible candidates for renames and duplicate data within an archive, allowing a patch to capture renames and copies with very little cost.".getBytes(Charset.forName("UTF8"));

    @SuppressWarnings("javadoc")
    public final static TestFile TF1 = new TestFile(FILE1, CONTENT1);
    @SuppressWarnings("javadoc")
    public final static TestFile TF2 = new TestFile(FILE2, CONTENT2);
    @SuppressWarnings("javadoc")
    public final static TestFile TF3 = new TestFile(FILE3, CONTENT3);
    @SuppressWarnings("javadoc")
    public final static TestFile TF4 = new TestFile(FILE4, CONTENT4);

    // it's like a macro!
    @SuppressWarnings("javadoc")
    public static TestFile tf(String file, byte[] content) {
        return new TestFile(file, content);
    }

    @SuppressWarnings("javadoc")
    public static Archive makeArchive(TestFile... files) throws IOException {
        SimpleArchive archive = new SimpleArchive();
        for (TestFile tf : files) {
            archive.add(tf.file, NOWISH, tf.getInputStream(), true);
        }
        archive.finishArchive();
        return archive;
    }

    private Archive oldArchive;
    private Archive newArchive;
    private ByteArrayOutputStream outBuffer;
    private List<PatchDirective> expectedDirectives;

    @Before
    @SuppressWarnings("javadoc")
    public void setUp() {
        expectedDirectives = new LinkedList<PatchDirective>();
        oldArchive = null;
        newArchive = null;
        outBuffer = null;
    }

    @Deprecated
    private PatchGenerationReport generatePatchWithoutDeltaOrCompression() throws IOException {
        // TODO: Generate with deltas and compression instead
        return generatePatch(null, null);
    }

    private PatchGenerationReport generatePatch(
        List<DeltaGenerator> deltaGenerators, List<Compressor> compressors)
            throws IOException {
        outBuffer = new ByteArrayOutputStream();
        DataOutput dataOut = new DataOutputStream(outBuffer);
        PatchGenerator generator = new PatchGenerator(
            oldArchive, newArchive, dataOut, deltaGenerators, compressors);
        generator.init();
        return generator.generateAll();
    }

    private List<PatchDirective> getDirectives() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(outBuffer.toByteArray());
        PatchParser parser = new PatchParser(new DataInputStream(in));
        parser.init();
        return parser.readAll();
    }

    private void expectDirective(PatchDirective directive) {
        expectedDirectives.add(directive);
    }

    private void assertDirectivesMatch() throws IOException {
        BeginMetadata metadata = new BeginMetadata();
        metadata.setCd(newArchive.getCentralDirectory());
        expectedDirectives.add(0, PatchDirective.BEGIN(metadata));
        // We could do a blanket assertEquals but it's a pain to debug.
        List<PatchDirective> actualList = getDirectives();
        Iterator<PatchDirective> expected = expectedDirectives.iterator();
        Iterator<PatchDirective> actual = actualList.iterator();
        int count = 0;
        while (expected.hasNext() && actual.hasNext()) {
            count++;
            assertEquals("Error at directive " + count, expected.next(), actual.next());
        }
        if (expected.hasNext() || actual.hasNext()) {
            fail("expected " + expectedDirectives.size() + " directives, but have " + actualList.size());
        }
        
        // Apply the patch... whoaaaaaaa
        // We do a double-barrel test here:
        // 1. Test object equality - ensure that LOGICAL structure is identical
        // 2. Test binary equality - ensure that DISK structure is identical
        ByteArrayOutputStream binaryExpected = new ByteArrayOutputStream();
        newArchive.writeArchive(binaryExpected);
        ByteArrayInputStream in = new ByteArrayInputStream(outBuffer.toByteArray());
        PatchParser parser = new PatchParser(new DataInputStream(in));
        PatchApplier applier = new PatchApplier(oldArchive, parser, null, null);
        Archive transformed = applier.applyPatch();
        // LOGICAL test
        assertEquals(newArchive, transformed);
        ByteArrayOutputStream binaryProduced = new ByteArrayOutputStream();
        transformed.writeArchive(binaryProduced);
        // DISK test - protects against spurious logical object equivalence
        assertTrue(Arrays.equals(binaryExpected.toByteArray(), binaryProduced.toByteArray()));
    }

    private final static void twiddleTime(Archive archive, TestFile file, int offset) {
        if (Math.abs(offset) < 2000) throw new IllegalArgumentException("granularity too fine for MS DOS");
        final long newMillis = NOWISH + offset;
        final int newDate = MsDosDate.fromMillisecondsSinceEpoch(newMillis).to16BitPackedValue();
        final int newTime = MsDosTime.fromMillisecondsSinceEpoch(newMillis).to16BitPackedValue();
        CentralDirectoryFile cdf = archive.getCentralDirectory().getByPath(file.file);
        cdf.setLastModifiedFileDate_16bit(newDate);
        cdf.setLastModifiedFileTime_16bit(newTime);
        LocalFile lf = archive.getLocal().getByPath(file.file).getLocalFilePart();
        lf.setLastModifiedFileDate_16bit(newDate);
        lf.setLastModifiedFileTime_16bit(newTime);
    }

    // WARNING: This breaks the data in a way that makes it impossible to
    // extract the archive: the CRC is arbitrarily mangled, as is the data
    // block.
    private final static void twiddleData(Archive archive, TestFile file) {
        CentralDirectoryFile cdf = archive.getCentralDirectory().getByPath(file.file);
        cdf.setCrc32_32bit(~cdf.getCrc32_32bit());
        LocalSectionParts local = archive.getLocal().getByPath(file.file);
        if (local.hasDataDescriptor()) {
            local.getDataDescriptorPart().setCrc32_32bit(
                    ~local.getDataDescriptorPart().getCrc32_32bit());
        } else {
            local.getLocalFilePart().setCrc32_32bit(
                    ~local.getLocalFilePart().getCrc32_32bit());
        }
        byte[] data = local.getFileDataPart().getData();
        data[0] = (byte) ~data[0]; // flip bits
    }

    private static NewMetadata metadataForNew(Archive archive, TestFile file) {
        LocalSectionParts alp = archive.getLocal().getByPath(file.file);
        // TODO: Also test compression engines here
        return new NewMetadata(alp.getLocalFilePart(), alp.getDataDescriptorPart(),
            BuiltInCompressionEngine.NONE.getId(), alp.getFileDataPart().getData());
    }

    private static RefreshMetadata metadataForRefresh(Archive archive, TestFile file) {
        LocalSectionParts alp = archive.getLocal().getByPath(file.file);
        return new RefreshMetadata(alp.getLocalFilePart(), alp.getDataDescriptorPart());
    }

    private static int offsetForCopy(Archive archive, TestFile file) {
        return (int) archive.getCentralDirectory().getByPath(file.file)
                .getRelativeOffsetOfLocalHeader_32bit();
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testCopyOne() throws Exception {
        oldArchive = makeArchive(TF1);
        newArchive = makeArchive(TF1);
        assertEquals(oldArchive, newArchive);
        generatePatchWithoutDeltaOrCompression();
        expectDirective(PatchDirective.COPY(0));
        assertDirectivesMatch();
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testReplaceOne() throws Exception {
        oldArchive = makeArchive(TF1);
        newArchive = makeArchive(TF2);
        generatePatchWithoutDeltaOrCompression();
        expectDirective(PatchDirective.NEW(metadataForNew(newArchive, TF2)));
        assertDirectivesMatch();
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testDeleteFirst() throws Exception {
        oldArchive = makeArchive(TF1,TF2,TF3);
        newArchive = makeArchive(TF2,TF3); // missing FIRST entry
        generatePatchWithoutDeltaOrCompression();
        expectDirective(PatchDirective.COPY(offsetForCopy(oldArchive, TF2)));
        expectDirective(PatchDirective.COPY(offsetForCopy(oldArchive, TF3)));
        assertDirectivesMatch();
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testDeleteMiddle() throws Exception {
        oldArchive = makeArchive(TF1,TF2,TF3);
        newArchive = makeArchive(TF1,TF3); // missing MIDDLE entry
        generatePatchWithoutDeltaOrCompression();
        expectDirective(PatchDirective.COPY(offsetForCopy(oldArchive, TF1)));
        expectDirective(PatchDirective.COPY(offsetForCopy(oldArchive, TF3)));
        assertDirectivesMatch();
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testDeleteLast() throws Exception {
        oldArchive = makeArchive(TF1,TF2,TF3);
        newArchive = makeArchive(TF1,TF2); // missing LAST entry
        generatePatchWithoutDeltaOrCompression();
        expectDirective(PatchDirective.COPY(offsetForCopy(oldArchive, TF1)));
        expectDirective(PatchDirective.COPY(offsetForCopy(oldArchive, TF2)));
        assertDirectivesMatch();
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testCopyOutOfOrder() throws Exception {
        oldArchive = makeArchive(TF1,TF2,TF3,TF4);
        newArchive = makeArchive(TF4,TF3,TF2,TF1);
        generatePatchWithoutDeltaOrCompression();
        expectDirective(PatchDirective.COPY(offsetForCopy(oldArchive, TF4)));
        expectDirective(PatchDirective.COPY(offsetForCopy(oldArchive, TF3)));
        expectDirective(PatchDirective.COPY(offsetForCopy(oldArchive, TF2)));
        expectDirective(PatchDirective.COPY(offsetForCopy(oldArchive, TF1)));
        assertDirectivesMatch();
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testRefreshOne() throws Exception {
        oldArchive = makeArchive(TF1);
        newArchive = makeArchive(TF1);
        twiddleTime(newArchive, TF1, 5000); // Make TF1 newer, but keep same data
        generatePatchWithoutDeltaOrCompression();
        expectDirective(PatchDirective.REFRESH(
                offsetForCopy(oldArchive, TF1),
                metadataForRefresh(newArchive, TF1)));
        assertDirectivesMatch();
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testAppend() throws Exception {
        oldArchive = makeArchive(TF1);
        newArchive = makeArchive(TF1,TF2);
        generatePatchWithoutDeltaOrCompression();
        expectDirective(PatchDirective.COPY(offsetForCopy(oldArchive, TF1)));
        expectDirective(PatchDirective.NEW(metadataForNew(newArchive, TF2)));
        assertDirectivesMatch();
    }

    private void prepareMixtureTest() throws Exception {
        oldArchive = makeArchive(TF1, TF2, TF3);
        newArchive = makeArchive(TF2, TF3, TF4);
        twiddleTime(newArchive, TF2, 5000); // Make TF2 newer, but keep same data
        twiddleData(newArchive, TF3); // Make TF3 have updated data
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testMixture() throws Exception {
        prepareMixtureTest();
        generatePatchWithoutDeltaOrCompression();
        // Deleted: TF1; 
        // Added: TF4 (NEW)
        // New metadata: TF2 (REFRESH)
        // New content: TF3 (NEW)
        expectDirective(PatchDirective.REFRESH(
                offsetForCopy(oldArchive, TF2),
                metadataForRefresh(newArchive, TF2)));
        expectDirective(PatchDirective.NEW(metadataForNew(newArchive, TF3)));
        expectDirective(PatchDirective.NEW(metadataForNew(newArchive, TF4)));
        assertDirectivesMatch();
    }

    @Test
    @SuppressWarnings("javadoc")
    public void testReportGeneration() throws Exception {
        prepareMixtureTest();
        List<DeltaGenerator> deltaGenerators = new ArrayList<DeltaGenerator>();
        deltaGenerators.add(new JxdDeltaGenerator());
        deltaGenerators.add(new BsDiffDeltaGenerator());
        List<Compressor> compressors = new ArrayList<Compressor>();
        compressors.add(new DeflateCompressor());
        PatchGenerationReport report = generatePatch(deltaGenerators, compressors);
        assertNotNull(report);
        String textReport = report.toString();
        assertNotNull(textReport);
        assertFalse(textReport.isEmpty());
        String csvReport = report.toCsv();
        assertNotNull(csvReport);
        assertFalse(csvReport.isEmpty());
        System.out.println("Test Report (Text):");
        System.out.println(report);
        System.out.println("Test Report (CSV):");
        System.out.println(report.toCsv());
    }

}
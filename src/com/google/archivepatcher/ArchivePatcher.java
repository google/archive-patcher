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

import com.google.archivepatcher.parts.CentralDirectoryFile;
import com.google.archivepatcher.parts.CentralDirectorySection;
import com.google.archivepatcher.patcher.BeginMetadata;
import com.google.archivepatcher.patcher.NewMetadata;
import com.google.archivepatcher.patcher.PatchDirective;
import com.google.archivepatcher.patcher.PatchMetadata;
import com.google.archivepatcher.patcher.PatchParser;
import com.google.archivepatcher.patcher.RefreshMetadata;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ArchivePatcher extends AbstractArchiveTool {

    public final static void main(String... args) throws Exception {
        new ArchivePatcher().run(args);
    }

    @Override
    protected void configureOptions(MicroOptions options) {
        super.configureOptions(options);
        options.option("makepatch").isUnary()
            .describedAs("generate a patch to transform --old into --new");
        options.option("applypatch").isUnary()
            .describedAs("apply a patch file to --old, creating --new");
        options.option("explain").isUnary()
            .describedAs("explain a patch in the context of --old and --new");
        options.option("old").isRequired()
            .describedAs("the old archive to read from");
        options.option("new").isRequired()
            .describedAs("the archive that --old is being transformed into");
        options.option("patch").isRequired()
            .describedAs("the path to the patchfile to create or apply");
        options.option("deltaclass")
            .describedAs("with --makepatch, the name of a class to use to " +
                    "generate deltas; with --applypatch, the name of a " +
                    "class to use to apply deltas; otherwise, unused. " +
                    "Interfaces: " + DeltaGenerator.class.getName() +", " +
                    DeltaApplier.class.getName());
    }

    @SuppressWarnings("unchecked")
    @Override
    protected final void run(MicroOptions options) throws Exception {
        if (options.has("explain")) {
            explain(options.getArg("old"),
                    options.getArg("new"),
                    options.getArg("patch"));
        } else if (options.has("makepatch")) {
            DeltaGenerator deltaGenerator = maybeCreateInstance(
                    options.getArg("deltaclass", null),
                    DeltaGenerator.class);
            makePatch(options.getArg("old"),
                    options.getArg("new"),
                    options.getArg("patch"),
                    deltaGenerator);
        } else if (options.has("applypatch")) {
            DeltaApplier deltaApplier = maybeCreateInstance(
                    options.getArg("deltaclass", null),
                    DeltaApplier.class);
            applyPatch(options.getArg("old"),
                    options.getArg("new"),
                    options.getArg("patch"),
                    deltaApplier);
        } else {
            throw new MicroOptions.OptionException(
                    "Must specify --makepatch, --applypatch or --explain");
        }
    }

    private static Class<?> loadRequiredClass(final String className,
            final Class<?> requiredInterface)
                    throws MicroOptions.OptionException {
        try {
            Class<?> result = Class.forName(className);
            if (!requiredInterface.isAssignableFrom(result)) {
                throw new MicroOptions.OptionException(
                        "class doesn't implement " +
                        requiredInterface.getName());
            }
            return result;
        } catch (ClassNotFoundException e) {
            MicroOptions.OptionException error =
                    new MicroOptions.OptionException(
                            "failed to load class: " + className);
            error.initCause(e);
            throw error;
        }
    }

    @SuppressWarnings("unchecked") // enforced by loadrequiredClass.
    private static <T> T createRequiredInstance(final String className,
            final Class<T> requiredInterface)
            throws MicroOptions.OptionException {
        Class<?> clazz = loadRequiredClass(className, requiredInterface);
        try {
            return (T) clazz.newInstance();
        } catch (Exception e) {
            MicroOptions.OptionException error =
                    new MicroOptions.OptionException(
                            "failed to initialize class: " + className);
            error.initCause(e);
            throw error;
        }
    }

    private static <T> T maybeCreateInstance(final String className,
            final Class<T> requiredInterface)
            throws MicroOptions.OptionException {
        if (className == null) return null;
        return createRequiredInstance(className, requiredInterface);
    }

    public void makePatch(String oldFile, String newFile, String patchFile, DeltaGenerator deltaGenerator) throws IOException {
        if (isVerbose()) {
            log("generating patch");
            log("  old:             " + oldFile);
            log("  new:             " + newFile);
            log("  patch:           " + patchFile);
            log("  delta generator: " + (deltaGenerator == null ?
                    "none" : deltaGenerator.getClass().getName()));
        }
        FileOutputStream out = new FileOutputStream(patchFile);
        DataOutputStream dos = new DataOutputStream(out);
        PatchGenerator generator = new PatchGenerator(oldFile, newFile, dos, deltaGenerator);
        generator.generatePatch();
        dos.flush();
        out.flush();
        out.close();
    }

    public void applyPatch(String oldFile, String newFile, String patchFile, DeltaApplier deltaApplier) throws IOException {
        if (isVerbose()) {
            log("applying patch");
            log("  old:   " + oldFile);
            log("  new:   " + newFile);
            log("  patch: " + patchFile);
        }
        PatchApplier applier = new PatchApplier(oldFile, patchFile, deltaApplier);
        Archive result = applier.applyPatch();
        FileOutputStream out = new FileOutputStream(newFile);
        result.writeArchive(out);
        out.flush();
        out.close();
    }

    public void explain(String oldFile, String newFile, String patchFilePath) throws IOException {
        if (isVerbose()) {
            log("explain patch");
            log("  old:   " + oldFile);
            log("  new:   " + newFile);
            log("  patch: " + patchFilePath);
        }
        Archive oldArchive = Archive.fromFile(oldFile);
        final Map<Integer, CentralDirectoryFile> oldCDFByOffset =
                new HashMap<Integer, CentralDirectoryFile>();
        for (CentralDirectoryFile entry : oldArchive.getCentralDirectory().entries()) {
            oldCDFByOffset.put((int) entry.getRelativeOffsetOfLocalHeader_32bit(), entry);
        }

        Archive newArchive = Archive.fromFile(newFile);
        CentralDirectorySection patchCd = null;
        File patchFile = new File(patchFilePath);
        PatchParser parser = new PatchParser(patchFile);
        parser.init();
        int numCopy = 0;
        int sizeOfCopyRecords = 0; // the records themselves, price paid in patch
        int sizeOfDataSavedByCopy = 0; // the compressed data we avoided having to copy
        int numNew = 0;
        int sizeOfNewRecords = 0; // the records themselves, price paid in patch
        int sizeOfDataInNew = 0; // the compressed data we had to insert
        int numRefresh = 0;
        int sizeOfRefreshRecords = 0; // the records themselves, price paid in patch
        int sizeOfDataSavedByRefresh = 0; // the compressed data we avoided having to copy
        int numPatch = 0;
        int sizeOfPatchRecords = 0; // the records themselves, price paid in patch
        int sizeOfDataSavedByPatch = 0; // the size reduction from using delta instead of copy
        NumberFormat pretty = NumberFormat.getNumberInstance(Locale.US);
        PatchDirective directive = null;
        while( (directive = parser.read()) != null) {
            switch(directive.getCommand()) {
                case BEGIN:
                    patchCd = ((BeginMetadata) directive.getPart()).getCd();
                    break;
                case COPY:
                    numCopy++;
                    final CentralDirectoryFile copiedCdf = oldCDFByOffset.get(directive.getOffset());
                    final int copiedSavings = (int) copiedCdf.getCompressedSize_32bit();
                    if (isVerbose()) {
                        log("COPY " + copiedCdf.getFileName());
                        log("  cost:    5 bytes");
                        log("  savings: " + pretty.format(copiedSavings) + " bytes");
                    }
                    sizeOfCopyRecords += 1 + 4; // type + offset
                    sizeOfDataSavedByCopy += copiedSavings;
                    break;
                case PATCH:
                    numPatch++;
                    final CentralDirectoryFile patchedCdf =
                            newArchive.getCentralDirectory().getByPath(
                                    oldCDFByOffset.get(directive.getOffset())
                                    .getFileName());
                    final PatchMetadata patchMetadata = (PatchMetadata) directive.getPart();
                    final int sizeOfPatchRecord = 1 + patchMetadata.getStructureLength();
                    final int patchSavings = (int) patchedCdf.getCompressedSize_32bit(); // [cost - savings = delta versus new]
                    if (isVerbose()) {
                        log("PATCH " + patchedCdf.getFileName());
                        log("  cost:    " + pretty.format(sizeOfPatchRecord) + " bytes");
                        log("  savings: " + pretty.format(patchSavings) + " bytes");
                    }
                    sizeOfPatchRecords += sizeOfPatchRecord;
                    sizeOfDataSavedByPatch += patchSavings;
                    break;
                case NEW:
                    numNew++;
                    final NewMetadata newMetadata = (NewMetadata) directive.getPart();
                    final int sizeOfNewRecord = 1 + newMetadata.getStructureLength();
                    final int sizeOfNewData = newMetadata.getFileDataPart().getStructureLength();
                    if (isVerbose()) {
                        log ("NEW " + newMetadata.getLocalFilePart().getFileName());
                        log ("  cost:    " + pretty.format(sizeOfNewRecord) + " bytes (of which " + pretty.format(sizeOfNewData) + " was compressed data)");
                    }
                    sizeOfNewRecords += sizeOfNewRecord;
                    sizeOfDataInNew += sizeOfNewData;
                    break;
                case REFRESH:
                    numRefresh++;
                    final CentralDirectoryFile refreshedCdf = oldCDFByOffset.get(directive.getOffset());
                    final RefreshMetadata refreshMetadata = (RefreshMetadata) directive.getPart();
                    final int sizeOfRefreshRecord = 1 + refreshMetadata.getStructureLength();
                    final int refreshSavings = (int) refreshedCdf.getCompressedSize_32bit();
                    if (isVerbose()) {
                        log("REFRESH " + refreshedCdf.getFileName());
                        log("  cost:    " + pretty.format(sizeOfRefreshRecord) + " bytes");
                        log("  savings: " + pretty.format(refreshSavings) + " bytes");
                    }
                    sizeOfRefreshRecords += sizeOfRefreshRecord;
                    sizeOfDataSavedByRefresh += refreshSavings;
                    break;
            }
        }

        // Output stats
        final int patchSize = (int) patchFile.length();
        final List<CentralDirectoryFile> patchCdEntries = patchCd.entries();
        int patchCdSize = 0;
        int numPatchCdEntries = 0;
        for (CentralDirectoryFile patchCdEntry : patchCdEntries) {
            patchCdSize += patchCdEntry.getStructureLength();
            numPatchCdEntries++;
        }
        log("Patch size      : " + pretty.format(patchSize) + " bytes");
        log("Patch EOCD size : " + pretty.format(patchCd.getEocd().getStructureLength()) + " bytes");
        log("Patch CD size   : " + pretty.format(patchCdSize) + " bytes");
        log("Patch CD entries: " + pretty.format(numPatchCdEntries));
        log("Totals:");
        final int averageSizeOfCopyRecord = numCopy == 0 ? 0 : sizeOfCopyRecords / numCopy;
        final int averageSizeOfDataSavedByCopy = numCopy == 0 ? 0 : sizeOfDataSavedByCopy / numCopy;
        log("COPY (" + pretty.format(numCopy) + " entries):");
        log("  cost:    " + pretty.format(sizeOfCopyRecords) + " bytes (average " + pretty.format(averageSizeOfCopyRecord) + ")");
        log("  savings: " + pretty.format(sizeOfDataSavedByCopy) + " bytes (average " + pretty.format(averageSizeOfDataSavedByCopy) + ")");
        final int averageSizeOfRefreshRecord = numRefresh == 0 ? 0 : sizeOfRefreshRecords / numRefresh;
        final int averageSizeOfDataSavedByRefresh = numRefresh == 0 ? 0 : sizeOfDataSavedByRefresh / numRefresh;
        log("REFRESH (" + pretty.format(numRefresh) + " entries):");
        log("  cost:    " + pretty.format(sizeOfRefreshRecords) + " bytes (average " + pretty.format(averageSizeOfRefreshRecord) + ")");
        log("  savings: " + pretty.format(sizeOfDataSavedByRefresh) + " bytes (average " + pretty.format(averageSizeOfDataSavedByRefresh) + ")");
        final int averageSizeOfPatchRecord = numPatch == 0 ? 0 : sizeOfPatchRecords / numPatch;
        final int averageSizeOfDataSavedByPatch = numPatch == 0 ? 0 : sizeOfDataSavedByPatch / numPatch;
        log("PATCH (" + pretty.format(numPatch) + " entries):");
        log("  cost:    " + pretty.format(sizeOfPatchRecords) + " bytes (average " + pretty.format(averageSizeOfPatchRecord) + ")");
        log("  savings: " + pretty.format(sizeOfDataSavedByPatch) + " bytes (average " + pretty.format(averageSizeOfDataSavedByPatch) + ")");
        log("NEW (" + numNew + " entries):");
        log("  cost:    " + pretty.format(sizeOfNewRecords) + " bytes (of which " + pretty.format(sizeOfDataInNew) + " was compressed data)");
    }

}

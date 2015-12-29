// Copyright 2015 Google Inc. All rights reserved.
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

package com.google.archivepatcher.reporting;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.archivepatcher.compression.BuiltInCompressionEngine;
import com.google.archivepatcher.patcher.PatchCommand;
import com.google.archivepatcher.patcher.PatchDirective;

/**
 * Records statistics and information gathered during patch generation.
 */
public class PatchGenerationReport {
    private final int oldArchiveSizeBytes;
    private final int newArchiveSizeBytes;
    private int totalPatchSizeBytes = 0;
    private final List<PatchGenerationReportEntry> perFilePatchEntries =
        new ArrayList<PatchGenerationReportEntry>();
    private PatchGenerationReportEntry wholeArchivePatchEntry = null;
    private PatchGenerationReportEntry beginPatchEntry = null;
    private boolean wholeArchivePatchUsed = false;
    private NumberFormat decimalFormat = null;

    /**
     * Constructs a new report.
     * @param oldArchiveSizeBytes the size of the old archive, in bytes
     * @param newArchiveSizeBytes the size of the new archive, in bytes
     */
    public PatchGenerationReport(int oldArchiveSizeBytes,
        int newArchiveSizeBytes) {
        this.oldArchiveSizeBytes = oldArchiveSizeBytes;
        this.newArchiveSizeBytes = newArchiveSizeBytes;
    }

    /**
     * Sets the entry representing the results of a whole-archive patch.
     * @param wholeArchiveEntry the entry to set
     */
    public void setWholeArchiveEntry(PatchGenerationReportEntry wholeArchiveEntry) {
        this.wholeArchivePatchEntry = wholeArchiveEntry;
    }

    /**
     * Sets the entry representing the BEGIN command for the patch.
     * @param directive the BEGIN directive to set
     */
    public void setBeginEntry(PatchDirective directive) {
        if (directive.getCommand() != PatchCommand.BEGIN) {
            throw new IllegalArgumentException("Must be a BEGIN command");
        }
        this.beginPatchEntry = new PatchGenerationReportEntry(
            null,  // oldPath
            null,  // newPath
            PatchCommand.BEGIN,  // patchCommand
            BuiltInCompressionEngine.NONE.getId(),
            0,  // oldArchiveSizeBytes
            BuiltInCompressionEngine.NONE.getId(),
            directive.getPart().getStructureLength());  // newArchiveSizeBytes
        this.beginPatchEntry.setFullEntrySizeBytes(
            directive.getPart().getStructureLength());
    }

    /**
     * Sets the total size of the patch, in bytes.
     * @param sizeBytes the value to set
     */
    public void setTotalPatchSizeBytes(int sizeBytes) {
        totalPatchSizeBytes = sizeBytes;
    }

    /**
     * Returns a list of all of the entries in the report.
     * @return such a list
     */
    public List<PatchGenerationReportEntry> getPerFilePatchEntries() {
        return Collections.unmodifiableList(perFilePatchEntries);
    }

    /**
     * Adds a new entry to the report.
     * @param oldPath the path of the resource in the old archive
     * @param newPath the path of the resource in the new archive
     * @param command the patch command that was used
     * @param oldCompressionEngineId the ID of the compression engine used to
     * compress the resource in the old archive
     * @param oldSizeBytes the size of the compressed data in the old archive
     * @param newCompressionEngineId the ID of the compression engine used to
     * compress the resource in the new archive
     * @param newSizeBytes the size of the compressed data in the new archive
     * @return the entry added
     */
    public PatchGenerationReportEntry addEntry(String oldPath, String newPath,
        PatchCommand command, int oldCompressionEngineId, int oldSizeBytes,
        int newCompressionEngineId, int newSizeBytes) {
        PatchGenerationReportEntry newEntry = new PatchGenerationReportEntry(
            oldPath, newPath, command, oldCompressionEngineId, oldSizeBytes,
            newCompressionEngineId, newSizeBytes);
        perFilePatchEntries.add(newEntry);
        return newEntry;
    }

    /**
     * Remove an existing entry from the report.
     * @param entry the entry to remove
     */
    public void removeEntry(PatchGenerationReportEntry entry) {
        perFilePatchEntries.remove(entry);
    }

    /**
     * Sets the boolean flag indicating whether or not the report is for a
     * patch over the entire archive (as opposed to file-by-file).
     * @param value true if so, otherwise false
     */
    public void setWholeArchivePatchUsed(boolean value) {
        this.wholeArchivePatchUsed = value;
    }

    /**
     * Returns the entry for the start of the patch.
     * @return the entry
     */
    public PatchGenerationReportEntry getBeginPatchEntry() {
        return beginPatchEntry;
    }

    /**
     * If this report refers to a patch over the entire archive (as opposed to
     * file-by-file), returns the entry describing that result.
     * @return the result, or null if the report does NOT refer to a patch over
     * the whole archive
     */
    public PatchGenerationReportEntry getWholeArchivePatchEntry() {
        return wholeArchivePatchEntry;
    }

    /**
     * Return a simple string consisting of the old archive size, new archive
     * size and total patch size (all in bytes) separated by commas.
     * 
     * @return the string described
     */
    public String toCsv() {
        return oldArchiveSizeBytes + "," + newArchiveSizeBytes + "," + totalPatchSizeBytes;
    }

    /**
     * Dumps the report to a human-readable format.
     */
    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append("Old archive size: " + dec(oldArchiveSizeBytes) + "\n");
        buffer.append("New archive size: " + dec(newArchiveSizeBytes) + "\n");
        buffer.append("Total patch size: " + dec(totalPatchSizeBytes) + "\n");
        buffer.append("Total savings using patch instead of new archive: " + dec(newArchiveSizeBytes - totalPatchSizeBytes) + "\n");
        buffer.append("Patch style used: ");
        if (wholeArchivePatchUsed) {
            buffer.append("whole-archive delta\n");
        } else {
            buffer.append("deltas per-entry\n");
        }
        buffer.append("----------\n");
        buffer.append("Whole-archive delta statistics:\n");
        appendEntry(buffer, wholeArchivePatchEntry);
        buffer.append("----------\n\n\n");

        buffer.append("Deltas per-entry statistics:\n");
        buffer.append("Begin-patch entry:\n");
        appendEntry(buffer, beginPatchEntry);
        buffer.append("----------\n");

        buffer.append("Per-file patch entries:\n");
        buffer.append("----------\n");
        for (int x=0; x<perFilePatchEntries.size(); x++) {
            PatchGenerationReportEntry entry = perFilePatchEntries.get(x);
            appendEntry(buffer, entry);
            if (x < perFilePatchEntries.size() - 1) {
                // Add blank line between entries
                buffer.append("----------\n");
            }
        }
        buffer.append("----------\n");
        return buffer.toString();
    }

    /**
     * Append one entry to the report.
     * @param buffer the buffer to append to
     * @param entry the entry to write
     */
    private void appendEntry(StringBuilder buffer, PatchGenerationReportEntry entry) {
        final String oldPath = entry.getOldPath();
        final String newPath = entry.getNewPath();
        if (oldPath != null) {
            buffer.append("Entry in old archive: " + oldPath + " (" + entry.getOldArchiveSizeBytes() + " bytes)\n");
        }
        if (newPath != null) {
            buffer.append("Entry in new archive: " + newPath + " (" + entry.getNewArchiveSizeBytes() + " bytes)\n");
        }

        final PatchCommand command = entry.getCommand();
        if (command != null) {
            buffer.append("Patch command used: " + command.toString() + "\n");
        }

        if (entry.isDeltaGenerationAttempted()) {
            final Map<Strategy, Integer> deltaSizesByStrategy =
                entry.getDeltaSizeBytesByStrategy();
            if (!deltaSizesByStrategy.isEmpty()) {
                final Strategy chosenDeltaTransferStrategy = entry.getChosenDeltaTransferStrategy();
                if (chosenDeltaTransferStrategy == null) {
                    buffer.append("Delta generation attempted, but failed to produce a smaller result than the 'NEW' patch command.\n");
                } else {
                    buffer.append("Delta strategy used (*): " + chosenDeltaTransferStrategy + "\n");
                }
                buffer.append("Delta generator produced sizes:\n");
                for (Map.Entry<Strategy, Integer> sizeData : deltaSizesByStrategy.entrySet()) {
                    final boolean isUsed = chosenDeltaTransferStrategy == sizeData.getKey();
                    buffer.append(isUsed ? " * " : "   ");  // denote which one was used, if any
                    buffer.append(sizeData.getKey() + ": " + dec(sizeData.getValue()) + "\n");
                }
            } else {
                buffer.append("No delta generation attempted.");
            }
        }

      // For stuff that didn't use a delta generator we only have the size of the patch directive itself.
      buffer.append("Size of patch data: " + dec(entry.getFullEntrySizeBytes()) + "\n");
    }

    private String dec(int value) {
        if (decimalFormat == null) {
            decimalFormat = new DecimalFormat("#,###,###,### bytes");
        }
        return decimalFormat.format(value);
    }
}
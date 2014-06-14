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

import com.google.archivepatcher.meta.CompressionMethod;
import com.google.archivepatcher.meta.DeflateCompressionOption;
import com.google.archivepatcher.meta.Flag;
import com.google.archivepatcher.parts.LocalSectionParts;
import com.google.archivepatcher.parts.CentralDirectoryFile;
import com.google.archivepatcher.parts.CentralDirectorySection;
import com.google.archivepatcher.parts.EndOfCentralDirectory;
import com.google.archivepatcher.parts.LocalFile;
import com.google.archivepatcher.parts.LocalSection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class ArchiveCompacter extends AbstractArchiveTool {
    public static void main(String... args) throws Exception {
        new ArchiveCompacter().run(args);
    }

    private Archive archive;
    private boolean recompress;
    private boolean removeDataDescriptors;
    private boolean removeExtras;
    private boolean removeComments;
    private boolean dryRun;
    private String outFile;

    @Override
    public void configureOptions(MicroOptions options) {
        super.configureOptions(options);
        options.option("dryrun").isUnary();
        options.option("recompress").isUnary();
        options.option("remove-dds").isUnary();
        options.option("remove-extras").isUnary();
        options.option("remove-comments").isUnary();
        options.option("aggressive").isUnary();
        options.option("outfile");
        options.option("archive").isRequired();
    }

    @Override
    protected final void run(MicroOptions options) throws Exception {
        if (!options.has("dryrun") && !options.has("outfile")) {
            throw new MicroOptions.MissingArgException(
                    "must specify --outfile unless using --dryrun");
        }
        setArchive(Archive.fromFile(options.getArg("archive")));
        final boolean aggressive = options.has("aggressive");
        setDryRun(options.has("dryrun"));
        setRecompress(aggressive | options.has("recompress"));
        setRemoveComments(aggressive | options.has("remove-comments"));
        setRemoveDataDescriptors(aggressive | options.has("remove-dds"));
        setRemoveExtras(aggressive | options.has("remove-extras"));
        if (!isDryRun()) setOutFile(options.getArg("outfile"));
        compact();
    }
    

    private byte[] recompress(byte[] data, boolean deflated) throws IOException {
        InputStream in;
        if (deflated) {
            Inflater inflater = new Inflater(true);
            in = new InflaterInputStream(new ByteArrayInputStream(data), inflater);
        } else {
            in = new ByteArrayInputStream(data);
        }
        Deflater deflater = new Deflater(9,  true);
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        DeflaterOutputStream out = new DeflaterOutputStream(byteBuffer, deflater);
        byte[] buffer = new byte[4096];
        int numRead = 0;
        while ((numRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, numRead);
        }
        out.close();
        return byteBuffer.toByteArray();
    }
    
    public int compact() throws IOException {
        final CentralDirectorySection cd = archive.getCentralDirectory();
        final LocalSection local = archive.getLocal();
        int wastedByDataDescriptor = 0;
        int numWastedDataDescriptors = 0;
        int wastedByLocalExtraField = 0;
        int numWastedLocalExtraField = 0;
        int wastedByCentralExtraField = 0;
        int numWastedCentralExtraField = 0;
        int wastedBySuboptimalCompression = 0;
        int numSuboptimallyCompressed = 0;
        int wastedByFileComment = 0;
        int numWastedFileComments = 0;
        int wastedByArchiveComment = 0;
        final NumberFormat pretty = NumberFormat.getNumberInstance(Locale.US);
        for (CentralDirectoryFile cdf : cd.entries()) {
            StringBuilder buffer = null;
            boolean noteworthy = false;
            if (isVerbose()) {
                buffer = new StringBuilder("");
            }
            final LocalSectionParts alp = local.getByPath(cdf.getFileName());

            // Data descriptors
            if (alp.hasDataDescriptor()) {
                if (isVerbose()) {
                    noteworthy = true;
                    buffer.append("  Useless data descriptor (" + pretty.format(alp.getDataDescriptorPart().getStructureLength()) + " bytes)\n");
                }
                numWastedDataDescriptors++;
                wastedByDataDescriptor += alp.getDataDescriptorPart().getStructureLength();
                if (removeDataDescriptors && !dryRun) {
                    alp.setDataDescriptorPart(null);
                    short flags = (short) alp.getLocalFilePart().getGeneralPurposeBitFlag_16bit();
                    flags = Flag.unset(Flag.USE_DATA_DESCRIPTOR_FOR_SIZES_AND_CRC32, flags);
                    alp.getLocalFilePart().setGeneralPurposeBitFlag_16bit(flags);
                    flags = (short) cdf.getGeneralPurposeBitFlag_16bit();
                    flags = Flag.unset(Flag.USE_DATA_DESCRIPTOR_FOR_SIZES_AND_CRC32, flags);
                    cdf.setGeneralPurposeBitFlag_16bit(flags);
                }
            }

            // Extra fields
            final LocalFile lf = alp.getLocalFilePart();
            if (lf.getExtraFieldLength_16bit() > 0) {
                if (isVerbose()) {
                    noteworthy = true;
                    buffer.append("  Useless local 'extra' field (" + pretty.format(lf.getExtraFieldLength_16bit()) + " bytes)\n");
                }
                numWastedLocalExtraField++;
                wastedByLocalExtraField += lf.getExtraFieldLength_16bit();
                if (removeExtras && !dryRun) {
                    lf.setExtraField(new byte[0]);
                }
            }
            if (cdf.getExtraFieldLength_16bit() > 0) {
                if (isVerbose()) {
                    noteworthy = true;
                    buffer.append("  Useless central directory 'extra' field (" + pretty.format(cdf.getExtraFieldLength_16bit()) + " bytes)\n");
                }
                numWastedCentralExtraField++;
                wastedByCentralExtraField += cdf.getExtraFieldLength_16bit();
                if (removeExtras && !dryRun) {
                    cdf.setExtraField(new byte[0]);
                }
            }
            
            // Recompress
            final boolean isCompressed = !CompressionMethod.NO_COMPRESSION.equals(alp.getLocalFilePart().getCompressionMethod());
            final byte[] recompressed = recompress(alp.getFileDataPart().getData(), isCompressed);
            final int potentialDelta = recompressed.length - alp.getFileDataPart().getData().length;
            if (potentialDelta < 0) {
                if (isVerbose()) {
                    buffer.append("  Suboptimal compression (" + pretty.format(potentialDelta * -1) + " bytes)\n");
                    noteworthy = true;
                }
                numSuboptimallyCompressed++;
                wastedBySuboptimalCompression += (-1 * potentialDelta);
                if (recompress && !dryRun) {
                    alp.getFileDataPart().setData(recompressed);
                    lf.setCompressedSize_32bit(recompressed.length);
                    lf.setCompressionMethod_16bit(CompressionMethod.DEFLATED.value);
                    short flags = (short) lf.getGeneralPurposeBitFlag_16bit();
                    flags = Flag.setCompressionOption(DeflateCompressionOption.MAXIMUM, flags);
                    lf.setGeneralPurposeBitFlag_16bit(flags);
                    cdf.setCompressedSize_32bit(recompressed.length);
                    flags = (short) cdf.getGeneralPurposeBitFlag_16bit();
                    flags = Flag.setCompressionOption(DeflateCompressionOption.MAXIMUM, flags);
                    cdf.setGeneralPurposeBitFlag_16bit(flags);
                    cdf.setCompressionMethod_16bit(CompressionMethod.DEFLATED.value);
                }
            }
            
            // File comments
            if (cdf.getFileCommentLength_16bit() > 0) {
                if (isVerbose()) {
                    noteworthy = true;
                    buffer.append("  Useless file comment (" + pretty.format(cdf.getFileCommentLength_16bit()) + " bytes)\n");
                }
                numWastedFileComments++;
                wastedByFileComment += cdf.getFileCommentLength_16bit();
                if (removeComments && !dryRun) {
                    cdf.setFileComment("");
                }
            }

            // All done
            if (isVerbose() && noteworthy) {
                buffer.insert(0, "Entry: " + cdf.getFileName() + ":\n");
                buffer.deleteCharAt(buffer.length()-1);
                log(buffer.toString());
            }
        }
        final EndOfCentralDirectory eocd = cd.getEocd();
        wastedByArchiveComment += eocd.getZipFileCommentLength_16bit();
        if (isVerbose() && eocd.getZipFileCommentLength_16bit() > 0) {
            log("Useless archive comment (" + pretty.format(eocd.getZipFileCommentLength_16bit()) + " bytes)");
        }
        if (removeComments && !dryRun) {
            eocd.setZipFileComment("");
        }

        final int totalWasted =
                wastedByArchiveComment + wastedByCentralExtraField +
                wastedByDataDescriptor + wastedByFileComment +
                wastedByLocalExtraField + wastedBySuboptimalCompression;
        final int numEntries = cd.entries().size();
        final String OF_X = "/" + pretty.format(numEntries);
        log("Total entries: " + pretty.format(numEntries));
        if (totalWasted > 0)
            log("Total wasted space: " + pretty.format(totalWasted) + " bytes:");
        if (wastedByLocalExtraField > 0)
            log("  Local 'Extra' fields:             " + pretty.format(wastedByLocalExtraField) + " bytes (" + pretty.format(numWastedLocalExtraField) + OF_X + " entries)");
        if (wastedByCentralExtraField > 0)
            log("  Central Directory 'Extra' fields: " + pretty.format(wastedByCentralExtraField) + " bytes (" + pretty.format(numWastedCentralExtraField) + OF_X + " entries)");
        if (wastedByDataDescriptor > 0)
            log("  Redundant data descriptors:       " + pretty.format(wastedByDataDescriptor) + " bytes (" + pretty.format(numWastedDataDescriptors) + OF_X + " entries)");
        if (wastedBySuboptimalCompression > 0)
            log("  Suboptimal compression:           " + pretty.format(wastedBySuboptimalCompression) + " bytes (" + pretty.format(numSuboptimallyCompressed) + OF_X + " entries)");
        if (wastedByFileComment > 0)
            log("  Unnecessary file comments:        " + pretty.format(wastedByFileComment) + " bytes (" + pretty.format(numWastedFileComments) + OF_X + " entries)");
        if (wastedByArchiveComment > 0)
            log("  Unnecessary archive comment:      " + pretty.format(wastedByArchiveComment) + " bytes");

        // Realign the archive to take into account all changes made.
        if (!dryRun) {
            if (isVerbose()) log("Realigning archive...");
            realign();
            if (isVerbose()) log("Writing compacted archive to " + getOutFile() + "...");
            FileOutputStream out = new FileOutputStream(getOutFile());
            archive.writeArchive(out);
            out.flush();
            out.close();
            if (isVerbose()) log("Done, new size=" + pretty.format(new File(getOutFile()).length()) + " bytes");
        } else if (isVerbose()) {
            log("Dry run only, not writing compacted archive.");
        }

        return totalWasted;
    }

    // TODO: This could be a method in Archive.
    private void realign() {
        final CentralDirectorySection cd = archive.getCentralDirectory();
        final LocalSection local = archive.getLocal();
        int offset = 0;
        for (LocalSectionParts alp : local.entries()) {
            final LocalFile lf =  alp.getLocalFilePart();
            final CentralDirectoryFile cdf = cd.getByPath(lf.getFileName());
            cdf.setRelativeOffsetOfLocalHeader_32bit(offset);
            offset += alp.getStructureLength();
        }
        final int startOfCentralDirectory = offset;
        for (CentralDirectoryFile cdf : cd.entries()) {
            offset += cdf.getStructureLength();
        }
        final int lengthOfCentralDirectory = offset - startOfCentralDirectory;
        final EndOfCentralDirectory eocd = cd.getEocd();
        eocd.setLengthOfCentralDirectory_32bit(lengthOfCentralDirectory);
        eocd.setOffsetOfStartOfCentralDirectoryRelativeToDisk_32bit(startOfCentralDirectory);
        if (isVerbose()) {
            log("Start of local section:      0");
            log("Length of local section:     " + startOfCentralDirectory + " bytes");
            log("Start of central directory:  " + startOfCentralDirectory);
            log("Length of central directory: " + lengthOfCentralDirectory + " bytes");
            log("Start of EOCD:               " + (startOfCentralDirectory + lengthOfCentralDirectory));
            log("Length of EOCD:              " + eocd.getStructureLength() + " bytes");
        }
    }

    // Autogen cruft
    public Archive getArchive() { return archive; }
    public void setArchive(Archive arg) { archive = arg; }
    public String getOutFile() { return outFile; }
    public void setOutFile(String arg) { outFile = arg; }
    public boolean isRecompress() { return recompress; }
    public void setRecompress(boolean arg) { recompress = arg; }
    public boolean isRemoveDataDescriptors() { return removeDataDescriptors; }
    public void setRemoveDataDescriptors(boolean arg) { removeDataDescriptors = arg; }
    public boolean isRemoveExtras() { return removeExtras; }
    public void setRemoveExtras(boolean arg) { removeExtras = arg; }
    public boolean isRemoveComments() { return removeComments; }
    public void setRemoveComments(boolean arg) { removeComments = arg; }
    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean arg) { dryRun = arg; }
}
// Copyright (C) 2014 Andrew Hayden.  All rights reserved.

package com.google.archivepatcher.patcher;

import java.io.UnsupportedEncodingException;

import com.google.archivepatcher.meta.CompressionMethod;
import com.google.archivepatcher.parts.CentralDirectoryFile;
import com.google.archivepatcher.parts.CentralDirectorySection;
import com.google.archivepatcher.parts.EndOfCentralDirectory;
import com.google.archivepatcher.parts.FileData;
import com.google.archivepatcher.parts.LocalFile;
import com.google.archivepatcher.util.MsDosDate;
import com.google.archivepatcher.util.MsDosTime;


/**
 * Helper class used in {@link PatchWriterTest} and {@link PatchParserTest}.
 */
@SuppressWarnings("javadoc")
class PatchTestData {
    CentralDirectoryFile cdf;
    CentralDirectorySection cds;
    EndOfCentralDirectory eocd;
    LocalFile lf;
    FileData fd;

    PatchTestData() {
        cdf = new CentralDirectoryFile();
        cdf.setCompressedSize_32bit(3L);
        cdf.setCompressionMethod_16bit(CompressionMethod.NO_COMPRESSION.value);
        cdf.setCrc32_32bit(32);
        cdf.setDiskNumberStart_16bit(1);
        cdf.setFileName("foo");
        cdf.setLastModifiedFileDate_16bit(new MsDosDate(1, 1, 0).to16BitPackedValue());
        cdf.setLastModifiedFileTime_16bit(new MsDosTime(0, 0, 0).to16BitPackedValue());
        cdf.setRelativeOffsetOfLocalHeader_32bit(1);
        cdf.setUncompressedSize_32bit(3L);
        cdf.setVersionMadeBy_16bit(20);
        cdf.setVersionNeededToExtract_16bit(20);
        eocd = new EndOfCentralDirectory();
        eocd.setDiskNumber_16bit(1);
        eocd.setDiskNumberOfStartOfCentralDirectory_16bit(1);
        eocd.setLengthOfCentralDirectory_32bit(1000);
        eocd.setNumEntriesInCentralDir_16bit(1);
        eocd.setNumEntriesInCentralDirThisDisk_16bit(1);
        eocd.setOffsetOfStartOfCentralDirectoryRelativeToDisk_32bit(1);
        cds = new CentralDirectorySection();
        cds.append(cdf);
        cds.setEocd(eocd);

        lf = new LocalFile();
        lf.setCompressedSize_32bit(3L);
        lf.setCompressionMethod_16bit(CompressionMethod.NO_COMPRESSION.value);
        lf.setCrc32_32bit(32);
        lf.setFileName("foo");
        lf.setLastModifiedFileDate_16bit(new MsDosDate(1, 1, 0).to16BitPackedValue());
        lf.setLastModifiedFileTime_16bit(new MsDosTime(0, 0, 0).to16BitPackedValue());
        lf.setUncompressedSize_32bit(3L);
        lf.setVersionNeededToExtract_16bit(20);
        try {
            fd = new FileData("foo".getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            // UTF-8 support required by Java specification.
            throw new RuntimeException("System doesn't support UTF8");
        }
    }
}

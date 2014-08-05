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

import com.google.archivepatcher.parts.LocalSectionParts;
import com.google.archivepatcher.parts.CentralDirectoryFile;
import com.google.archivepatcher.parts.CentralDirectorySection;
import com.google.archivepatcher.parts.EndOfCentralDirectory;
import com.google.archivepatcher.parts.LocalSection;

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

/**
 * A simple representation of an archive consisting of two parts: a "local"
 * section and a "central directory".
 */
public class Archive {
    /**
     * The PKZIP "version" that this library supports.
     */
    protected static final int STANDARD_VERSION = 20;

    /**
     * The "local" section of the archive: local file entries and their
     * associated headers.
     */
    protected LocalSection local;

    /**
     * The "central directory" section of the archive; lives at the end of the
     * archive and contains references back to the local file entries.
     */
    protected CentralDirectorySection centralDirectory;

    /**
     * Read an archive in from the specified filesystem path.
     * This is a convenience method that invokes
     * {@link #fromFile(RandomAccessFile)}.
     * 
     * @param path the path to the archive that should be read
     * @return the results of parsing the archive
     * @throws IOException if something goes wrong while reading
     */
    public static Archive fromFile(String path) throws IOException {
        RandomAccessFile file = null;
        try {
            file = new RandomAccessFile(path, "r");
            return Archive.fromFile(file);
        } finally {
            try {
                file.close();
            } catch (Exception ignored) {
                // Ignored
            }
        }
    }

    /**
     * Read an archive in from the specified {@link RandomAccessFile}.
     * 
     * @param raf the file to read from
     * @return the results of parsing the archive
     * @throws IOException if something goes wrong while reading
     */
    public static Archive fromFile(RandomAccessFile raf) throws IOException {
        LocalSection local = new LocalSection();
        CentralDirectorySection centralDirectory =
            new CentralDirectorySection();

        // First, read in the End Of Central Directory, which has the offsets
        // we need in order to read the central directory, which in turn has
        // the data lengths for each local file entry in the archive.
        final int eocdOffset = EndOfCentralDirectory.seek(raf);
        if (eocdOffset == -1) throw new IOException("no EOCD marker");
        final EndOfCentralDirectory eocd = new EndOfCentralDirectory();
        raf.seek(eocdOffset);
        eocd.read(raf);
        centralDirectory.setEocd(eocd);

        // With the EOCD in hand we can read the central directory itself.
        final int offsetOfCentralDirectory = (int)
                eocd.getOffsetOfStartOfCentralDirectoryRelativeToDisk_32bit();
        raf.seek(offsetOfCentralDirectory);
        final int numRecords = eocd.getNumEntriesInCentralDirThisDisk_16bit();
        for (int x=0; x<numRecords; x++) {
            CentralDirectoryFile header = new CentralDirectoryFile();
            header.read(raf);
            centralDirectory.append(header);
        }

        raf.seek(0);
        for (int x=0; x<numRecords; x++) {
            LocalSectionParts localPart = new LocalSectionParts(centralDirectory);
            localPart.read(raf);
            local.append(localPart);
        }
        return new Archive(local, centralDirectory);
    }

    /**
     * Creates a new, empty archive.
     */
    public Archive() {
        this(new LocalSection(), new CentralDirectorySection());
    }

    /**
     * Creates an archive with the specified "local" section and "central
     * directory" section.
     * 
     * @param local the "local" section of the archive
     * @param centralDirectory the "central directory" section of the archive
     */
    public Archive(LocalSection local, CentralDirectorySection centralDirectory) {
        this.local = local;
        this.centralDirectory = centralDirectory;
    }

    /**
     * Sets the "local" section of the archive.
     * @param local the new value to set
     */
    public void setLocal(LocalSection local) {
        this.local = local;
    }

    /**
     * Returns the "local" section of the archive.
     * @return the "local" section of the archive
     */
    public LocalSection getLocal() {
        return local;
    }

    /**
     * Sets the "central directory" section of the archive.
     * @param centralDirectory the new value to set
     */
    public void setCentralDirectry(CentralDirectorySection centralDirectory) {
        this.centralDirectory = centralDirectory;
    }

    /**
     * Returns the "central directory" section of the archive.
     * @return the "central directory" section of the archive
     */
    public CentralDirectorySection getCentralDirectory() {
        return centralDirectory;
    }

    /**
     * Writes the archive to the specified {@link OutputStream}.
     * This is a convenience method that invokes
     * {@link #writeArchive(DataOutput)}.
     * 
     * @param out the output stream to write to
     * @throws IOException if anything goes wrong while writing
     */
    public final void writeArchive(OutputStream out) throws IOException {
        writeArchive((DataOutput) new DataOutputStream(out));
        out.flush();
    }

    /**
     * Writes the archive to the specified {@link DataOutputStream}.
     * @param out the output to write to
     * @throws IOException if anything goes wrong while writing
     */
    public void writeArchive(DataOutput out) throws IOException {
        for (LocalSectionParts alp : local.entries()) {
            alp.write(out);
        }
        for (CentralDirectoryFile cdf : centralDirectory.entries()) {
            cdf.write(out);
        }
        centralDirectory.getEocd().write(out);
    }

    // Autogenerated, no special logic
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((centralDirectory == null) ?
            0 : centralDirectory.hashCode());
        result = prime * result + ((local == null) ? 0 : local.hashCode());
        return result;
    }

    // Autogenerated, no special logic
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        Archive other = (Archive) obj;
        if (centralDirectory == null) {
            if (other.centralDirectory != null)
                return false;
        } else if (!centralDirectory.equals(other.centralDirectory))
            return false;
        if (local == null) {
            if (other.local != null)
                return false;
        } else if (!local.equals(other.local))
            return false;
        return true;
    }
}

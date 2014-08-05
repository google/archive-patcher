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

import com.google.archivepatcher.meta.Flag;
import com.google.archivepatcher.parts.CentralDirectoryFile;
import com.google.archivepatcher.parts.FileData;
import com.google.archivepatcher.parts.DataDescriptor;
import com.google.archivepatcher.parts.EndOfCentralDirectory;
import com.google.archivepatcher.parts.LocalFile;
import com.google.archivepatcher.parts.Part;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.LinkedList;
import java.util.List;

/**
 * An experimental tool that attempts to copy an archive entry-by-entry instead
 * of byte-by-byte. This provides a sanity check that a given source archive
 * can be processed by this library without any data loss; if the resulting
 * copy is not a perfect binary match for the source archive, then it may be
 * unsafe to use this library to process it.
 */
public class ZipCopy {
    /**
     * Runs the copy tool. Args are the source path (copy from) and destination
     * path (copy to).
     * 
     * @param args the arguments to the program
     * @throws Exception if anything goes wrong
     */
    public static void main(String... args) throws Exception {
        new ZipCopy().copy(args[0], args[1]);
    }

    /**
     * Copy the archive at the specified source path to a new archive whose
     * path is destinationPath.
     * 
     * @param sourcePath archive to copy from
     * @param destinationPath path where the copy should be created
     * @throws IOException if anything goes wrong
     */
    public void copy(final String sourcePath, final String destinationPath)
    throws IOException {
        RandomAccessFile raf = new RandomAccessFile(sourcePath, "r");
        int eocdOffset = EndOfCentralDirectory.seek(raf);
        if (eocdOffset == -1) return;
        EndOfCentralDirectory eocd = new EndOfCentralDirectory();
        raf.seek(eocdOffset);
        eocd.read(raf);
        final int offsetOfCentralDirectory = (int)
                eocd.getOffsetOfStartOfCentralDirectoryRelativeToDisk_32bit();
        raf.seek(offsetOfCentralDirectory);
        final int numRecords = eocd.getNumEntriesInCentralDirThisDisk_16bit();
        final CentralDirectoryFile[] centralDirectoryFileHeaders = new CentralDirectoryFile[numRecords];
        final List<Part> centralDirectoryParts = new LinkedList<Part>();
        for (int x=0; x<numRecords; x++) {
            CentralDirectoryFile header = new CentralDirectoryFile();
            header.read(raf);
            centralDirectoryFileHeaders[x] = header;
            centralDirectoryParts.add(header);
        }
        centralDirectoryParts.add(eocd);
        
        final List<Part> localParts = new LinkedList<Part>();
        for (int x=0; x<numRecords; x++) {
            CentralDirectoryFile header = centralDirectoryFileHeaders[x];
            int offset = (int) header.getRelativeOffsetOfLocalHeader_32bit();
            raf.seek(offset);
            LocalFile localHeader = new LocalFile();
            localHeader.read(raf);
            localParts.add(localHeader);
            final int compressedSize = (int) header.getCompressedSize_32bit();
            FileData dataPart = new FileData(compressedSize);
            dataPart.read(raf);
            localParts.add(dataPart);
            final short flags = (short) header.getGeneralPurposeBitFlag_16bit();
            boolean hasDataDescriptor = Flag.has(Flag.USE_DATA_DESCRIPTOR_FOR_SIZES_AND_CRC32, flags);
            if (hasDataDescriptor) {
                DataDescriptor descriptor = new DataDescriptor();
                descriptor.read(raf);
                localParts.add(descriptor);
            }
        }
        
        // Now output a copy
        FileOutputStream fos = new FileOutputStream(destinationPath);
        DataOutputStream out = new DataOutputStream(fos);
        List<Part> allParts = new LinkedList<Part>();
        allParts.addAll(localParts);
        allParts.addAll(centralDirectoryParts);
        for (Part part : allParts) {
            part.write(out);
        }
        out.flush();
        fos.flush();
        fos.close();
    }
}

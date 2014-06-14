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
import java.io.RandomAccessFile;
import java.util.LinkedList;
import java.util.List;


public class ZipCopy {
    public static void main(String... args) throws Exception {
        String fileName = args[0];
        System.out.println("zipcat " + fileName);

        RandomAccessFile raf = new RandomAccessFile(fileName, "r");
        int eocdOffset = EndOfCentralDirectory.seek(raf);
        System.out.println("eocd offset=" + eocdOffset);
        if (eocdOffset == -1) return;
        EndOfCentralDirectory eocd = new EndOfCentralDirectory();
        raf.seek(eocdOffset);
        eocd.read(raf);
        System.out.println(eocd);
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
            System.out.println(header);
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
            System.out.println(localHeader);
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
                System.out.println(descriptor);
            }
        }
        
        System.out.println("making copy");
        // Now output a copy
        FileOutputStream fos = new FileOutputStream(fileName + ".copy");
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

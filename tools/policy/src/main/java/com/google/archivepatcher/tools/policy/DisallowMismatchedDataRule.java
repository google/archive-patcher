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

package com.google.archivepatcher.tools.policy;

import com.google.archivepatcher.meta.Flag;
import com.google.archivepatcher.parts.DataDescriptor;
import com.google.archivepatcher.parts.LocalFile;

/**
 * Disallow any mismatch between the data that is stored both in the central
 * directory and the local section for a file.
 */
public class DisallowMismatchedDataRule extends EntryRule {

    /**
     * Whether or not to allow the data descriptor to be redundant with the
     * local file part; see constructor for more information.
     */
    private final boolean allowRedundantDataDescriptor;

    /**
     * Create a new instance of the rule.
     * @param allowRedundantDataDescriptor if true, allow the local file part
     * of the archive to contain the crc32, compressed and uncompressed size
     * even if a data descriptor is specified - so long as the values in both
     * parts match. The spec says that if a data descriptor is used, the values
     * in the local file entry should all be zeroes; but some tools don't
     * follow this rule correctly.
     */
    public DisallowMismatchedDataRule(boolean allowRedundantDataDescriptor) {
        this.allowRedundantDataDescriptor = allowRedundantDataDescriptor;
    }

    @Override
    protected void checkInternal() {
        LocalFile localFile = lsp.getLocalFilePart();
        // Now verify constraints: if central directory says this entry has a
        // data descriptor, it needs to.
        final boolean shouldHaveDataDescriptor = Flag.has(
            Flag.USE_DATA_DESCRIPTOR_FOR_SIZES_AND_CRC32,
            (short) cdf.getGeneralPurposeBitFlag_16bit());
        final DataDescriptor localDescriptor = lsp.getDataDescriptorPart();
        if (shouldHaveDataDescriptor && localDescriptor == null) {
            notOk(cdf.getFileName(),
                "should use a data descriptor in the local part but doesn't");
        }
        if (!shouldHaveDataDescriptor && localDescriptor != null) {
            notOk(cdf.getFileName(),
                "should not use a data descriptor in the local part but does");
        }

        // Verify same-ness of all fields except for the extras, because the
        // extras field is often used in the local section *only* for word-
        // and page-aligning resources for direct access via mmap.
        ensureSame("compression method", cdf.getCompressionMethod(),
            localFile.getCompressionMethod());
        ensureSame("file name length", cdf.getFileNameLength_16bit(),
            localFile.getFileNameLength_16bit());
        ensureSame("file name", cdf.getFileName(), localFile.getFileName());
        ensureSame("general purpose flags",
            cdf.getGeneralPurposeBitFlag_16bit(),
            localFile.getGeneralPurposeBitFlag_16bit());
        ensureSame("last modified date", cdf.getLastModifiedFileDate(),
            localFile.getLastModifiedFileDate());
        ensureSame("last modified time", cdf.getLastModifiedFileTime(),
            localFile.getLastModifiedFileTime());
        ensureSame("version needed to extract",
            cdf.getVersionNeededToExtract_16bit(),
            localFile.getVersionNeededToExtract_16bit());

        if (shouldHaveDataDescriptor) {
            // CRC32, compressed and uncompressed sizes of the local file part
            // should all be zero. However, empirically this is not true in some
            // archives, like APKs.
            if (!allowRedundantDataDescriptor) {
                if (localFile.getCrc32_32bit() != 0) {
                    notOk(cdf.getFileName(),
                        "uses a data descriptor but has a " +
                        "non-zero value in the local section crc32");
                }
                if (localFile.getCompressedSize_32bit() != 0) {
                    notOk(cdf.getFileName(),
                        "uses a data descriptor but has a " +
                        "non-zero value in the local section compressed size");
                }
                if (localFile.getUncompressedSize_32bit() != 0) {
                    notOk(cdf.getFileName(),
                        "uses a data descriptor but has a " +
                        "non-zero value in the local section uncompressed size");
                }
            } else {
                // Allow the redundant information to exist as long as it is
                // correct.
                if (localFile.getCrc32_32bit() != 0) {
                    ensureSame("crc32 (non-conforming)", cdf.getCrc32_32bit(),
                        localFile.getCrc32_32bit());
                }
                if (localFile.getCompressedSize_32bit() != 0) {
                    ensureSame("compressed size (non-conforming)",
                        cdf.getCompressedSize_32bit(),
                        localFile.getCompressedSize_32bit());
                }
                if (localFile.getUncompressedSize_32bit() != 0) {
                    ensureSame("uncompressed size (non-conforming)",
                        cdf.getUncompressedSize_32bit(),
                        localFile.getUncompressedSize_32bit());
                }
            }

            if (lsp.getDataDescriptorPart() != null) {
                // CRC32, compressed and uncompressed sizes of the data
                // descriptor part should be set and should match the values in
                // the central directory.
                ensureSame("crc32 (using data descriptor)",
                    cdf.getCrc32_32bit(),
                    localDescriptor.getCrc32_32bit());
                ensureSame("compressed size (using data descriptor)",
                    cdf.getCompressedSize_32bit(),
                    localDescriptor.getCompressedSize_32bit());
                ensureSame("uncompressed size (using data descriptor)",
                    cdf.getUncompressedSize_32bit(),
                    localDescriptor.getUncompressedSize_32bit());
            }
        } else {
            // CRC32, compressed and uncompressed sizes of the local file part
            // should be set and should match the values in the central
            // directory.
            ensureSame("crc32", cdf.getCrc32_32bit(),
                localFile.getCrc32_32bit());
            ensureSame("compressed size", cdf.getCompressedSize_32bit(),
                localFile.getCompressedSize_32bit());
            ensureSame("uncompressed size", cdf.getUncompressedSize_32bit(),
                localFile.getUncompressedSize_32bit());
        }
    }

    private void ensureSame(String description, Object centralDirectoryValue,
        Object localSectionValue) {
        if (centralDirectoryValue == null && localSectionValue == null) {
            return;  // Both null: ok.
        }
        if (centralDirectoryValue == null || localSectionValue == null) {
            // One value is null, the other is not.
            mismatch(description, centralDirectoryValue, localSectionValue);
            return;
        }
        if (!centralDirectoryValue.equals(localSectionValue)) {
            // Values do not match
            mismatch(description, centralDirectoryValue, localSectionValue);
        }
    }
    private void mismatch(String description, Object centralDirectoryValue,
        Object localSectionValue) {
        notOk(cdf.getFileName(), description + " mismatch: value in central " +
            "directory (" + centralDirectoryValue + ") != value in local " +
            "section (" + localSectionValue + ")");
    }
}
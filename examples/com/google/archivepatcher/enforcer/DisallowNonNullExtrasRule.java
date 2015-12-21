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

package com.google.archivepatcher.enforcer;

/**
 * If an 'extras' field is present, ensure that it contains only nulls.
 */
public class DisallowNonNullExtrasRule extends EntryRule {
    @Override
    protected void checkInternal() {
        int indexOfBadByte = indexOfNonZeroByte(cdf.getExtraField());
        if (indexOfBadByte != -1) {
            notOk(cdf.getFileName(), "extras field in central directory " +
                "contains non-null at index " + indexOfBadByte);
        }
        indexOfBadByte = indexOfNonZeroByte(
            lsp.getLocalFilePart().getExtraField());
        if (indexOfBadByte != -1) {
            notOk(cdf.getFileName(), "extras field in local section " +
                "contains non-null at index " + indexOfBadByte);
        }
    }

    /**
     * Returns the first index of a non-zero byte in the specified data, or -1
     * if there is no such byte.
     * @param data the data to check
     * @return as described
     */
    private final int indexOfNonZeroByte(byte[] data) {
        if (data == null) {
            return -1;
        }
        for (int x=0; x<data.length; x++) {
            if (data[x] != 0) {
                return x;
            }
        }
        return -1;
    }
}
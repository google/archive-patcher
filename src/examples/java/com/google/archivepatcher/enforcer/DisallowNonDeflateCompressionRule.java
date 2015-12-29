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

import com.google.archivepatcher.meta.CompressionMethod;

/**
 * Where compression has been used, disallows all algorithms other than deflate.
 * Ignores entries where compression has not been used.
 */
public class DisallowNonDeflateCompressionRule extends EntryRule {
    @Override
    protected void checkInternal() {
        if (cdf.getCompressionMethod() != CompressionMethod.DEFLATED &&
            cdf.getCompressionMethod() != CompressionMethod.NO_COMPRESSION) {
            notOk(cdf.getFileName(),
                "central directory specified non-deflate compression");
        }
        final CompressionMethod localCompression =
            lsp.getLocalFilePart().getCompressionMethod();
        if (localCompression != CompressionMethod.DEFLATED &&
            localCompression != CompressionMethod.NO_COMPRESSION) {
            notOk(cdf.getFileName(),
                "local section specified non-deflate compression");
        }
    }
}

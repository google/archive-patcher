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

import com.google.archivepatcher.Archive;
import com.google.archivepatcher.parts.CentralDirectoryFile;
import com.google.archivepatcher.parts.LocalSectionParts;

/**
 * An EntryRule is responsible for scanning one entry in an archive. It will
 * either accept or reject the entry depending on the implementation, optionally
 * producing a human-readable message. The intention is that a combination of
 * rules can be combined to enforce policies for archives, such as forbidding
 * the use of "comments" in an archive or ensuring that only certain compression
 * methods are used, and so on.
 * @see ArchiveRule
 */
public abstract class EntryRule extends AbstractRule {
    /**
     * The archive being processed.
     */
    protected Archive archive;

    /**
     * The central directory data for the entry being processed.
     */
    protected CentralDirectoryFile cdf;

    /**
     * The local section data for the entry being processed.
     */
    protected LocalSectionParts lsp;

    /**
     * Scans one entry in an archive.
     * @param archive the archive in which the entry exists
     * @param centralDirectoryFile a {@link CentralDirectoryFile} part that
     * corresponds to the entry in the archive
     * @param localSectionParts a {@link LocalSectionParts} object that
     * contains all the local section parts that correspond to the entry in the
     * archive 
     * @return the results of the scan
     */
    public final RuleResult check(Archive archive,
        CentralDirectoryFile centralDirectoryFile,
        LocalSectionParts localSectionParts) {
        this.archive = archive;
        this.reasons = null;
        this.ok = true;
        this.cdf = centralDirectoryFile;
        this.lsp = localSectionParts;
        checkInternal();
        return new RuleResult(getClass(), ok, reasons);
    }
}
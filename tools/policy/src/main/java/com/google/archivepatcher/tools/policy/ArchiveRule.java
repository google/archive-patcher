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

import com.google.archivepatcher.Archive;

/**
 * An ArchiveRule is responsible for scanning the high-level constructs of an
 * archive (e.g., detecting the presence of a correctly-formed central
 * directory). Unlike an {@link EntryRule}, an ArchiveRule deals with the
 * structure of the archive; it can do things like scan for gaps between
 * entries, check for word alignment, and so on. Like an {@link EntryRule},
 * it will either accept or reject an archive depending on the implementation,
 * optionally producing a human-readable message. The intention is that a
 * combination of rules can be combined to enforce policies for archives.
 * @see EntryRule
 */
public abstract class ArchiveRule extends AbstractRule {
    /**
     * The archive being processed.
     */
    protected Archive archive;

    /**
     * Scans high-level constructs of an archive.
     * @param archive the archive to scan
     * @return the results of the scan
     */
    public final RuleResult check(Archive archive) {
        this.archive = archive;
        this.reasons = null;
        this.ok = true;
        checkInternal();
        return new RuleResult(this.getClass(), ok, reasons);
    }
}
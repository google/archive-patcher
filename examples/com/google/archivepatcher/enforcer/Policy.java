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

import java.util.LinkedList;
import java.util.List;

import com.google.archivepatcher.Archive;
import com.google.archivepatcher.parts.CentralDirectoryFile;
import com.google.archivepatcher.parts.LocalSectionParts;

/**
 * Groups together a collection of {@link EntryRule}s and {@link ArchiveRule}s,
 * together forming a coherent policy for an archive.
 */
public class Policy {
    /**
     * All defined entry rules.
     */
    private final List<EntryRule> entryRules = new LinkedList<EntryRule>();

    /**
     * All defined archive rules.
     */
    private final List<ArchiveRule> archiveRules =
        new LinkedList<ArchiveRule>();

    /**
     * Adds the specified rules to this policy.
     * @param rules the rules to add
     * @return this object
     */
    public Policy addRules(AbstractRule... rules) {
        for (AbstractRule rule : rules) {
            if (rule instanceof EntryRule) {
                entryRules.add((EntryRule) rule);
            } else if (rule instanceof ArchiveRule) {
                archiveRules.add((ArchiveRule) rule);
            }
        }
        return this;
    }

    /**
     * Check if the specified archive complies with this policy.
     * @param archive the archive to check compliance for
     * @return the result of checking compliance
     */
    public ComplianceReport checkCompliance(Archive archive) {
        final ComplianceReport result =
            new ComplianceReport(archive.getBackingFile());

        // First apply all archive rules.
        for (final ArchiveRule archiveRule : archiveRules) {
            result.append(archiveRule.check(archive));
        }

        // Now apply all entry rules.
        for (final CentralDirectoryFile cdf :
            archive.getCentralDirectory().entries()) {
            final LocalSectionParts lsp = archive.getLocal().getByPath(
                cdf.getFileName());
            for (EntryRule entryRule : entryRules) {
                result.append(entryRule.check(archive, cdf, lsp));
            }
        }
        return result;
    }
}
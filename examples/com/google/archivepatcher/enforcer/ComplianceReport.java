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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contains the result of an attempt to check an archive for compliance with a
 * policy.
 */
public class ComplianceReport {
    /**
     * The archive file that the result is for.
     */
    private final File file;

    /**
     * If any rule is not-ok, this field becomes false.
     */
    private boolean complies = true;

    /**
     * The results.
     */
    private final List<RuleResult> results = new ArrayList<RuleResult>();

    /**
     * Construct a new, initially valid result for the specified file.
     * @param file the file
     */
    public ComplianceReport(File file) {
        this.file = file;
    }

    /**
     * Append the result of a single rule to this policy enforcement result.
     * @param result the result to append
     */
    public void append(RuleResult result) {
        results.add(result);
        if (!result.ok) {
            complies = false;
        }
    }

    /**
     * Returns an unmodifiable, live view of the results.
     * @return the results
     */
    public List<RuleResult> getResults() {
        return Collections.unmodifiableList(results);
    }

    /**
     * Checks if the archive complied with the policy.
     * @return true if so, otherwise false
     */
    public boolean complies() {
        return complies;
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder()
             .append("ComplianceResult for ")
             .append(file.getAbsolutePath())
             .append(":")
             .append(complies ? " compliant" : " not compliant");
        for (RuleResult result : results) {
            if (!result.ok) {
                buffer.append("\n").append(result);
            }
        }
        return buffer.toString();
    }
}
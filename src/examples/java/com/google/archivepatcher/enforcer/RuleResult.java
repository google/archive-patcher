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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The result of a rule check.
 */
public class RuleResult {
    /**
     * The type of rule that generated this result.
     */
    public final Class<?> ruleClass;

    /**
     * Whether or not the result of the scan was ok.
     */
    public final boolean ok;

    /**
     * Optional details related to the result, as a human-readable string.
     */
    public final List<String> reasons;

    /**
     * Construct a new result with the specified details.
     * @param ruleClass the kind of rule that generated this result
     * @param ok whether the result was or no
     * @param reasons the reasons for the result given
     */
    public RuleResult(final Class<?> ruleClass, final boolean ok,
        List<String> reasons) {
        this.ruleClass = ruleClass;
        this.ok = ok;
        if (reasons == null || reasons.size() == 0) {
            this.reasons = Collections.emptyList();
        } else {
            this.reasons = Collections.unmodifiableList(
                new ArrayList<String>(reasons));
        }
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder()
            .append(ruleClass.getSimpleName())
            .append(": ")
            .append(ok ? "OK" : "NOT OK:");
        for (String reason : reasons) {
            buffer.append("\n    ").append(reason);
        }
        return buffer.toString();
    }

}
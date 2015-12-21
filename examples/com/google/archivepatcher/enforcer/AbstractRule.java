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
import java.util.List;

/**
 * Convenience base class for scanners.
 */
public abstract class AbstractRule {
    /**
     * Whether or not the overall result is ok.
     */
    protected boolean ok = true;

    /**
     * The list of reasons why the result is not ok, if any.
     */
    protected List<String> reasons = null;

    /**
     * Mark the result as not ok, with the specified reason.
     * @param resource the resource that wasn't ok, e.g. an entry within an
     * archive
     * @param reason the reason to provide
     */
    protected final void notOk(String resource, String reason) {
        ok = false;
        addReason(resource, reason);
    }

    /**
     * Add the reason to the list.
     * @param resource the resource
     * @param reason the reason the resource is in the list
     */
    private final void addReason(String resource, String reason) {
        if (reason != null) {
            if (reasons == null) {
                reasons = new ArrayList<String>(1);
            }
            reasons.add(resource + ": " + reason);
        }
    }

    /**
     * Subclasses override this method to do interesting work. Protected fields
     * provide access to all the necessary data. Generally, subclasses should
     * scan the entry and make calls to {@link #notOk(String, String)} to report
     * errors. Scanners should report as many errors at once as possible
     * instead of bailing out after the first one.
     * <p>
     * The result of the scan defaults to ok.
     */
    protected abstract void checkInternal();
}
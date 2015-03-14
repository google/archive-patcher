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

package com.google.archivepatcher;

/**
 * Information about how a specific deflate algorithm was run.
 */
public class DeflateInfo {

    /**
     * Catch-all for algorithms whose results are reproducible by this
     * library.
     */
    public final static String UNKNOWN_DEFLATE =
        "unknown_deflate";

    /**
     * The implementation name for an algorithm whose results can be
     * reproduced by the default Sun/Oracle JRE implementation of "deflate".
     */
    public final static String JRE_DEFLATE =
        "jre_deflate_or_similar";

    /**
     * Whether or not the info represents a successful match.
     */
    public final boolean matched;

    /**
     * The implementation of deflate that was run.
     */
    public final String implementationName;

    /**
     * Parameters with which the implementation was run.
     */
    public final String implementationParameters;

    /**
     * Creates a new info object with the specified values.
     * @param matched whether or not the deflate output was successfully
     * matched
     * @param implementationName the name of the implementation, or unknown
     * @param implementationParameters the parameters that produced the
     * output of the deflate operation
     */
    public DeflateInfo(boolean matched, String implementationName,
        String implementationParameters) {
        this.matched = matched;
        this.implementationName = implementationName;
        this.implementationParameters = implementationParameters;
    }

    @Override
    public String toString() {
        return matched + "," + implementationName + "," + implementationParameters;
    }
}
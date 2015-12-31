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

package com.google.archivepatcher.tools.reassembler;

import java.io.File;

/**
 * The result of a single reassembly request.
 */
public final class ReassemblyResult {
    /**
     * The input file that was to be reassembled.
     */
    public final File inputFile;

    /**
     * The reassembled output.
     */
    public final File outputFile;

    /**
     * True if verification of the reassembly was requested.
     */
    public final boolean verificationRequested;

    /**
     * True if verification of the reassembly was requested and succeeded.
     */
    public final boolean verificationSucceeded;

    /**
     * If verification was requested, the SHA-256 of the original input file
     */
    public final String inputSHA256;

    /**
     * If verification was requested, the SHA-256 of the reassembled file
     */
    public final String reassembledSHA256;

    /**
     * Any error that occurred during reassembly.
     */
    public final Throwable error;

    /**
     * Stats for the reassembly task.
     */
    public final ReassemblyStats stats;

    /**
     * Create a new result with the specified data. See fields for more
     * information.
     * @param inputFile see fields
     * @param outputFile see fields
     * @param verificationRequested see fields
     * @param verificationSucceeded see fields
     * @param inputSHA256 see fields
     * @param reassembledSHA256 see fields
     * @param error see fields
     * @param stats see fields
     */
    public ReassemblyResult(final File inputFile, final File outputFile,
        final boolean verificationRequested,
        final boolean verificationSucceeded,
        final String inputSHA256,
        final String reassembledSHA256,
        final Throwable error, final ReassemblyStats stats) {
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        this.verificationRequested = verificationRequested;
        this.verificationSucceeded = verificationSucceeded;
        this.inputSHA256 = inputSHA256;
        this.reassembledSHA256 = reassembledSHA256;
        this.error = error;
        this.stats = stats;
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer
            .append("Input file:             ")
            .append(inputFile.getAbsolutePath())
            .append("\nOutput file:            ")
            .append(outputFile.getAbsolutePath())
            .append("\nVerification requested: ")
            .append(verificationRequested)
            .append("\nVerification succeeded: ")
            .append(verificationSucceeded)
            .append("\nInput file SHA-256:     ")
            .append(verificationRequested ? inputSHA256 : "Not computed")
            .append("\nOutput file SHA-256:    ")
            .append(verificationRequested ? reassembledSHA256 : "Not computed")
            .append("\nError message (if any): ")
            .append(error == null ? "None" : error.getMessage())
            .append("\nStats:\n").append(stats).append("\n");
        return buffer.toString();
    }
}
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

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Collects a batch of reassembly results.
 */
public final class ReassemblyBatchResult {
    private final ReassemblyStats aggregateStats = new ReassemblyStats();
    private final ReassemblyStats successStats = new ReassemblyStats();
    private final ReassemblyStats failureStats = new ReassemblyStats();
    private final Map<String, ReassemblyResult> allResultsByInputFilePath =
        new TreeMap<String, ReassemblyResult>();
    private final Map<String, ReassemblyResult> successesByInputFilePath =
        new TreeMap<String, ReassemblyResult>();
    private final Map<String, ReassemblyResult> failuresByInputFilePath =
        new TreeMap<String, ReassemblyResult>();

    /**
     * Append a single result to the batch.
     * @param result the result to append
     */
    public void append(ReassemblyResult result) {
        allResultsByInputFilePath.put(
            result.inputFile.getAbsolutePath(), result);
        if (result.stats != null) {
            aggregateStats.accumulate(result.stats);
        }
        boolean isFailure = false;
        if (result.verificationRequested && !result.verificationSucceeded) {
            isFailure = true;
        }
        if (result.error != null) {
            isFailure = true;
        }
        if (isFailure) {
            failuresByInputFilePath.put(
                result.inputFile.getAbsolutePath(), result);
            if (result.stats != null) {
                failureStats.accumulate(result.stats);
            }
        } else {
            successesByInputFilePath.put(
                result.inputFile.getAbsolutePath(), result);
            if (result.stats != null) {
                successStats.accumulate(result.stats);
            }
        }
    }

    /**
     * Returns a live, unmodifiable view of the results for the batch.
     * Keys are the paths to the input file, values are the results for that
     * input file.
     * @return as described
     */
    public Map<String, ReassemblyResult> getAllResultsByInputFilePath() {
        return Collections.unmodifiableMap(allResultsByInputFilePath);
    }

    /**
     * Returns a live, unmodifiable view of the successful results for the
     * batch. Failures are omitted.
     * Keys are the paths to the input file, values are the results for that
     * input file.
     * @return as described
     */
    public Map<String, ReassemblyResult> getSuccessesByInputFilePath() {
        return Collections.unmodifiableMap(successesByInputFilePath);
    }

    /**
     * Returns a live, unmodifiable view of the failure results for the
     * batch. Successes are omitted.
     * Keys are the paths to the input file, values are the results for that
     * input file.
     * @return as described
     */
    public Map<String, ReassemblyResult> getFailuresByInputFilePath() {
        return Collections.unmodifiableMap(failuresByInputFilePath);
    }

    /**
     * Return a live, modifiable view of the aggregate statistics for the
     * result.
     * @return as described
     */
    public ReassemblyStats getAggregateStats() {
        return aggregateStats;
    }

    /**
     * Return a live, modifiable view of the aggregate statistics for the
     * result for successful tasks only.
     * @return as described
     */
    public ReassemblyStats getSuccessStats() {
        return successStats;
    }

    /**
     * Return a live, modifiable view of the aggregate statistics for the
     * result for failed tasks only.
     * @return as described
     */
    public ReassemblyStats getFailureStats() {
        return failureStats;
    }

    @Override
    public String toString() {
        if (allResultsByInputFilePath.size() == 0) {
            return "No results";
        }
        // Special case for 1 archive, the base case, where aggregation is
        // useless noise.
        if (allResultsByInputFilePath.size() == 1) {
            return allResultsByInputFilePath.entrySet().iterator().next()
                .getValue().toString();
        }

        StringBuilder buffer = new StringBuilder();

        // First the high-level aggregate stats.
        buffer.append(aggregateStats);

        // If successes != failures, it's worth separating them out.
        if (successesByInputFilePath.size() != failuresByInputFilePath.size()) {
            if (successesByInputFilePath.size() > 0) {
                buffer.append("\nSuccesses only:\n").append(successStats);
            }
            if (failuresByInputFilePath.size() > 0) {
                buffer.append("\nFailures only:\n").append(failureStats);
            }
        }

        if (successesByInputFilePath.size() > 0) {
            buffer.append("\n\n");
            buffer.append(
                "Detailed stats for successful archives, by input path:");
            for (Map.Entry<String, ReassemblyResult> entry :
                successesByInputFilePath.entrySet()) {
                String path = entry.getKey();
                ReassemblyResult result = entry.getValue();
                buffer.append("Path: ").append(path).append(":\n")
                    .append(result.toString()).append("\n\n");
            }
        }

        if (failuresByInputFilePath.size() > 0) {
            buffer.append("\n\n");
            buffer.append(
                "Detailed stats for failed archives, by input path:");
            for (Map.Entry<String, ReassemblyResult> entry :
                failuresByInputFilePath.entrySet()) {
                String path = entry.getKey();
                ReassemblyResult result = entry.getValue();
                buffer.append("Path: ").append(path).append(":\n")
                    .append(result.toString()).append("\n\n");
            }
        }
        return buffer.toString();
    }
}
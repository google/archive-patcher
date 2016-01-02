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

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.LinkedList;
import java.util.List;

/**
 * Reassembles an archive on-demand.
 */
public class ReassemblerService extends IntentService {
    private static final String TAG = "ReassemblerService";

    private static final String ACTION_REASSEMBLE =
            "com.google.archivepatcher.tools.reassembler.action.REASSEMBLE";

    private static final String INPUT_ARCHIVE_PARAM =
            "com.google.archivepatcher.tools.reassembler.extra.INPUT_ARCHIVE";
    private static final String DIRECTIVES_DIR_PARAM =
            "com.google.archivepatcher.tools.reassembler.extra.DIRECTIVES_DIR";
    private static final String OUTPUT_DIR_PARAM =
            "com.google.archivepatcher.tools.reassembler.extra.OUTPUT_DIR";
    private static final String VERIFY_PARAM =
            "com.google.archivepatcher.tools.reassembler.extra.VERIFY";

    public ReassemblerService() {
        super("ReassemblerService");
    }

    /**
     * Starts this service to perform a reassemble operation with the given parameters. If the
     * service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionReassemble(Context context, String inputArchive,
                                             String directivesDir, String outputDir,
                                             boolean verify) {
        Intent intent = new Intent(context, ReassemblerService.class);
        intent.setAction(ACTION_REASSEMBLE);
        intent.putExtra(INPUT_ARCHIVE_PARAM, inputArchive);
        intent.putExtra(DIRECTIVES_DIR_PARAM, directivesDir);
        intent.putExtra(OUTPUT_DIR_PARAM, outputDir);
        intent.putExtra(VERIFY_PARAM, verify);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) return;
        if (!ACTION_REASSEMBLE.equals(intent.getAction())) return;

        final String inputArchive = intent.getStringExtra(INPUT_ARCHIVE_PARAM);
        if (inputArchive == null) {
            Log.e(TAG, "Missing param: " + INPUT_ARCHIVE_PARAM);
            return;
        }
        final String directivesDir = intent.getStringExtra(DIRECTIVES_DIR_PARAM);
        if (directivesDir == null) {
            Log.e(TAG, "Missing param: " + DIRECTIVES_DIR_PARAM);
            return;
        }
        final String outputDir = intent.getStringExtra(OUTPUT_DIR_PARAM);
        if (outputDir == null) {
            Log.e(TAG, "Missing param: " + OUTPUT_DIR_PARAM);
            return;
        }
        if (!intent.hasExtra(VERIFY_PARAM)) {
            Log.e(TAG, "Missing param: " + VERIFY_PARAM);
            return;
        }
        final boolean verify = intent.getBooleanExtra(VERIFY_PARAM, false);
        handleActionReassemble(
                inputArchive, directivesDir, outputDir, verify);
    }

    private File safeFile(String path) {
        if (path.startsWith("//")) {
            path = path.substring(1);
        }
        final File externalFilesDir = Environment.getExternalStorageDirectory();
        String[] normalizablePaths = new String[] {
                "/sdcard/",
                "/mnt/sdcard/"
        };
        for (String normalizablePath : normalizablePaths) {
            if (path.startsWith(normalizablePath)) {
                return new File(externalFilesDir,
                    path.substring(normalizablePath.length()));
            }
        }
        return new File(path);
    }

    /**
     * Perform the specified reassembly work.
     */
    private void handleActionReassemble(String inputArchivePath,
        String directivesDirPath, String outputDirPath, boolean verify) {
        final File inputArchive = safeFile(inputArchivePath);
        final File directivesDir = safeFile(directivesDirPath);
        final File outputDir = safeFile(outputDirPath);
        Log.i(TAG, "Starting reassembly of " + inputArchive.getAbsolutePath() +
                " with directives in " + directivesDir.getAbsolutePath() +
                ", outputting to " + outputDir.getAbsolutePath());
        final File statsOutputFile = new File(
            outputDir, inputArchive.getName() + ".stats");
        final File tempStatsOutputFile = new File(
            outputDir, inputArchive.getName() + ".stats.temp");
        final File statsCsvOutputFile = new File(
                outputDir, inputArchive.getName() + ".stats.csv");
        final File tempStatsCsvOutputFile = new File(
                outputDir, inputArchive.getName() + ".stats.csv.temp");
        final List<File> archives = new LinkedList<>();
        archives.add(inputArchive);
        FileWriter statsOut = null;
        FileWriter statsCsvOut = null;
        try {
            Reassembler reassembler = new Reassembler();
            ReassemblyBatchResult result = reassembler.reassemble(
                    archives, 1, outputDir, directivesDir, verify);

            // Dump stats to file
            statsOut = new FileWriter(tempStatsOutputFile);
            statsOut.write(result.toString());
            statsOut.flush();
            tempStatsOutputFile.renameTo(statsOutputFile);

            // Dump csv stats to file
            statsCsvOut = new FileWriter(tempStatsCsvOutputFile);
            statsCsvOut.write(result.toSimplifiedCsv(true /* output header */));
            statsCsvOut.write("\n"); // For convenience in batch processing of CSV files
            statsCsvOut.flush();
            tempStatsCsvOutputFile.renameTo(statsCsvOutputFile);
            Log.i(TAG, "Reassembly completed in " +
                    result.getAggregateStats().totalMillisRebuilding + "ms");
        } catch (Exception e) {
            Log.e(TAG, "Reassembly failed.", e);
        } finally {
            if (statsOut != null) {
                try {
                    statsOut.close();
                } catch (Exception ignored) {
                    // Nothing to be done.
                }
            }
            if (statsCsvOut != null) {
                try {
                    statsCsvOut.close();
                } catch (Exception ignored) {
                    // Nothing to be done.
                }
            }
        }
    }
}

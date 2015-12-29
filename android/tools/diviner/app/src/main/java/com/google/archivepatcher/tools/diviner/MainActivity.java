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

package com.google.archivepatcher.tools.diviner;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CompatibilityDiviner";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        new CompatibilityCheck().execute();
    }

    private static final class CompatibilityResult {
        public final boolean isCompatible;
        public final String details;
        public CompatibilityResult(final boolean isCompatible, final String details) {
            this.isCompatible = isCompatible;
            this.details = details;
        }
    }

    class CompatibilityCheck extends AsyncTask<Void, Void, CompatibilityResult> {
        @Override
        protected CompatibilityResult doInBackground(Void... params) {
            CompressionDivinerCompatibilityCheck checker =
                    new CompressionDivinerCompatibilityCheck();
            try {
                StringBuilder buffer = new StringBuilder();
                boolean isCompatible = checker.isCompatible(buffer);
                return new CompatibilityResult(isCompatible, buffer.toString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected void onPostExecute(final CompatibilityResult result) {
            super.onPostExecute(result);
            final String message = result.isCompatible ? "Compatible!" : "Incompatible!";
            Log.i(TAG, message);
            if (result.details != null) {
                Log.v(TAG, result.details);
            }
            TextView mainTextView = (TextView) findViewById(R.id.main_text);
            mainTextView.append(message);
            final String description = Build.MANUFACTURER + " " +
                    Build.MODEL + " / SDK " + Build.VERSION.SDK_INT + " (" +
                    Build.CPU_ABI + ", " + Build.TYPE + ")";
            ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
            progressBar.setIndeterminate(false);
            Button button = (Button) findViewById(R.id.button);
            button.setEnabled(true);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_SENDTO);
                    intent.setData(Uri.parse("mailto:"));
                    intent.putExtra(Intent.EXTRA_SUBJECT,
                            "ArchivePatcher Compatibility Result: " + message);
                    String text = description;
                    if (result.details != null) {
                        text += "\nDetails:\n" + result.details;
                    } else {
                        text += "\nNo details provided.";
                    }
                    intent.putExtra(Intent.EXTRA_TEXT, text);
                    if (intent.resolveActivity(getPackageManager()) != null) {
                        startActivity(intent);
                    } else {
                        Toast.makeText(getApplicationContext(), "No email handler available.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }
}

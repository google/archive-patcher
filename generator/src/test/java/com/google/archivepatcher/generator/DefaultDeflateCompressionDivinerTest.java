// Copyright 2016 Google LLC. All rights reserved.
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

package com.google.archivepatcher.generator;

import static com.google.archivepatcher.shared.PatchConstants.CompressionMethod.DEFLATE;
import static com.google.archivepatcher.shared.PatchConstants.CompressionMethod.STORED;
import static com.google.common.truth.Truth.assertThat;

import com.google.archivepatcher.generator.DefaultDeflateCompressionDiviner.DivinationResult;
import com.google.archivepatcher.shared.DefaultDeflateCompatibilityWindow;
import com.google.archivepatcher.shared.DeflateCompressor;
import com.google.archivepatcher.shared.JreDeflateParameters;
import com.google.archivepatcher.shared.UnitTestZipArchive;
import com.google.archivepatcher.shared.UnitTestZipEntry;
import com.google.archivepatcher.shared.bytesource.ByteSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import static org.junit.Assume.assumeTrue;

/** Tests for {@link DefaultDeflateCompressionDiviner}. */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class DefaultDeflateCompressionDivinerTest {

  /**
   * Test delivery written to the file.
   */
  private byte[] testData = null;

  @Before
  public void setup() {
    // TODO: fix compatibility in OpenJDK 1.8 (or higher)
    assumeTrue(new DefaultDeflateCompatibilityWindow().isCompatible());

    testData = new DefaultDeflateCompatibilityWindow().getCorpus();
  }

  /**
   * Deflates the test delivery using the specified parameters, storing them in a temp file and
   * returns the temp file created.
   * @param parameters the parameters to use for deflating
   * @return the temp file with the delivery
   */
  private byte[] deflate(JreDeflateParameters parameters) throws IOException {
    DeflateCompressor compressor = new DeflateCompressor();
    compressor.setNowrap(parameters.nowrap);
    compressor.setStrategy(parameters.strategy);
    compressor.setCompressionLevel(parameters.level);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    compressor.compress(new ByteArrayInputStream(testData), buffer);
    return buffer.toByteArray();
  }

  @Test
  public void testDivineDeflateParameters_JunkData() throws IOException {
    final byte[] junk = new byte[] {0, 1, 2, 3, 4};
    try (ByteSource entry = ByteSource.wrap(junk)) {
      assertThat(DefaultDeflateCompressionDiviner.divineDeflateParametersForEntry(entry)).isNull();
    }
  }

  @Test
  public void testDivineDeflateParameters_AllValidSettings() throws IOException {
    for (boolean nowrap : new boolean[] {true, false}) {
      for (int strategy : new int[] {0, 1, 2}) {
        for (int level : new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9}) {
          JreDeflateParameters trueParameters = JreDeflateParameters.of(level, strategy, nowrap);
          final byte[] buffer = deflate(trueParameters);
          JreDeflateParameters divinedParameters;
          try (ByteSource byteSource = ByteSource.wrap(buffer)) {
            divinedParameters =
                DefaultDeflateCompressionDiviner.divineDeflateParametersForEntry(byteSource);
          }
          assertThat(divinedParameters).isNotNull();
          // TODO make *CERTAIN 100%( that strategy doesn't matter for level < 4.
          if (strategy == 1 && level <= 3) {
            // Strategy 1 produces identical output at levels 1, 2 and 3.
            assertThat(divinedParameters).isEqualTo(JreDeflateParameters.of(level, 0, nowrap));
          } else if (strategy == 2) {
            // All levels are the same with strategy 2.
            // TODO: Assert only one test gets done for this, should be the first level always.
            assertThat(divinedParameters.nowrap).isEqualTo(nowrap);
            assertThat(divinedParameters.strategy).isEqualTo(strategy);
          } else {
            assertThat(divinedParameters).isEqualTo(trueParameters);
          }
        } // End of iteration on level
      } // End of iteration on strategy
    } // End of iteration on nowrap
  }

  @Test
  public void testDivineDeflateParameters_File() throws IOException {
    File tempFile = File.createTempFile("ddcdt", "tmp");
    tempFile.deleteOnExit();
    UnitTestZipArchive.saveTestZip(tempFile);

    try (ByteSource tempBlob = ByteSource.fromFile(tempFile)) {
      List<DivinationResult> results =
          DefaultDeflateCompressionDiviner.divineDeflateParameters(tempBlob);
      assertThat(results).hasSize(UnitTestZipArchive.ALL_ENTRIES.size());
      for (int x = 0; x < results.size(); x++) {
        UnitTestZipEntry expected = UnitTestZipArchive.ALL_ENTRIES.get(x);
        DivinationResult actual = results.get(x);
        assertThat(actual.minimalZipEntry.getFileName()).isEqualTo(expected.path);
        int expectedLevel = expected.level;
        if (expectedLevel > 0) {
          // Compressed entry
          assertThat(actual.minimalZipEntry.compressionMethod()).isEqualTo(DEFLATE);
          assertThat(actual.divinedParameters).isNotNull();
          assertThat(actual.divinedParameters.level).isEqualTo(expectedLevel);
          assertThat(actual.divinedParameters.strategy).isEqualTo(0);
          assertThat(actual.divinedParameters.nowrap).isTrue();
        } else {
          // Uncompressed entry
          assertThat(actual.minimalZipEntry.compressionMethod()).isEqualTo(STORED);
          assertThat(actual.divinedParameters).isNull();
        }
      }
    } finally {
      try {
        tempFile.delete();
      } catch (Exception ignoreD) {
        // Nothing
      }
    }
  }
}

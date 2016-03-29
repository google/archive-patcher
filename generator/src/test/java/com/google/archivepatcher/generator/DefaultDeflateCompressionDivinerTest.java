// Copyright 2016 Google Inc. All rights reserved.
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

import com.google.archivepatcher.generator.DefaultDeflateCompressionDiviner.DivinationResult;
import com.google.archivepatcher.shared.DefaultDeflateCompatibilityWindow;
import com.google.archivepatcher.shared.DeflateCompressor;
import com.google.archivepatcher.shared.JreDeflateParameters;
import com.google.archivepatcher.shared.MultiViewInputStreamFactory;
import com.google.archivepatcher.shared.UnitTestZipArchive;
import com.google.archivepatcher.shared.UnitTestZipEntry;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Tests for {@link DefaultDeflateCompressionDiviner}.
 */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class DefaultDeflateCompressionDivinerTest {
  /**
   * The object under test.
   */
  private DefaultDeflateCompressionDiviner diviner = null;

  /**
   * Test data written to the file.
   */
  private byte[] testData = null;

  @Before
  public void setup() {
    testData = new DefaultDeflateCompatibilityWindow().getCorpus();
    diviner = new DefaultDeflateCompressionDiviner();
  }

  /**
   * Deflates the test data using the specified parameters, storing them in a temp file and
   * returns the temp file created.
   * @param parameters the parameters to use for deflating
   * @return the temp file with the data
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

  private static class ByteArrayInputStreamFactory
      implements MultiViewInputStreamFactory<ByteArrayInputStream> {
    private final byte[] data;
    private final boolean supportMark;
    private final boolean dieOnClose;

    /**
     * Create a factory the returns streams on the specified data buffer, optionally supporting
     * {@link InputStream#mark(int)}.
     * @param data the data buffer to return streams for
     * @param supportMark whether or not to support marking
     * @param dieOnClose whether or not to throw nasty exceptions on close()
     */
    public ByteArrayInputStreamFactory(byte[] data, boolean supportMark, boolean dieOnClose) {
      this.data = data;
      this.supportMark = supportMark;
      this.dieOnClose = dieOnClose;
    }

    @Override
    public ByteArrayInputStream newStream() throws IOException {
      return new ByteArrayInputStream(data) {
        @Override
        public boolean markSupported() {
          return supportMark;
        }

        @Override
        public void close() throws IOException {
          if (dieOnClose) {
            throw new IOException("brainnnnnnnnnnssssss!");
          }
          super.close();
        }
      };
    }
  }

  @Test
  public void testDivineDeflateParameters_NoMarkInputStreamFactory() throws IOException {
    final JreDeflateParameters parameters = JreDeflateParameters.of(1, 0, true);
    final byte[] buffer = deflate(parameters);
    try {
      // The factory here will NOT support mark(int), which should cause failure. Also, throw
      // exceptions on close() to be extra rude.
      diviner.divineDeflateParameters(new ByteArrayInputStreamFactory(buffer, false, true));
      Assert.fail("operating without a markable stream");
    } catch (IllegalArgumentException expected) {
      // Correct!
    }
  }

  @Test
  public void testDivineDeflateParameters_BadCloseInputStreamFactory() throws IOException {
    final JreDeflateParameters parameters = JreDeflateParameters.of(1, 0, true);
    final byte[] buffer = deflate(parameters);
    // The factory here will produce streams that throw exceptions when close() is called.
    // These exceptions should be ignored.
    JreDeflateParameters result =
        diviner.divineDeflateParameters(new ByteArrayInputStreamFactory(buffer, true, true));
    Assert.assertEquals(result, parameters);
  }

  @Test
  public void testDivineDeflateParameters_JunkData() throws IOException {
    final byte[] junk = new byte[] {0, 1, 2, 3, 4};
    Assert.assertNull(
        diviner.divineDeflateParameters(new ByteArrayInputStreamFactory(junk, true, false)));
  }

  @Test
  public void testDivineDeflateParameters_AllValidSettings() throws IOException {
    for (boolean nowrap : new boolean[] {true, false}) {
      for (int strategy : new int[] {0, 1, 2}) {
        for (int level : new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9}) {
          JreDeflateParameters trueParameters = JreDeflateParameters.of(level, strategy, nowrap);
          final byte[] buffer = deflate(trueParameters);
          ByteArrayInputStreamFactory factory =
              new ByteArrayInputStreamFactory(buffer, true, false);
          JreDeflateParameters divinedParameters = diviner.divineDeflateParameters(factory);
          Assert.assertNotNull(divinedParameters);
          // TODO(andrewhayden) make *CERTAIN 100%( that strategy doesn't matter for level < 4.
          if (strategy == 1 && level <= 3) {
            // Strategy 1 produces identical output at levels 1, 2 and 3.
            Assert.assertEquals(JreDeflateParameters.of(level, 0, nowrap), divinedParameters);
          } else if (strategy == 2) {
            // All levels are the same with strategy 2.
            // TODO: Assert only one test gets done for this, should be the first level always.
            Assert.assertEquals(nowrap, divinedParameters.nowrap);
            Assert.assertEquals(strategy, divinedParameters.strategy);
          } else {
            Assert.assertEquals(trueParameters, divinedParameters);
          }
        } // End of iteration on level
      } // End of iteration on strategy
    } // End of iteration on nowrap
  }

  @Test
  public void testDivineDeflateParameters_File() throws IOException {
    File tempFile = File.createTempFile("ddcdt", "tmp");
    tempFile.deleteOnExit();
    try {
      UnitTestZipArchive.saveTestZip(tempFile);
      List<DivinationResult> results = diviner.divineDeflateParameters(tempFile);
      Assert.assertEquals(UnitTestZipArchive.allEntriesInFileOrder.size(), results.size());
      for (int x = 0; x < results.size(); x++) {
        UnitTestZipEntry expected = UnitTestZipArchive.allEntriesInFileOrder.get(x);
        DivinationResult actual = results.get(x);
        Assert.assertEquals(expected.path, actual.minimalZipEntry.getFileName());
        int expectedLevel = expected.level;
        if (expectedLevel > 0) {
          // Compressed entry
          Assert.assertTrue(actual.minimalZipEntry.isDeflateCompressed());
          Assert.assertNotNull(actual.divinedParameters);
          Assert.assertEquals(expectedLevel, actual.divinedParameters.level);
          Assert.assertEquals(0, actual.divinedParameters.strategy);
          Assert.assertTrue(actual.divinedParameters.nowrap);
        } else {
          // Uncompressed entry
          Assert.assertFalse(actual.minimalZipEntry.isDeflateCompressed());
          Assert.assertNull(actual.divinedParameters);
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

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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.US_ASCII;

import com.google.archivepatcher.shared.JreDeflateParameters;
import com.google.archivepatcher.shared.PatchConstants;
import com.google.archivepatcher.shared.TypedRange;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link PatchWriter}.
 */
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class PatchWriterTest {
  // This is Integer.MAX_VALUE + 1.
  private static final long BIG = 2048L * 1024L * 1024L;

  private static final JreDeflateParameters DEFLATE_PARAMS = JreDeflateParameters.of(6, 0, true);

  private static final TypedRange<Void> OLD_DELTA_FRIENDLY_UNCOMPRESS_RANGE =
      new TypedRange<>(BIG, 17L, null);

  private static final TypedRange<JreDeflateParameters> NEW_DELTA_FRIENDLY_UNCOMPRESS_RANGE =
      new TypedRange<>(BIG - 100L, BIG, DEFLATE_PARAMS);

  private static final TypedRange<JreDeflateParameters> NEW_DELTA_FRIENDLY_RECOMPRESS_RANGE =
      new TypedRange<>(BIG, BIG, DEFLATE_PARAMS);

  private static final List<TypedRange<Void>> OLD_DELTA_FRIENDLY_UNCOMPRESS_PLAN =
      Collections.singletonList(OLD_DELTA_FRIENDLY_UNCOMPRESS_RANGE);

  private static final List<TypedRange<JreDeflateParameters>> NEW_DELTA_FRIENDLY_UNCOMPRESS_PLAN =
      Collections.singletonList(NEW_DELTA_FRIENDLY_UNCOMPRESS_RANGE);

  private static final List<TypedRange<JreDeflateParameters>> NEW_DELTA_FRIENDLY_RECOMPRESS_PLAN =
      Collections.singletonList(NEW_DELTA_FRIENDLY_RECOMPRESS_RANGE);

  private static final long DELTA_FRIENDLY_OLD_FILE_SIZE = BIG - 75L;

  private static final long DELTA_FRIENDLY_NEW_FILE_SIZE = BIG + 75L;

  private static final PreDiffPlan PLAN =
      new PreDiffPlan(
          Collections.emptyList(),
          OLD_DELTA_FRIENDLY_UNCOMPRESS_PLAN,
          NEW_DELTA_FRIENDLY_UNCOMPRESS_PLAN,
          NEW_DELTA_FRIENDLY_RECOMPRESS_PLAN);

  private static final String DELTA_CONTENT = "this is a really cool delta, woo";

  private File deltaFile = null;

  private ByteArrayOutputStream buffer = null;

  @Before
  public void setup() throws IOException {
    buffer = new ByteArrayOutputStream();
    deltaFile = File.createTempFile("patchwritertest", "delta");
    deltaFile.deleteOnExit();
    try (FileOutputStream out = new FileOutputStream(deltaFile)) {
      out.write(DELTA_CONTENT.getBytes());
      out.flush();
    }
  }

  @After
  public void tearDown() {
    deltaFile.delete();
  }

  @Test
  public void testWriteV1Patch() throws IOException {
    // ---------------------------------------------------------------------------------------------
    // CAUTION - DO NOT CHANGE THIS FUNCTION WITHOUT DUE CONSIDERATION FOR BREAKING THE PATCH FORMAT
    // ---------------------------------------------------------------------------------------------
    // This test writes a simple patch with all the static data listed above and verifies it.
    // This code MUST be INDEPENDENT of the real patch parser code, even if it is partially
    // redundant; this guards against accidental changes to the patch writer that could alter the
    // format and otherwise escape detection.
    PatchWriter writer =
        new PatchWriter(
            PLAN, DELTA_FRIENDLY_OLD_FILE_SIZE, DELTA_FRIENDLY_NEW_FILE_SIZE, deltaFile);
    writer.writePatch(buffer);
    DataInputStream patchIn = new DataInputStream(new ByteArrayInputStream(buffer.toByteArray()));
    byte[] eightBytes = new byte[8];

    // Start by reading the signature and flags
    patchIn.readFully(eightBytes);
    assertThat(eightBytes).isEqualTo(PatchConstants.IDENTIFIER.getBytes(US_ASCII));
    assertThat(patchIn.readInt()).isEqualTo(0); // Flags, all reserved in v1

    assertThat(patchIn.readLong()).isEqualTo(DELTA_FRIENDLY_OLD_FILE_SIZE);

    // Read the uncompression instructions
    assertThat(patchIn.readInt()).isEqualTo(1); // Number of old archive uncompression instructions
    assertThat(patchIn.readLong()).isEqualTo(OLD_DELTA_FRIENDLY_UNCOMPRESS_RANGE.getOffset());
    assertThat(patchIn.readLong()).isEqualTo(OLD_DELTA_FRIENDLY_UNCOMPRESS_RANGE.getLength());

    // Read the recompression instructions
    assertThat(patchIn.readInt()).isEqualTo(1); // Number of new archive recompression instructions
    assertThat(patchIn.readLong()).isEqualTo(NEW_DELTA_FRIENDLY_RECOMPRESS_RANGE.getOffset());
    assertThat(patchIn.readLong()).isEqualTo(NEW_DELTA_FRIENDLY_RECOMPRESS_RANGE.getLength());
    // Now the JreDeflateParameters for the record
    assertThat(patchIn.read())
        .isEqualTo(PatchConstants.CompatibilityWindowId.DEFAULT_DEFLATE.patchValue);
    assertThat(patchIn.read()).isEqualTo(DEFLATE_PARAMS.level);
    assertThat(patchIn.read()).isEqualTo(DEFLATE_PARAMS.strategy);
    assertThat(patchIn.read()).isEqualTo(DEFLATE_PARAMS.nowrap ? 1 : 0);

    // Delta section. V1 patches have exactly one delta entry and it is always mapped to the entire
    // file contents of the delta-friendly archives
    assertThat(patchIn.readInt()).isEqualTo(1); // Number of difference records
    assertThat(patchIn.read()).isEqualTo(PatchConstants.DeltaFormat.BSDIFF.patchValue);
    assertThat(patchIn.readLong()).isEqualTo(0); // Old delta-friendly range start
    assertThat(patchIn.readLong()).isEqualTo(DELTA_FRIENDLY_OLD_FILE_SIZE); // old range length
    assertThat(patchIn.readLong()).isEqualTo(0); // New delta-friendly range start
    assertThat(patchIn.readLong()).isEqualTo(DELTA_FRIENDLY_NEW_FILE_SIZE); // new range length
    byte[] expectedDeltaContent = DELTA_CONTENT.getBytes("US-ASCII");
    assertThat(patchIn.readLong()).isEqualTo(expectedDeltaContent.length);
    byte[] actualDeltaContent = new byte[expectedDeltaContent.length];
    patchIn.readFully(actualDeltaContent);
    assertThat(actualDeltaContent).isEqualTo(expectedDeltaContent);
  }
}

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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.google.archivepatcher.shared.JreDeflateParameters;
import com.google.archivepatcher.shared.PatchConstants;
import com.google.archivepatcher.shared.PatchConstants.CompatibilityWindowId;
import com.google.archivepatcher.shared.PatchConstants.DeltaFormat;
import com.google.archivepatcher.shared.Range;
import com.google.archivepatcher.shared.TypedRange;
import com.google.archivepatcher.shared.bytesource.ByteSource;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
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

  private static final Range OLD_DELTA_FRIENDLY_UNCOMPRESS_RANGE = Range.of(BIG, 17L);

  private static final TypedRange<JreDeflateParameters> NEW_DELTA_FRIENDLY_UNCOMPRESS_RANGE =
      TypedRange.of(BIG - 100L, BIG, DEFLATE_PARAMS);

  private static final TypedRange<JreDeflateParameters> NEW_DELTA_FRIENDLY_RECOMPRESS_RANGE =
      TypedRange.of(BIG, BIG, DEFLATE_PARAMS);

  private static final ImmutableList<Range> OLD_DELTA_FRIENDLY_UNCOMPRESS_PLAN =
      ImmutableList.of(OLD_DELTA_FRIENDLY_UNCOMPRESS_RANGE);

  private static final ImmutableList<TypedRange<JreDeflateParameters>>
      NEW_DELTA_FRIENDLY_UNCOMPRESS_PLAN = ImmutableList.of(NEW_DELTA_FRIENDLY_UNCOMPRESS_RANGE);

  private static final ImmutableList<TypedRange<JreDeflateParameters>>
      NEW_DELTA_FRIENDLY_RECOMPRESS_PLAN = ImmutableList.of(NEW_DELTA_FRIENDLY_RECOMPRESS_RANGE);

  private static final long DELTA_FRIENDLY_OLD_FILE_SIZE = BIG - 75L;

  private static final PreDiffPlan PLAN =
      new PreDiffPlan(
          Collections.emptyList(),
          OLD_DELTA_FRIENDLY_UNCOMPRESS_PLAN,
          NEW_DELTA_FRIENDLY_UNCOMPRESS_PLAN,
          NEW_DELTA_FRIENDLY_RECOMPRESS_PLAN);

  private static final String[] DELTA_CONTENTS =
      new String[] {"this is a really cool delta, woo", "this is another really cool delta, woo"};
  private static final ImmutableList<DeltaEntry> DELTA_ENTRIES =
      ImmutableList.of(
          new FakeDeltaEntry(DELTA_CONTENTS[0].getBytes(UTF_8)),
          new FakeDeltaEntry(DELTA_CONTENTS[1].getBytes(UTF_8)));

  private ByteArrayOutputStream buffer = null;

  private PatchWriter patchWriter;

  @Before
  public void setup() throws IOException {
    buffer = new ByteArrayOutputStream();

    patchWriter =
        new PatchWriter(
            PLAN,
            DELTA_FRIENDLY_OLD_FILE_SIZE,
            DELTA_ENTRIES,
            mock(ByteSource.class),
            mock(ByteSource.class),
            mock(DeltaGeneratorFactory.class));
  }

  @Test
  public void testWriteV1Patch() throws Exception {
    // ---------------------------------------------------------------------------------------------
    // CAUTION - DO NOT CHANGE THIS FUNCTION WITHOUT DUE CONSIDERATION FOR BREAKING THE PATCH FORMAT
    // ---------------------------------------------------------------------------------------------
    // This test writes a simple patch with all the static data listed above and verifies it.
    // This code MUST be INDEPENDENT of the real patch parser code, even if it is partially
    // redundant; this guards against accidental changes to the patch writer that could alter the
    // format and otherwise escape detection.
    PatchWriter spyPatchWriter = spy(patchWriter);
    // We stub the "writeDeltaEntry" method here and test it separately for test atomicity
    doAnswer(
            invocation -> {
              invocation
                  .getArgument(4, OutputStream.class)
                  .write(invocation.getArgument(0, FakeDeltaEntry.class).delta);
              return null;
            })
        .when(spyPatchWriter)
        .writeDeltaEntry(any(), any(), any(), any(), any());

    spyPatchWriter.writePatch(buffer);
    DataInputStream patchIn = new DataInputStream(new ByteArrayInputStream(buffer.toByteArray()));
    byte[] eightBytes = new byte[8];

    // Start by reading the signature and flags
    patchIn.readFully(eightBytes);
    assertThat(eightBytes).isEqualTo(PatchConstants.IDENTIFIER.getBytes(US_ASCII));
    assertThat(patchIn.readInt()).isEqualTo(0); // Flags, all reserved in v1

    assertThat(patchIn.readLong()).isEqualTo(DELTA_FRIENDLY_OLD_FILE_SIZE);

    // Read the uncompression instructions
    assertThat(patchIn.readInt()).isEqualTo(1); // Number of old archive uncompression instructions
    assertThat(patchIn.readLong()).isEqualTo(OLD_DELTA_FRIENDLY_UNCOMPRESS_RANGE.offset());
    assertThat(patchIn.readLong()).isEqualTo(OLD_DELTA_FRIENDLY_UNCOMPRESS_RANGE.length());

    // Read the recompression instructions
    assertThat(patchIn.readInt()).isEqualTo(1); // Number of new archive recompression instructions
    assertThat(patchIn.readLong()).isEqualTo(NEW_DELTA_FRIENDLY_RECOMPRESS_RANGE.offset());
    assertThat(patchIn.readLong()).isEqualTo(NEW_DELTA_FRIENDLY_RECOMPRESS_RANGE.length());
    // Now the JreDeflateParameters for the record
    assertThat(patchIn.read()).isEqualTo(CompatibilityWindowId.DEFAULT_DEFLATE.patchValue);
    assertThat(patchIn.read()).isEqualTo(DEFLATE_PARAMS.level);
    assertThat(patchIn.read()).isEqualTo(DEFLATE_PARAMS.strategy);
    assertThat(patchIn.read()).isEqualTo(DEFLATE_PARAMS.nowrap ? 1 : 0);

    assertThat(patchIn.readInt()).isEqualTo(DELTA_ENTRIES.size()); // Number of difference records
    // NOTE: here we fake the delta content completely. The actual delta format is tested in
    // DeltaEntryTest.
    for (String deltaContent : DELTA_CONTENTS) {
      byte[] expectedDeltaContent = deltaContent.getBytes(UTF_8);
      // assertThat(patchIn.readLong()).isEqualTo(expectedDeltaContent.length);
      byte[] actualDeltaContent = new byte[expectedDeltaContent.length];
      patchIn.readFully(actualDeltaContent);
      assertThat(actualDeltaContent).isEqualTo(expectedDeltaContent);
    }
  }

  @Test
  public void writeDeltaEntry() throws Exception {
    byte[] oldData = new byte[100];
    byte[] newData = new byte[200];
    // prepare some fake data.
    for (int i = 0; i < oldData.length; i++) {
      oldData[i] = (byte) i;
    }
    for (int i = 0; i < newData.length; i++) {
      newData[i] = (byte) (newData.length - i);
    }
    byte[] expectedOut = new byte[] {1, 2, 3, 4, 5, 6, 7};
    ByteSource oldBlob = ByteSource.wrap(oldData);
    ByteSource newBlob = ByteSource.wrap(newData);
    // Have some random ranges of old data and new data.
    long oldBlobRangeOffset = oldData.length / 3;
    long oldBlobRangeLength = oldData.length / 2;
    long newBlobRangeOffset = newData.length / 3;
    long newBlobRangeLength = newData.length / 2;
    Range oldBlobRange = Range.of(oldBlobRangeOffset, oldBlobRangeLength);
    Range newBlobRange = Range.of(newBlobRangeOffset, newBlobRangeLength);
    FakeDeltaGeneratorFactory deltaGeneratorFactory =
        new FakeDeltaGeneratorFactory(
            ImmutableMap.of(
                DeltaFormat.BSDIFF,
                new FakeDeltaGenerator(
                    oldBlob.slice(oldBlobRange), newBlob.slice(newBlobRange), expectedOut)));

    DeltaEntry entry =
        DeltaEntry.builder()
            .deltaFormat(DeltaFormat.BSDIFF)
            .oldBlobRange(oldBlobRange)
            .newBlobRange(newBlobRange)
            .build();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
      patchWriter.writeDeltaEntry(entry, oldBlob, newBlob, deltaGeneratorFactory, dataOutputStream);
    }

    try (DataInputStream patchIn =
        new DataInputStream(new ByteArrayInputStream(outputStream.toByteArray()))) {
      assertThat(patchIn.read()).isEqualTo(DeltaFormat.BSDIFF.patchValue);
      assertThat(patchIn.readLong()).isEqualTo(oldBlobRangeOffset);
      assertThat(patchIn.readLong()).isEqualTo(oldBlobRangeLength);
      assertThat(patchIn.readLong()).isEqualTo(newBlobRangeOffset);
      assertThat(patchIn.readLong()).isEqualTo(newBlobRangeLength);
      assertThat(patchIn.readLong()).isEqualTo(expectedOut.length);
      byte[] actualDeltaContent = new byte[expectedOut.length];
      patchIn.readFully(actualDeltaContent);
      assertThat(actualDeltaContent).isEqualTo(expectedOut);
    }
  }

  /** A fake delta entry with pre-configured delta data. */
  @SuppressWarnings("ExtendsAutoValue")
  private static class FakeDeltaEntry extends DeltaEntry {
    public final byte[] delta;

    public FakeDeltaEntry(byte[] delta) {
      this.delta = delta;
    }

    // Fake methods.
    @Override
    DeltaFormat deltaFormat() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Range oldBlobRange() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Range newBlobRange() {
      throw new UnsupportedOperationException();
    }
  }

  /** A {@link DeltaGeneratorFactory} that returns the configured delta generators. */
  private static class FakeDeltaGeneratorFactory extends DeltaGeneratorFactory {
    private final Map<DeltaFormat, DeltaGenerator> deltaGenerators;

    public FakeDeltaGeneratorFactory(Map<DeltaFormat, DeltaGenerator> deltaGenerators) {
      // This doesn't matter since we will be returning the configured delta generators.
      super(/* useNativeBsDiff= */ true);
      this.deltaGenerators = deltaGenerators;
    }

    @Override
    public DeltaGenerator create(DeltaFormat deltaFormat) {
      if (deltaGenerators.containsKey(deltaFormat)) {
        return deltaGenerators.get(deltaFormat);
      } else {
        throw new IllegalArgumentException("Unsupported delta format " + deltaFormat);
      }
    }
  }

  /** A {@link DeltaGenerator} that verifies the input and outputs the bytes configured. */
  private static class FakeDeltaGenerator extends DeltaGenerator {
    private final ByteSource expectedOld;
    private final ByteSource expectedNew;
    private final byte[] output;

    public FakeDeltaGenerator(ByteSource expectedOld1, ByteSource expectedNew1, byte[] output1) {
      this.expectedOld = expectedOld1;
      this.expectedNew = expectedNew1;
      this.output = output1;
    }

    @Override
    public void generateDelta(ByteSource oldBlob, ByteSource newBlob, OutputStream deltaOut)
        throws IOException, InterruptedException {
      assertByteSourceEquals(oldBlob, expectedOld);
      assertByteSourceEquals(newBlob, expectedNew);
      deltaOut.write(output);
    }

    private final void assertByteSourceEquals(ByteSource actual, ByteSource expected)
        throws IOException {
      try (InputStream actualIn = actual.openStream();
          InputStream expectedIn = expected.openStream()) {
        assertThat(actualIn.read()).isEqualTo(expectedIn.read());
      }
    }
  }
}


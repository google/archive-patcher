package com.google.archivepatcher.delta;

import com.google.archivepatcher.DeltaApplier;
import com.google.archivepatcher.util.IOUtils;
import com.nothome.delta.GDiffPatcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Implementation of a {@link DeltaApplier} based on javaxdelta.
 */
public class JxdDeltaApplier implements DeltaApplier {
    @Override
    public void applyDelta(InputStream oldData, InputStream deltaData,
            OutputStream newOut) throws IOException {
        new GDiffPatcher().patch(IOUtils.readAll(oldData), deltaData, newOut);
    }
}

package com.google.archivepatcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Implementation of a {@link DeltaApplier} based on javaxdelta.
 */
public interface DeltaApplier {
    /**
     * Applies the delta from a corresponding {@link DeltaGenerator} to the
     * data in oldData, writing the final artifact into newOut.
     * 
     * @param oldData the old data
     * @param deltaData the delta 
     * @param newOut output stream where the final result is written
     * @throws IOException if something goes awry while reading or writing
     */
    public void applyDelta(InputStream oldData, InputStream deltaData,
            OutputStream newOut) throws IOException;
}

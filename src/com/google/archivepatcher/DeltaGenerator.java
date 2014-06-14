package com.google.archivepatcher;

import com.google.archivepatcher.parts.CentralDirectoryFile;
import com.google.archivepatcher.parts.LocalSectionParts;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An interface for implementing a delta generator. When generating patches,
 * a delta generator can be configured to attempt to produce a delta between
 * the old and new versions of an entry in an archive; the most compact
 * representation of the delta that gets the job done will generally be used. 
 */
public abstract class DeltaGenerator {

    /**
     * Returns true if this generator can process the given entry.
     * The generator should not attempt to process the entry at this stage;
     * instead, processing (which may be computationally intensive) should be
     * deferred till {@link #makeDelta(InputStream, InputStream, OutputStream)}.
     * <p>
     * If this method returns true, the object <em>will</em> receive a call to
     * {@link #makeDelta(InputStream, InputStream, OutputStream)} for the
     * resource at some point in the future; otherwise, it will not. Note that
     * it is <em>not</em> guaranteed that the next call to
     * {@link #makeDelta(InputStream, InputStream, OutputStream)} will be
     * for the resource that is identified in the current call.
     *   
     * @param lsp the local parts
     * @param cdf the central directory entry corresponding to the local parts
     * @return true if the generator can produce a delta for this resource
     */
    public boolean accept(LocalSectionParts lsp, CentralDirectoryFile cdf) {
        return true;
    }

    /**
     * Outputs a representation of the difference between oldData and newData,
     * suitable for transforming oldData into newData when a corresponding
     * {@link DeltaApplier} is invoked upon the resulting deltaOut.
     * <p>
     * Note that the patcher may decline to use the results of this call if
     * the resulting delta is determined to be suboptimal. This may occur for
     * several reasons, such as but not limited to: the delta is larger than
     * the new data, the delta is larger than the delta produced by another
     * competing {@link DeltaGenerator}, etceteras.
     * 
     * @param oldData the old data
     * @param newData the new data
     * @param deltaOut output stream where the delta is written to
     * @throws IOException if something goes awry while reading or writing
     */
    public abstract void makeDelta(InputStream oldData, InputStream newData,
            OutputStream deltaOut) throws IOException;
}

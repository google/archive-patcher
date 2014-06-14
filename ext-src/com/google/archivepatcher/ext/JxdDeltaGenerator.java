package com.google.archivepatcher.ext;

import com.google.archivepatcher.DeltaGenerator;
import com.google.archivepatcher.parts.CentralDirectoryFile;
import com.google.archivepatcher.parts.LocalSectionParts;
import com.google.archivepatcher.util.IOUtils;
import com.nothome.delta.Delta;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Implementation of a {@link DeltaGenerator} based on javaxdelta.
 */
public class JxdDeltaGenerator extends DeltaGenerator {

    @Override
    public boolean accept(LocalSectionParts lsp, CentralDirectoryFile cdf) {
        return true;
    }

    @Override
    public void makeDelta(InputStream oldData, InputStream newData,
            OutputStream deltaOut) throws IOException {
        new Delta().compute(IOUtils.readAll(oldData),
                IOUtils.readAll(newData), deltaOut);
    }
}
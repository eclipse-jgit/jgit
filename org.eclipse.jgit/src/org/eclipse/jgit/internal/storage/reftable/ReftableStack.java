package org.eclipse.jgit.internal.storage.reftable;

import java.io.IOException;

public interface ReftableStack {
    long nextUpdateIndex() throws IOException;
}

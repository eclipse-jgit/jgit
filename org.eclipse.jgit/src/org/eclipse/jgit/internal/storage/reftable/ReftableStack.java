package org.eclipse.jgit.internal.storage.reftable;

import java.io.IOException;
import java.util.List;

public interface ReftableStack extends AutoCloseable {
    /**
     * Returns the logical timestamp at which the next reftable in the stack should start.
     */
    long nextUpdateIndex() throws IOException;

    /**
     * Get unmodifiable list of tables
     *
     * @return unmodifiable list of tables
     */
    List<Reftable> readers();
}

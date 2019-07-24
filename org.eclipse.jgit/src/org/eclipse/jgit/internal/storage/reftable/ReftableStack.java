package org.eclipse.jgit.internal.storage.reftable;

import java.io.IOException;
import java.util.List;

/**
 * A read-only snapshot of a stack of reftable.
 */
public interface ReftableStack {
    /**
     * @return the logical timestamp at which the next reftable in the stack should start.
     * @throws java.io.IOException on I/O problems.
     */
    long nextUpdateIndex() throws IOException;

    /**
     * @return unmodifiable list of tables that constitute the stack.
     */
    List<Reftable> readers();
}

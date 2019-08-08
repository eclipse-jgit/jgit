package org.eclipse.jgit.internal.storage.reftable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public interface ReftableStack extends AutoCloseable {
    long nextUpdateIndex() throws IOException;
    List<Reftable> readers();
}

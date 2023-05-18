package org.eclipse.jgit.internal.storage.commitgraph;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class GraphObjectIndexTest {

  @Test
  public void findGraphPosition_noObjInBucket() throws CommitGraphFormatException {
    GraphObjectIndex idx = new GraphObjectIndex(100,
        new byte[256 * 4], new byte[] {});
    int graphPosition = idx.findGraphPosition(
        ObjectId.fromString("731dfd4c5eb6f88b98e983b9b0551b3562a0c46c"));
    assertEquals(-1, graphPosition);
  }

}

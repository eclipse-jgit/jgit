package org.eclipse.jgit.util;

import java.io.IOException;
import org.eclipse.jgit.attributes.Attributes;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Classes with this interface support both local and remote depositories (unlike most of the
 * project, which only supports local repositories).
 */
public interface InCoreSupport {

  /**
   * Use the result {@link RepoIOHandler} to handle any IO related actions.
   */
  static RepoIOHandler initRepoIOHandler(
      Repository repo,
      boolean inCore,
      ObjectInserter oi,
      ObjectReader reader,
      Attributes attributes)
      throws IOException {
    String smudgeCommand = null;
    if (!inCore) {
      TreeWalk walk = new TreeWalk(repo);
      smudgeCommand = walk.getSmudgeCommand(attributes);
    }
    return new RepoIOHandler(
        repo, inCore, oi, reader, smudgeCommand);
  }
}

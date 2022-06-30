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
   *
   * @param repo          either local or remote repository.
   * @param inCore        whether the repository is remote (inCore==true), or local
   *                      (inCore==false).
   * @param oi            to use for writing the modified objects with.
   * @param reader        to read existing content with.
   * @param attributes    to use for determining the metadata
   * @return              an IO handler.
   * @throws IOException  might occur when reading the attributes.
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

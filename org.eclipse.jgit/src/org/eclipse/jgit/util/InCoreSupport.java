package org.eclipse.jgit.util;

import java.io.IOException;
import org.eclipse.jgit.attributes.Attributes;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;

public interface InCoreSupport {

  static InCoreHandler initInCoreHandler(Repository repo, ObjectInserter oi, ObjectReader reader,
      boolean inCore, boolean shouldUpdateIndex, Attributes attributes) throws IOException {
    String smudgeCommand = null;
    if(!inCore) {
      TreeWalk walk = new TreeWalk(repo);
      smudgeCommand = walk.getSmudgeCommand(attributes);
    }
    return new InCoreHandler(repo, oi, reader, inCore, shouldUpdateIndex, smudgeCommand);
  }
}



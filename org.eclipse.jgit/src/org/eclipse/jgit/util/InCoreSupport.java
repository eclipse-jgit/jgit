package org.eclipse.jgit.util;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;

/**
 * Classes with this interface support both local and remote depositories (unlike most of the
 * project, which only supports local repositories).
 */
public interface InCoreSupport {

  /**
   * Initiates {@link RepoIOHandler} for the given local repository.
   * <p>
   * Use the result object to handle any IO related actions.
   *
   * @param local    repository
   * @param dirCache if set, use the provided dir cache. Otherwise, use the default repository one
   * @return an IO handler.
   */
  public static RepoIOHandler initRepoIOHandlerForLocal(Repository local,
      DirCache dirCache) {
    return new RepoIOHandler(local, dirCache);
  }

  /**
   * Initiates {@link RepoIOHandler} for the given remote repository.
   * <p>
   * Use the result object to handle any IO related actions.
   *
   * @param remote   repository
   * @param dirCache if set, use the provided dir cache. Otherwise, creates a new one
   * @param oi       to use for writing the modified objects with.
   * @return an IO handler.
   */
  public static RepoIOHandler initRepoIOHandlerForRemote(Repository remote,
      DirCache dirCache, ObjectInserter oi) {
    return new RepoIOHandler(remote, dirCache, oi);
  }
}

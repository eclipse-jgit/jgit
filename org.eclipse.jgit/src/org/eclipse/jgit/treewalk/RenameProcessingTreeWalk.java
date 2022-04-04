package org.eclipse.jgit.treewalk;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;

public class RenameProcessingTreeWalk  extends NameConflictTreeWalk{

  private static final int TREE_MODE = FileMode.TREE.getBits();

  private boolean fastMinHasMatch;

  private String renamePath;

  /**
   * Create a new tree walker for a given repository.
   *
   * @param repo the repository the walker will obtain data from.
   */
  public RenameProcessingTreeWalk (Repository repo) {
    super(repo);
  }

  /**
   * Create a new tree walker for a given repository.
   *
   * @param repo the repository the walker will obtain data from.
   * @param or the reader the walker will obtain tree data from.
   * @since 4.3
   */
  public RenameProcessingTreeWalk (@Nullable Repository repo, ObjectReader or) {
    super(repo, or);
  }

  /**
   * Create a new tree walker for a given repository.
   *
   * @param or the reader the walker will obtain tree data from.
   */
  public RenameProcessingTreeWalk (ObjectReader or) {
    super(or);
  }

  public void swapRenameTree(int nth, CanonicalTreeParser tree){
    trees[nth] = tree;
    trees[nth].matches = currentHead;
  }

  public void setPathName(String pathName){
    this.renamePath = pathName;
  }

  @Override
  public byte[] getRawPath() {
    return renamePath.getBytes();
  }

  @Override
  public String getPathString() {
    return renamePath;
  }
}
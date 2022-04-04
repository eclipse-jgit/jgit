package org.eclipse.jgit.treewalk;

import static org.eclipse.jgit.merge.ResolveMerger.T_BASE;
import static org.eclipse.jgit.merge.ResolveMerger.T_FILE;
import static org.eclipse.jgit.merge.ResolveMerger.T_INDEX;
import static org.eclipse.jgit.merge.ResolveMerger.T_OURS;
import static org.eclipse.jgit.merge.ResolveMerger.T_THEIRS;

import java.io.IOException;
import java.util.Set;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.RenameResolver;
import org.eclipse.jgit.merge.RenameResolver.RenameEntry;
import org.eclipse.jgit.merge.RenameResolver.RenameType;
import org.eclipse.jgit.merge.ResolveMerger;

/**
 * Specialized TreeWalk adopted to merge renames on {@link org.eclipse.jgit.merge.ResolveMerger}.
 *
 * <p> The regular TreeWalk knows nothing about the renames. Hence, when it is walked by {@link org.eclipse.jgit.merge.ResolveMerger} on merge, the entries with exactly matching paths are merged, resulting in merge conflicts.
 * <p> This walk provides gives the  {@link org.eclipse.jgit.merge.ResolveMerger} a way to process the renames in two phased
 * <ul>
 *   <li> Non-renames: Processes all regular diffs, skipping any diffs related to the rename.
 *   <li> Renames:Processes each {@link RenameEntry} separately, pairing the matching "source" and "target" path from the corresponding merge trees.</li>
 * </ul>
 */
public class RenameProcessingTreeWalk  extends NameConflictTreeWalk {

  private String renamePath;

  private final RenameResolver renameResolver;

  private final boolean skipRenames;
  private final IndexChecker indexChecker;
  private final WorktreeChecker worktreeChecker;
  boolean isTerminated =  false;


  /**
   * Create a new tree walker for a given repository.
   *
   * @param repo the repository the walker will obtain data from.
   * @param renameResolver {@link RenameResolver} that holds the renames on trees, that the walk is operating on.
   * @param indexChecker see {@link IndexChecker}
   * @param worktreeChecker see {@link WorktreeChecker}
   * @param skipRenames should be true if this is the first (non-rename) processing phase of {@link ResolveMerger}
   */
  public RenameProcessingTreeWalk(Repository repo, RenameResolver renameResolver, IndexChecker indexChecker, WorktreeChecker worktreeChecker, boolean skipRenames) {
    super(repo);
    this.renameResolver = renameResolver;
    this.skipRenames = skipRenames;
    this.indexChecker = indexChecker;
    this.worktreeChecker = worktreeChecker;
  }

  /**
   * Create a new tree walker for a given repository.
   *
   * @param repo the repository the walker will obtain data from.
   * @param or the reader the walker will obtain tree data from.
   * @param renameResolver {@link RenameResolver} that holds the renames on trees, that the walk is operating on.
   * @param indexChecker see {@link IndexChecker}
   * @param worktreeChecker see {@link WorktreeChecker}
   * @param skipRenames should be true if this is the first (non-rename) processing phase of {@link ResolveMerger}
   */
  public RenameProcessingTreeWalk(@Nullable Repository repo, ObjectReader or, RenameResolver renameResolver, IndexChecker indexChecker, WorktreeChecker worktreeChecker, boolean skipRenames) {
    super(repo, or);
    this.renameResolver = renameResolver;
    this.skipRenames = skipRenames;
    this.indexChecker = indexChecker;
    this.worktreeChecker = worktreeChecker;
  }

  /**
   * Create a new tree walker for a given repository.
   *
   * @param or the reader the walker will obtain tree data from.
   * @param renameResolver {@link RenameResolver} that holds the renames on trees, that the walk is operating on.
   * @param indexChecker see {@link IndexChecker}
   * @param worktreeChecker see {@link WorktreeChecker}
   * @param skipRenames should be true if this is the first (non-rename) processing phase of {@link ResolveMerger}
   */
  public RenameProcessingTreeWalk(ObjectReader or,
      RenameResolver renameResolver, IndexChecker indexChecker, WorktreeChecker worktreeChecker, boolean skipRenames) {
    super(or);
    this.renameResolver = renameResolver;
    this.skipRenames = skipRenames;
    this.indexChecker = indexChecker;
    this.worktreeChecker = worktreeChecker;
  }

  /**
   * An interface to check index state for each entry, seen by the walk, see {@code ResolveMerger#isIndexDirty()}.
   *
   * <p> The merger can not operate if any of the paths with diffs are already present in index.
   * <p> We want to check this on the first (non-rename) processing phase even for the skipped renamed paths, since it will allow to abort early.
   */
  public interface IndexChecker {
    boolean isIndexDirty();
  }

  /**
   * An interface to check worktree state for the entries, that correlate with rename paths but do not belong to the rename, see {@code ResolveMerger#isWorktreeDirty()}.
   *
   * <p> If the merger needs to write to path, that the working tree modify, the merge fails. We skip the source and target paths of the rename, but wants to process any paths that correlate with the rename, but do not belong to it (see {@link #keepRenameCorrelations} during the non-rename phase, we need to check those paths are safe to update.
   */
  public interface WorktreeChecker {
    boolean isWorktreeDirty() throws IOException;
  }

  /**
   * Returns true if the walk was terminated because of the dirty index or work tree on the merged entry paths.
   */
  public boolean isTerminated(){
    return isTerminated;
  }

  private AbstractTreeIterator getTree(final int nth){
    return getTree(nth, AbstractTreeIterator.class);
  }


  @Override
  public boolean next() throws IOException {
    if(!skipRenames){
      return super.next();
    }
    // restore matches that were replaced for processing of rename correlations, see keepRenameCorrelations
    swapMatchBack();
    boolean moreEntries = super.next();
    if(!moreEntries){
      return false;
    }
    if(indexChecker.isIndexDirty()){
      this.isTerminated = true;
      return false;
    }
    if(renameResolver.isRenameEntry(getTree(T_BASE), getTree(T_OURS), getTree(T_THEIRS))){
       if(!keepRenameCorrelations()){
         // finish early if the conflict was found
         if(isTerminated){
           return false;
         }
         // skip to next non-rename entry
         return next();
       }
    }
    // no rename, proceed with the regular processing
    return true;
  }

  /**
   * Gives a way to substitute the nth tree in the walk with the passed tree.
   *
   * <p>This allows entries under different path to be processed by {@link ResolveMerger} at the same time.
   * @param nth index of the tree in this walk.
   * @param tree to replace the current tree with.
   */
  public void swapTree(int nth, CanonicalTreeParser tree) {
    trees[nth] = tree;
    trees[nth].matches = currentHead;
  }

  /**
   * Sets the path that the current entry should be merged under.
   */
  public void setPathName(String pathName) {
    this.renamePath = pathName;
  }

  @Override
  public byte[] getRawPath() {
    return renamePath != null ? renamePath.getBytes() : super.getRawPath();
  }

  @Override
  public String getPathString() {
    return renamePath != null ? renamePath : super.getPathString();
  }

  private AbstractTreeIterator[] matchTrees;

  private void keepTrees(Set<Integer> treesToKeep) {
    matchTrees = new AbstractTreeIterator[trees.length];
    for (int i = 0; i < trees.length; i++) {
      matchTrees[i] = this.trees[i].matches;
      if (!treesToKeep.contains(i)) {
        this.trees[i].matches = null;
      }
    }
  }

  private void swapMatchBack() {
    if (matchTrees != null) {
      for (int i = 0; i < matchTrees.length; i++) {
        this.trees[i].matches = matchTrees[i];
      }
      matchTrees = null;
    }
  }


  /**
   * Returns true if the entry should be processed in case of possible rename/add correlation.
   *
   * <p>We skip both source and target rename paths (to merge the matching entries at different paths later together), but we need to process possible additions of the files with correlated names.
   * <p>Since any addition of the target path would result in {@link org.eclipse.jgit.merge.RenameResolver.RenameConflict#RENAME_ADD_CONFLICT}, only the source path renames are processed for possible correlations.
   */
  private boolean keepRenameCorrelations( )
      throws IOException {
    // If either side added entries that correlate with source of the rename, they should not be skipped.
    // We also do not want to merge them with the entries, that are part of rename on other side.

    CanonicalTreeParser base =
        getTree(T_BASE, CanonicalTreeParser.class);
    // Only correlation on source need to be processed.
    if(base == null){
      return false;
    }
    String basePath = base.getEntryPathString();
    RenameType renameType = renameResolver.getRenameType(basePath);
    if (renameType.equals(RenameType.NO_RENAME) || renameType.equals(RenameType.RENAME_CONFLICT)) {
      return false;
    }
    RenameEntry renameEntry = renameResolver.getRenameEntry(basePath);
    if (renameResolver.isBaseRename(renameEntry.getTargetPath())) {
      // If the target path is a rename, skip, since swap renames are valid and are processed later on.
      return false;
    }
    Set<Integer> keepTrees = Set.of();
    if (renameType.equals(RenameType.RENAME_IN_OURS)) {
      if(this.getTree(T_OURS)!=null) {
        keepTrees = Set.of(T_OURS, T_INDEX, T_FILE);
      }
      // rename in ours only. keep ours for possible  source path name collision
      // theirs is dropped since it will be merged with the rename.
    } else if (renameType.equals(RenameType.RENAME_IN_THEIRS)) {
      // rename in theirs only. keep theirs for possible  source path name collision
      // ours is dropped since it will be merged with the rename.
      if(this.getTree(T_THEIRS)!=null) {
        keepTrees = Set.of(T_THEIRS);
      }
    } else if (renameType.equals(RenameType.RENAME_BOTH_NO_CONFLICT)) {
      // rename in both to the same name. both trees might have the added file that we want to content merge.
      // none is dropped since the correlation might be present on both sides.
      if(this.getTree(T_OURS)!=null || this.getTree(T_THEIRS)!=null){
        keepTrees = Set.of(T_OURS, T_THEIRS, T_INDEX, T_FILE);
      }
    }
    if(keepTrees.isEmpty()) {
      // nothing to process
      return false;
    }
    // We need to check if this entry can be processed by ResolveMerger#processEntry before substituting the trees
    // This check won't happen in ResolveMerger#processEntry if we only keep T_THEIRS, in other cases there is no harm in double-checking.
    if(worktreeChecker.isWorktreeDirty()){
      isTerminated = true;
      return false;
    }
    keepTrees(keepTrees);
    return true;
  }
}
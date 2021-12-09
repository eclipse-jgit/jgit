package org.eclipse.jgit.treewalk;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;

public class RenameDetectingTreeWalk extends TreeWalk {
  private static final int TREE_MODE = FileMode.TREE.getBits();

  private boolean fastMinHasMatch;


  Map<ObjectId, Map<ObjectId, DiffEntry>> renamedDiffEntry = new HashMap<>();
  Map<Integer, Map<Integer, Map<ObjectId, DiffEntry>>> renameByTrees= new HashMap<>();

  private AbstractTreeIterator dfConflict;

  /**
   * Create a new tree walker for a given repository.
   *
   * @param repo
   *            the repository the walker will obtain data from.
   */
  public RenameDetectingTreeWalk(Repository repo) {
    super(repo);
  }

  /**
   * Create a new tree walker for a given repository.
   *
   * @param repo
   *            the repository the walker will obtain data from.
   * @param or
   *            the reader the walker will obtain tree data from.
   * @since 4.3
   */
  public RenameDetectingTreeWalk(@Nullable Repository repo, ObjectReader or) {
    super(repo, or);
  }

  /**
   * Create a new tree walker for a given repository.
   *
   * @param or
   *            the reader the walker will obtain tree data from.
   */
  public RenameDetectingTreeWalk(ObjectReader or) {
    super(or);
  }

  public void addRenames(int nthA, int nthB, List<DiffEntry> diffEntries) {
    // 1) Looks like this works, but will always keep the non-renamed entry (ours). What is expected?
    // 2) This works assuming that the directory rename was detected. In case we were in root and moved to a sub dir, the root rename is not reported.
    // 3) check how it works with mix.
    for (DiffEntry diffEntry : diffEntries) {
      if (!renamedDiffEntry.containsKey(diffEntry.getOldId())) {
        renamedDiffEntry.put(diffEntry.getOldId().toObjectId(), new HashMap<>());
      }
      if (!renamedDiffEntry.containsKey(diffEntry.getNewId().toObjectId())) {
        renamedDiffEntry.put(diffEntry.getNewId().toObjectId(), new HashMap<>());
      }
      renamedDiffEntry.get(diffEntry.getOldId().toObjectId())
          .put(diffEntry.getOldId().toObjectId(), diffEntry);
      renamedDiffEntry.get(diffEntry.getNewId().toObjectId())
          .put(diffEntry.getNewId().toObjectId(), diffEntry);
    }
    if(!renameByTrees.containsKey(nthA)) {
      renameByTrees.put(nthA, new HashMap<>());
    }
    if(!renameByTrees.containsKey(nthB)) {
      // We likely don't always have nth
      renameByTrees.put(nthB, new HashMap<>());
    }
  }

  @Override
  AbstractTreeIterator min() throws CorruptObjectException {
    for (;;) {
      final AbstractTreeIterator minRef = fastMin();
      if (fastMinHasMatch)
        return minRef;

      if (isTree(minRef)) {
        if (skipEntry(minRef)) {
          for (AbstractTreeIterator t : trees) {
            if (t.matches == minRef) {
              t.next(1);
              t.matches = null;
            }
          }
          continue;
        }
        return minRef;
      }

      return combineDF(minRef);
    }
  }

  private AbstractTreeIterator fastMin() {
    fastMinHasMatch = true;

    int i = 0;
    AbstractTreeIterator minRef = trees[i];
    while (minRef.eof() && ++i < trees.length)
      minRef = trees[i];
    if (minRef.eof())
      return minRef;

    boolean hasConflict = false;
    minRef.matches = minRef;
    while (++i < trees.length) {
      final AbstractTreeIterator t = trees[i];
      if (t.eof())
        continue;

      final int cmp = renamePathCompare(t, minRef);
      if (cmp < 0) {
        if (fastMinHasMatch && isTree(minRef) && !isTree(t)
            && nameEqual(minRef, t)) {
          // We used to be at a tree, but now we are at a file
          // with the same name. Allow the file to match the
          // tree anyway.
          //
          t.matches = minRef;
          hasConflict = true;
        } else {
          fastMinHasMatch = false;
          t.matches = t;
          minRef = t;
        }
      } else if (cmp == 0) {
        // Exact name/mode match is best.
        //
        t.matches = minRef;
      } else if (fastMinHasMatch && isTree(t) && !isTree(minRef)
          && !isGitlink(minRef) && nameEqual(t, minRef)) {
        // The minimum is a file (non-tree) but the next entry
        // of this iterator is a tree whose name matches our file.
        // This is a classic D/F conflict and commonly occurs like
        // this, with no gaps in between the file and directory.
        //
        // Use the tree as the minimum instead (see combineDF).
        //

        for (int k = 0; k < i; k++) {
          final AbstractTreeIterator p = trees[k];
          if (p.matches == minRef)
            p.matches = t;
        }
        t.matches = t;
        minRef = t;
        hasConflict = true;
      } else
        fastMinHasMatch = false;
    }

    if (hasConflict && fastMinHasMatch && dfConflict == null)
      dfConflict = minRef;
    return minRef;
  }

  private boolean nameEqual(final AbstractTreeIterator a,
      final AbstractTreeIterator b) {
    return renamePathCompare(a, b, TREE_MODE) == 0;
  }

  private int renamePathCompare(final AbstractTreeIterator a,
      final AbstractTreeIterator b, int pMode) {

    if (renamedDiffEntry.containsKey(a.getEntryObjectId()) && renamedDiffEntry.get(
        a.getEntryObjectId()).containsKey(b.getEntryObjectId())) {
      return 0;
    }
    return a.pathCompare(b, pMode);
  }

  private int renamePathCompare(final AbstractTreeIterator a,
      final AbstractTreeIterator b) {
    return renamePathCompare(a, b, b.mode);
  }

  private boolean isGitlink(AbstractTreeIterator p) {
    return FileMode.GITLINK.equals(p.mode);
  }

  private static boolean isTree(AbstractTreeIterator p) {
    return FileMode.TREE.equals(p.mode);
  }

  private boolean skipEntry(AbstractTreeIterator minRef)
      throws CorruptObjectException {
    // A tree D/F may have been handled earlier. We need to
    // not report this path if it has already been reported.
    //
    for (AbstractTreeIterator t : trees) {
      if (t.matches == minRef || t.first())
        continue;

      int stepsBack = 0;
      for (;;) {
        stepsBack++;
        t.back(1);

        final int cmp = renamePathCompare(t, minRef, 0);
        if (cmp == 0) {
          // We have already seen this "$path" before. Skip it.
          //
          t.next(stepsBack);
          return true;
        } else if (cmp < 0 || t.first()) {
          // We cannot find "$path" in t; it will never appear.
          //
          t.next(stepsBack);
          break;
        }
      }
    }

    // We have never seen the current path before.
    //
    return false;
  }

  private AbstractTreeIterator combineDF(AbstractTreeIterator minRef)
      throws CorruptObjectException {
    // Look for a possible D/F conflict forward in the tree(s)
    // as there may be a "$path/" which matches "$path". Make
    // such entries match this entry.
    //
    AbstractTreeIterator treeMatch = null;
    for (AbstractTreeIterator t : trees) {
      if (t.matches == minRef || t.eof())
        continue;

      for (;;) {
        final int cmp = renamePathCompare(t, minRef, TREE_MODE);
        if (cmp < 0) {
          // The "$path/" may still appear later.
          //
          t.matchShift++;
          t.next(1);
          if (t.eof()) {
            t.back(t.matchShift);
            t.matchShift = 0;
            break;
          }
        } else if (cmp == 0) {
          // We have a conflict match here.
          //
          t.matches = minRef;
          treeMatch = t;
          break;
        } else {
          // A conflict match is not possible.
          //
          if (t.matchShift != 0) {
            t.back(t.matchShift);
            t.matchShift = 0;
          }
          break;
        }
      }
    }

    if (treeMatch != null) {
      // If we do have a conflict use one of the directory
      // matching iterators instead of the file iterator.
      // This way isSubtree is true and isRecursive works.
      //
      for (AbstractTreeIterator t : trees)
        if (t.matches == minRef)
          t.matches = treeMatch;

      if (dfConflict == null && !isGitlink(minRef)) {
        dfConflict = treeMatch;
      }

      return treeMatch;
    }

    return minRef;
  }

  @Override
  void popEntriesEqual() throws CorruptObjectException {
    final AbstractTreeIterator ch = currentHead;
    for (AbstractTreeIterator t : trees) {
      if (t.matches == ch) {
        if (t.matchShift == 0)
          t.next(1);
        else {
          t.back(t.matchShift);
          t.matchShift = 0;
        }
        t.matches = null;
      }
    }

    if (ch == dfConflict)
      dfConflict = null;
  }

  @Override
  void skipEntriesEqual() throws CorruptObjectException {
    final AbstractTreeIterator ch = currentHead;
    for (AbstractTreeIterator t : trees) {
      if (t.matches == ch) {
        if (t.matchShift == 0)
          t.skip();
        else {
          t.back(t.matchShift);
          t.matchShift = 0;
        }
        t.matches = null;
      }
    }

    if (ch == dfConflict)
      dfConflict = null;
  }

  @Override
  void stopWalk() throws IOException {
    if (!needsStopWalk()) {
      return;
    }

    // Name conflicts make aborting early difficult. Multiple paths may
    // exist between the file and directory versions of a name. To ensure
    // the directory version is skipped over (as it was previously visited
    // during the file version step) requires popping up the stack and
    // finishing out each subtree that the walker dove into. Siblings in
    // parents do not need to be recursed into, bounding the cost.
    for (;;) {
      AbstractTreeIterator t = min();
      if (t.eof()) {
        if (depth > 0) {
          exitSubtree();
          popEntriesEqual();
          continue;
        }
        return;
      }
      currentHead = t;
      skipEntriesEqual();
    }
  }

  private boolean needsStopWalk() {
    for (AbstractTreeIterator t : trees) {
      if (t.needsStopWalk()) {
        return true;
      }
    }
    return false;
  }

  /**
   * True if the current entry is covered by a directory/file conflict.
   *
   * This means that for some prefix of the current entry's path, this walk
   * has detected a directory/file conflict. Also true if the current entry
   * itself is a directory/file conflict.
   *
   * Example: If this TreeWalk points to foo/bar/a.txt and this method returns
   * true then you know that either for path foo or for path foo/bar files and
   * folders were detected.
   *
   * @return <code>true</code> if the current entry is covered by a
   *         directory/file conflict, <code>false</code> otherwise
   */
  public boolean isDirectoryFileConflict() {
    return dfConflict != null;
  }
}

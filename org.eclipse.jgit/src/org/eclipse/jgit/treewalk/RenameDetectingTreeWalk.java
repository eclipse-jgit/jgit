package org.eclipse.jgit.treewalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;

/**
 * Specialized TreeWalk to detect directory-file (D/F) name conflicts.
 * <p>
 * Due to the way a Git tree is organized the standard
 * {@link org.eclipse.jgit.treewalk.TreeWalk} won't easily find a D/F conflict
 * when merging two or more trees together. In the standard TreeWalk the file
 * will be returned first, and then much later the directory will be returned.
 * This makes it impossible for the application to efficiently detect and handle
 * the conflict.
 * <p>
 * Using this walk implementation causes the directory to report earlier than
 * usual, at the same time as the non-directory entry. This permits the
 * application to handle the D/F conflict in a single step. The directory is
 * returned only once, so it does not get returned later in the iteration.
 * <p>
 * When a D/F conflict is detected
 * {@link org.eclipse.jgit.treewalk.TreeWalk#isSubtree()} will return true and
 * {@link org.eclipse.jgit.treewalk.TreeWalk#enterSubtree()} will recurse into
 * the subtree, no matter which iterator originally supplied the subtree.
 * <p>
 * Because conflicted directories report early, using this walk implementation
 * to populate a {@link org.eclipse.jgit.dircache.DirCacheBuilder} may cause the
 * automatic resorting to run and fix the entry ordering.
 * <p>
 * This walk implementation requires more CPU to implement a look-ahead and a
 * look-behind to merge a D/F pair together, or to skip a previously reported
 * directory. In typical Git repositories the look-ahead cost is 0 and the
 * look-behind doesn't trigger, as users tend not to create trees which contain
 * both "foo" as a directory and "foo.c" as a file.
 * <p>
 * In the worst-case however several thousand look-ahead steps per walk step may
 * be necessary, making the overhead quite significant. Since this worst-case
 * should never happen this walk implementation has made the time/space tradeoff
 * in favor of more-time/less-space, as that better suits the typical case.
 */
/*
 The Name/dir walk just looks ahead for the dir with the same name as current node file and seeks all tree iterators to it.
 The potential walk-based solution for the rename detection will be composed of the following parts:
 1) Identify the renamed path. This needs to be done on the directory levels.


 2) The iterator iterates the trees, starting from the root directory.
   * The Merge is recursive: if the contents of the dir have changed (the object SHA1), it recurses until possible.
   * Without ND conflict detection, the dir in one tree/file in other wouldn't match. The dir would be reported earlier,
   and the file would be considered a deletion.
 Same applies to the renames. Since the renamed entries are not reported at the same time, they are processed by the merger as separate entities.
 The new name entry is reported as 'ADDED', the old name entry is reported as 'DELETED'. In case of the content modification this leads to the delete/modify conflict.
We could use the similar approach to the ND conflict detection. We would report the renamed dirs/files at the same time. With the following caveats:
1) The directory may be renamed, but the new entries could have been added along the way. E.g.
B: z/ {b, c}
0: z/d/{b, c}, z/e.
T: z/{b, c, d}
R: z/d/{b, c}, z/e
z/e here needs to be reported too.
First, z will be found by the iterator. Instead of emitting it, we would first like to process z/d, reporting z in O, we will need to seek to z/d. z/d will need to be skipped from O later, but z/e still needs to be processed (as a simple addition)
2 options here:
1) we seek O to z/d and mark it as min. We also need to seek all other trees to z/d, if present. All matching entries will need to be named as z/d. We will then need to seek back to z and skip z/d somehow?
In case of sub dir insert, we only ever need to seek forward. In case of sub dir remove, we also seek to z/d? but the rename should be to z.
2) we seek to z/d, but z remains min. We will then need to seek back to z.

2) The case of rename without subdir:
B: z/ {b, c}
0: k/{b, c, f/e}.
T: z/{b, c, d}
R: k/{b, c, d, f/e}
we need to seek O to k, the entries created under k. No seeking back is needed? We now it is a rename path in O because of the pre-processing, so will need to seek forward?
Merging dirs:
B: z/{b,c},   y/d
O: z/{b,c,e}, y/d
T: y/{b,c,d}
R: y/{b,c,d,e}
We need to report z at the same time as y. We also need to report y at the same time as y. This does not seem possible. Unless we seek individual files. This might require both look ahead and behind. Also, how do we skeep in this case?

# Testcase 1c, Transitive renaming
This will be very hard to implement. On x/d, we will need to seek to z/d, but also keep the resulting dir as y/
#   B: z/{b,c},   x/d
#   O: y/{b,c},   x/d
#   T: z/{b,c,d}
#   R: y/{b,c,d}  (because x/d -> z/d -> y/d)
 */
public class RenameDetectingTreeWalk extends TreeWalk {
  private static final int TREE_MODE = FileMode.TREE.getBits();

  private boolean fastMinHasMatch;// match if all tries has match



  Map<ObjectId, Map<ObjectId, DiffEntry>> renamedDiffEntry = new HashMap<>();
  Map<Integer, Map<Integer, Map<ObjectId, DiffEntry>>> renameByTrees= new HashMap<>();

  // Any rename object to DiffEntry map
  // We probably want an object id to map of object ids in all other trees?
  // Only for renames?
  // We need to mark dir, that contain renames as a renames themselves?
  Map<ObjectId, Map<ObjectId, DiffEntry>> anyRenameToDiffEntry = new HashMap<>();
  Map<Integer, Map<Integer, Map<String, String>>> renamePathsByTree  = new HashMap<>();

  Map<Integer, Map<String, String>> renamePathsToBaseByTree  = new HashMap<>();

  // old -> new, also probably need to keep processed or not?
  Map<String, Map<Integer, String>> baseRenamePaths  = new HashMap<>();
  Map<Integer, Set<String>> processedRenames  = new HashMap<>();
  int nthRenameBase;
  // Is processed rename set?


  private AbstractTreeIterator dfConflict;

  private boolean isRenameProcessing = false;
  private AbstractTreeIterator renameIterator;

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

  public void addRenames(int nthRenameBase, int nthB, List<DiffEntry> diffEntries) {
    this.nthRenameBase = nthRenameBase;
   for( DiffEntry entry: diffEntries) {
     if (entry.getChangeType().equals(ChangeType.RENAME)) {
       int endDirOld = entry.getOldPath().lastIndexOf("/");
       int endDirNew = entry.getNewPath().lastIndexOf("/");
       if (endDirNew == -1 || endDirOld == -1) {
         // something was renamed in the root directory
         continue;
       }
       while (endDirOld >= 0 && endDirNew >= 0
           && entry.getOldPath().charAt(endDirOld) == entry.getNewPath().charAt(endDirNew)) {
         endDirNew--;
         endDirOld--;
       }
       if (endDirNew == 0 && endDirOld == 0
           && entry.getOldPath().charAt(endDirOld) == entry.getNewPath().charAt(endDirNew)) {
         // only file name was changed
       }
       endDirOld = entry.getOldPath().indexOf("/", endDirOld);
       endDirNew = entry.getNewPath().indexOf("/", endDirNew);
       String oldDir = entry.getOldPath().substring(0, endDirOld);
       // this old dir needs to be reported at the same time as the new dir. The old dir was maybe modified
       // the new dir is added
       String newDir = entry.getNewPath().substring(0, endDirNew);
       // If this turns out to be inefficient, we cal likely store the tree-object pair (extract from diff list).
       if (!renamePathsByTree.containsKey(nthRenameBase)) {
         renamePathsByTree.put(nthRenameBase, new HashMap<>());
       }
       if (!renamePathsByTree.get(nthRenameBase).containsKey(nthB)) {
         renamePathsByTree.get(nthRenameBase).put(nthB, new HashMap<>());
       }

       if (!renamePathsByTree.containsKey(nthB)) {
         renamePathsByTree.put(nthB, new HashMap<>());
       }
       if (!renamePathsByTree.get(nthB).containsKey(nthRenameBase)) {
         renamePathsByTree.get(nthB).put(nthRenameBase, new HashMap<>());
       }

       //renamePathsByTree.get(nthA).get(nthB).put(newDir, oldDir);
       renamePathsByTree.get(nthRenameBase).get(nthB).put(entry.getNewPath(), entry.getOldPath());
       //renamePathsByTree.get(nthB).get(nthA).put(oldDir, newDir);
       renamePathsByTree.get(nthB).get(nthRenameBase).put(entry.getOldPath(), entry.getNewPath());
       if(!baseRenamePaths.containsKey(entry.getOldPath())) {
         baseRenamePaths.put(entry.getOldPath(), new HashMap<>());
       }
       if(!renamePathsToBaseByTree.containsKey(nthB)){
         renamePathsToBaseByTree.put(nthB, new HashMap<>());
       }
       renamePathsToBaseByTree.get(nthB).put(entry.getNewPath(), entry.getOldPath());
       baseRenamePaths.get(entry.getOldPath()).put(nthB, entry.getNewPath());
     }
   }

    // 1) Looks like this works, but will always keep the non-renamed entry (ours). What is expected?
    // 2) This works assuming that the directory rename was detected. In case we were in root and moved to a sub dir, the root rename is not reported.
    for (DiffEntry diffEntry : diffEntries) {
      if (!renamedDiffEntry.containsKey(diffEntry.getOldId().toObjectId())) {
        renamedDiffEntry.put(diffEntry.getOldId().toObjectId(), new HashMap<>());
      }
      if (!renamedDiffEntry.containsKey(diffEntry.getNewId().toObjectId())) {
        renamedDiffEntry.put(diffEntry.getNewId().toObjectId(), new HashMap<>());
      }
      renamedDiffEntry.get(diffEntry.getOldId().toObjectId())
          .put(diffEntry.getNewId().toObjectId(), diffEntry);
      renamedDiffEntry.get(diffEntry.getNewId().toObjectId())
          .put(diffEntry.getOldId().toObjectId(), diffEntry);
    }
    if(!renameByTrees.containsKey(nthRenameBase)) {
      renameByTrees.put(nthRenameBase, new HashMap<>());
    }
    if(!renameByTrees.containsKey(nthB)) {
      // We likely don't always have nth
      renameByTrees.put(nthB, new HashMap<>());
    }
  }

  private boolean isRenameProcessed(int nth){
    return processedRenames.containsKey(nth) && processedRenames.get(nth).contains(this.trees[nth].getEntryPathString());
  }

  @Override
  AbstractTreeIterator min() throws IOException {
    for (;;) {
      final AbstractTreeIterator minRef = fastMin();
      int nthMinRef = -1;
      for(int nth = 0; nth < trees.length; nth++){
        if(trees[nth] == minRef){
          nthMinRef = nth;
          break;
        }
      }
      if(trees[this.nthRenameBase].matches == minRef && isBaseRename() && !isRenameProcessed(this.nthRenameBase)) {
        // fastMin might not be a rename, comparing to base, but base node was renamed somewhere
        fastMinHasMatch = false;
      }
      if(isRename(nthMinRef, this.nthRenameBase) && !isRenameProcessed(nthMinRef)) {
        // fastMin is a rename comparing to base itself.
        fastMinHasMatch = false;
      }
      if(!this.trees[nthMinRef].eof() && isRenameProcessed(nthMinRef)){
        for (AbstractTreeIterator t : trees) {
          if (t.matches == minRef) {
            t.next(1);
            t.matches = null;
          }
        }
        continue;
      }
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
          //?
          continue;
        }
        return minRef;
      }

      return combineDF(minRef);
    }
  }

  private AbstractTreeIterator fastMin() {
    fastMinHasMatch = true;//match if all trees has a match?

    int i = 0;
    int minRef = i;
    // Just find some entry in any tree
    while (trees[minRef].eof() && ++i < trees.length)
      minRef = i;
    if (trees[minRef].eof())
      return trees[minRef];

    boolean hasConflict = false;
    trees[minRef].matches = trees[minRef];
    // For all trees, see if current entry matches. If so, mark it (t.matches)
    // If not, this tree just emits an empty node
    // Select the tree as a match, so that it is possible to recurse.
    // In combineDF later on, each tree is seeked to the matching entry. This is done by searching forward for the same entry and seeking back if no match was found.

    while (++i < trees.length) {
      final AbstractTreeIterator t = trees[i];
      if (t.eof())
        continue;

      final int cmp =  t.pathCompare(trees[minRef]);
      if (cmp < 0) {
        if (fastMinHasMatch && isTree(trees[minRef]) && !isTree(t)
            && nameEqual(trees[minRef], t)) {
          // We used to be at a tree, but now we are at a file
          // with the same name. Allow the file to match the
          // tree anyway.
          //
          t.matches = trees[minRef];
          hasConflict = true;
        } else {
          fastMinHasMatch = false;
          t.matches = t;
          minRef = i;
        }
      } else if (cmp == 0) {
        // Exact name/mode match is best.
        //
        t.matches = trees[minRef];
      } else if (fastMinHasMatch && isTree(t) && !isTree(trees[minRef])
          && !isGitlink(trees[minRef]) && nameEqual(t, trees[minRef])) {
        // The minimum is a file (non-tree) but the next entry
        // of this iterator is a tree whose name matches our file.
        // This is a classic D/F conflict and commonly occurs like
        // this, with no gaps in between the file and directory.
        //
        // Use the tree as the minimum instead (see combineDF).
        //

        for (int k = 0; k < i; k++) {
          final AbstractTreeIterator p = trees[k];
          if (p.matches == trees[minRef])
            p.matches = t;
        }
        t.matches = t;
        minRef = i;
        hasConflict = true;
      } else
        fastMinHasMatch = false;
    }

    if (hasConflict && fastMinHasMatch && dfConflict == null)
      dfConflict = trees[minRef];
    return trees[minRef];
  }

  private static boolean nameEqual(final AbstractTreeIterator a,
      final AbstractTreeIterator b) {
    return a.pathCompare(b, TREE_MODE) == 0;
  }

  private boolean isBaseRename() {
    return baseRenamePaths.containsKey(trees[nthRenameBase].getEntryPathString());
  }


  private boolean isRename(int nthA,
      int nthB) {
    if (renamePathsByTree.containsKey(nthA) && renamePathsByTree.get(nthA).containsKey(nthB)&& renamePathsByTree.containsKey(nthB) && renamePathsByTree.get(nthB).containsKey(nthA)) {
      return renamePathsByTree.get(nthA).get(nthB).containsKey(trees[nthB].getEntryPathString()) || renamePathsByTree.get(nthB).get(nthA).containsKey(trees[nthA].getEntryPathString());
    }
    return false;
  }

  private boolean isRenameMatch(int nthA,
      int nthB) {
    if (renamePathsByTree.containsKey(nthA) && renamePathsByTree.get(nthA).containsKey(nthB)
        && renamePathsByTree.containsKey(nthB) && renamePathsByTree.get(nthB).containsKey(nthA)) {
      if (renamePathsByTree.get(nthA).get(nthB).containsKey(trees[nthB].getEntryPathString())) {
        return renamePathsByTree.get(nthA).get(nthB).get(trees[nthB].getEntryPathString())
            .equals(trees[nthA].getEntryPathString());
      }
      if (renamePathsByTree.get(nthB).get(nthA).containsKey(trees[nthA].getEntryPathString())) {
        return renamePathsByTree.get(nthB).get(nthA).get(trees[nthA].getEntryPathString())
            .equals(trees[nthB].getEntryPathString());
      }
    }
    return trees[nthA].getEntryPathString().equals(trees[nthB].getEntryPathString());
  }

  private String getRename(int nthA, int nthB){
    if (renamePathsByTree.containsKey(nthA) && renamePathsByTree.get(nthA).containsKey(nthB)) {
      return renamePathsByTree.get(nthB).get(nthA).get(trees[nthA].getEntryPathString());
    }
    return trees[nthA].getEntryPathString();
  }

  private String getDir(int nthA){
    String path  = trees[nthA].getEntryPathString();
    int endDir = path.lastIndexOf("/");
    return path.substring(0, endDir);
  }

  private String getDir(String path){
    int endDir = path.lastIndexOf("/");
    return path.substring(0, endDir);
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

        final int cmp =  t.pathCompare(minRef, 0);
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

  private AbstractTreeIterator combineRename(AbstractTreeIterator minRef)
      throws IOException {
    // Look for a possible renames forward in the tree(s)
    // as there may be a "$path/" which was renamed to "$path".
    // Make them match the current entry.
    //
    int nthMinRef = -1;
    for(int nth = 0; nth < trees.length; nth++){
      if(trees[nth] == minRef){
        nthMinRef = nth;
        break;
      }
    }
    if(isRenameProcessed(nthMinRef)){
      return minRef;
    }
    isRenameProcessing = true;
    if(nthMinRef != this.nthRenameBase){
      seekToRename(minRef, nthMinRef, this.nthRenameBase);
    }

    for(int nth = 0; nth < trees.length; nth++) {
      seekToRename(trees[this.nthRenameBase], this.nthRenameBase, nth);
      if(this.trees[nth].matches == this.trees[this.nthRenameBase]) {
       // match was found. update to match same as other minRef
        this.trees[nth].matches = this.trees[nthMinRef];
      }
    }


    return minRef;
  }

  private void seekToRename(AbstractTreeIterator minRef, int nthMinRef, int nth)
      throws IOException {
    if(nthMinRef == nth){
      return;
    }
    AbstractTreeIterator origTree = trees[nth];
    if (trees[nth].matches == minRef) {
      return;
    }
    // This may be Empty Iterator, that always has eof = true, return the original iterator
    if(trees[nth].eof()) {
      trees[nth] = trees[nth].parent;
      trees[nth].originator = origTree;
    }
    for (; ; ) {
      AbstractTreeIterator t = trees[nth];

      boolean isMatch = isRenameMatch(nthMinRef, nth);
      if (!isMatch) {
        String renamePath = getRename(nthMinRef, nth);
        if (trees[nth].getEntryPathString().startsWith(getDir(renamePath))) {
          // We are in the right node, but need to recurse into the subtree to find the file match.
          if (!isTree(minRef) && isTree(t)) {
            AbstractTreeIterator subTree = t.createSubtreeIterator(this.getObjectReader());
            subTree.depthShift = t.depthShift +1;
            trees[nth] = subTree;
            continue;
          }
          // we are in the right subtree, should just recurse into it
        }
        // We are in the wrong node. Iterate until we find the right node.
        // TODO: this should work on the file level. Is it possible to optimize on dir level?
        //
        // I suspect that the rename is not always forward in the tree.
        t.matchShift++;
        // look ahead did not work, because we are in the wrong subtree. We need to get to the right
        t.next(1);
        if (t.eof()) {
          t.back(t.matchShift);
          t.matchShift = 0;
          break;
        }
      } else {
        // We have a match here.
        //
        t.matches = minRef;
        this.renameIterator = t;
        break;
      }
    }
    // sowehow we did not find a match. reset back.
    if (trees[nth].matches != minRef) {
      while (trees[nth].depthShift != 0) {
        //trees[nth].depthShift = 0;
        // those were temp iterators and can be just ignored.
        trees[nth] = trees[nth].parent;
      }
      if(trees[nth].matchShift != 0) {
        trees[nth].back(trees[nth].matchShift);
        trees[nth].matchShift = 0;
      }
      trees[nth]= origTree;
    }

  }

  private AbstractTreeIterator combineDF(AbstractTreeIterator minRef)
      throws IOException {
    // Look for a possible D/F conflict forward in the tree(s)
    // as there may be a "$path/" which matches "$path". Make
    // such entries match this entry.
    //
    AbstractTreeIterator treeMatch = null;
    for (AbstractTreeIterator t : trees) {
      if (t.matches == minRef || t.eof())
        continue;

      for (;;) {
        final int cmp =  t.pathCompare(minRef, TREE_MODE);
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
      // Just make the rename a match, instead of the initial min?
      //
      for (AbstractTreeIterator t : trees)
        if (t.matches == minRef)
          t.matches = treeMatch;

      if (renameIterator == null && !isGitlink(minRef)) {
        renameIterator = treeMatch;
      }

      return treeMatch;
    }
    combineRename(minRef);
    return minRef;
  }

  @Override
  public boolean next() throws  IOException {
    for (int nth = 0; nth < trees.length; nth++) {
      if(isRenameProcessing && trees[nth].matches == currentHead){
        if(!processedRenames.containsKey(nth)){
          processedRenames.put(nth, new HashSet<>());
        }
        processedRenames.get(nth).add(trees[nth].getEntryPathString());
      }
      while (trees[nth].depthShift != 0) {
        trees[nth] = trees[nth].parent;
      }
      // reset everything to pre-rename shift position
      if (trees[nth].matchShift != 0) {
        trees[nth].back(trees[nth].matchShift);
        trees[nth].matchShift = 0;
      }
      if(trees[nth].originator!=null){
        AbstractTreeIterator originator = trees[nth].originator;
        trees[nth].originator = null;
        trees[nth] = originator;
      }
    }

    isRenameProcessing = false;
    return super.next();
  }

  @Override
  void popEntriesEqual() throws CorruptObjectException {
    final AbstractTreeIterator ch = currentHead;
    for (int nth = 0; nth<trees.length; nth++) {
      while (trees[nth].depthShift != 0) {
        // those were temp iterators and can be just ignored.
        trees[nth] = trees[nth].parent;
      }
      AbstractTreeIterator t = trees[nth];
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

  private static boolean nonTree(AbstractTreeIterator tree) {
    return tree.mode != 0 && !FileMode.TREE.equals(tree.mode);
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

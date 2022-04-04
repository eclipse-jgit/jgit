package org.eclipse.jgit.merge;

import static org.eclipse.jgit.merge.ResolveMerger.T_OURS;
import static org.eclipse.jgit.merge.ResolveMerger.T_THEIRS;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.diff.DiffConfig;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.NameConflictTreeWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/**
 * Holds renames for {@link ResolveMerger}.
 *
 * <p> The {@link ResolveMerger} first processes all regular diffs, skipping any diffs related to rename.
 * <p> It then processes each rename pair separately, as well as rename conflicts.
 * <p> This class computes the renames, introduced by merge sides and provides an interface for {@link ResolveMerger} to get all renames-related information.
 *
 * <p> The renames are computed related to the 'base' of the tree-way merge. If the base is not present or the source file does not exist in the base commit, the renames would not be detected.
 */
public class RenameResolver {

  /**
   * Type of the rename, detected during the merge attempt.
   */
  public enum RenameType{
    /**
     * No rename is associated with this path or the type was nt computed yet.
     */
    NO_RENAME,
    /**
     * The paths was renamed on ours (head) side.
     */
    RENAME_IN_OURS,
    /**
     * The paths was renamed on heirs (merge) side.
     */
    RENAME_IN_THEIRS,
    /**
     * The paths was renamed on both ours and theirs side to the same target paths, which did not result in conflicts.
     */
    RENAME_BOTH_NO_CONFLICT,
    /**
     * The rename was detected, by the merge under the target path is not possible.
     */
    RENAME_CONFLICT}

  /**
   * The type of the rename conflict of the rename, detected during merge.
   */
  public enum RenameConflict{
    /**
     * No conflicts were encountered.
     */
    NO_CONFLICT,
    /**
     * Ours and theirs renamed same path to different target paths.
     */
    RENAME_BOTH_SIDES_CONFLICT,
    /**
     * One side added/modified the path that the other side renamed to.
     */
    RENAME_ADD_CONFLICT,
    /**
     * One side renamed the path, another deleted
     */
    RENAME_DELETE_CONFLICT,
    /**
     * Sides renamed different source path to the same targe path.
     */
    MULTIPLE_RENAME_CONFLICT}

  /**
   * Represents a rename detected during merge.
   */
  public static class RenameEntry {
    private RenameEntry(String sourcePath){
      this.sourcePath = sourcePath;
    }
    private String sourcePath;
    private String targetPath;
    private RenameType renameType = RenameType.NO_RENAME;
    private RenameConflict renameConflict = RenameConflict.NO_CONFLICT;

    private Map<Integer, String> targetPaths = new HashMap<>();

    /**
     * Return the source path of the rename.
     * <p> This is the name of the entry in the base commit.
     * @return source path of the rename.
     */
    public String getSourcePath() {
      return sourcePath;
    }

    /**
     * Returns the target path of the rename, that will be updated by the merger.
     * @return target path of the rename.
     */
    public String getTargetPath(){
      return targetPath;
    }

    /**
     * Returns the target path of the rename by merge side ({@link ResolveMerger#T_OURS} or {@link ResolveMerger#T_THEIRS}).
     *
     * <p>It is possible that {@link ResolveMerger#T_OURS} and {@link ResolveMerger#T_THEIRS}) renamed the same {@link #sourcePath} to different paths. Then results of this method are different from {@link #getTargetPath()}.
     * @param nth index of the side in {@link ResolveMerger}.
     * @return the target path of the rename by merge side.
     */
    public String getTargetPath(int nth){
      return targetPaths.get(nth);
    }

    /**
     * Returns path, that (if) {@link ResolveMerger#T_OURS} and {@link ResolveMerger#T_THEIRS}) renamed {@link #getSourcePath()} to.
     * @return all targets for {@link #sourcePath}.
     */
    public Collection<String> getTargetPaths(){
      return targetPaths.values();
    }

    /**
     * Returns {@link RenameType}, if assigned.
     * @return {@link RenameType}
     */
    public RenameType getRenameType(){
      return renameType;
    }

    /**
     * Returns {@link RenameConflict} or {@link RenameConflict#NO_CONFLICT} if no conflict was found.
     * @return {@link RenameConflict}.
     */
    public RenameConflict getRenameConflict(){
      return renameConflict;
    }
  }
  /**
   Map of source rename paths (present in {@link ResolveMerger#T_BASE} to target rename paths by the corresponding side trees({@link ResolveMerger#T_OURS}, {@link ResolveMerger#T_THEIRS}).
   */
  private Map<String, RenameEntry> baseRenamePaths = new HashMap<>();

  /**
   * For side trees ({@link ResolveMerger#T_OURS}, {@link ResolveMerger#T_THEIRS}), holds the mapping of paths that were renamed from base in that side tree to their original paths in {@link ResolveMerger#T_BASE}.
   */
  private Map<Integer, Map<String, String>> renamePathsByTree = new HashMap<>();


  private Map<String, RenameEntry> conflictingRenames = new HashMap<>();

  /** Reader to support object loading. */
  private ObjectReader reader;
  private DiffConfig diffCfg;
  @Nullable
  private final Repository db;

  /**
   * Creates the {@link RenameResolver} holder that computes the renames for the tree-way merge.
   *
   * @param db the {@link Repository} that {@link ResolveMerger} operates on.
   * @param reader {@link ObjectReader} that is used by {@link ResolveMerger}
   * @param config config of the repository.
   * @param baseTree a base of the merge
   * @param headTree {@link ResolveMerger#T_OURS} side of the merge.
   * @param mergeTree {@link ResolveMerger#T_THEIRS} side of the merge.
   * @throws IOException
   */
  public RenameResolver(@Nullable Repository db, ObjectReader reader, Config config, RevTree baseTree,
      RevTree headTree, RevTree mergeTree) throws IOException {
    this.db = db;
    this.reader = reader;
    this.diffCfg = config.get(DiffConfig.KEY);
    addRenames(baseTree, headTree, mergeTree);
  }

  private void addRenames(@Nullable RevTree baseTree,
        RevTree head, RevTree merge) throws IOException {
    if(baseTree == null){
      // no cross-side diff computations.
      return;
    }
    RenameDetector renameDetector = new RenameDetector(reader, diffCfg);
    List<DiffEntry> headRenames = computeRenames(renameDetector, baseTree, head);
    List<DiffEntry> mergeRenames = computeRenames(renameDetector, baseTree, merge);
    for (DiffEntry entry : headRenames) {
      addRenameEntry(entry, T_OURS);
    }
    for (DiffEntry entry : mergeRenames) {
      addRenameEntry(entry, T_THEIRS);
    }

    detectRenameConflicts(headRenames, mergeRenames);

    renamePathsByTree.put(T_OURS, new HashMap<>());
    renamePathsByTree.put(T_THEIRS, new HashMap<>());
    for (Entry<String, RenameEntry> baseRename : baseRenamePaths.entrySet()) {
      RenameEntry renameEntry = baseRename.getValue();
      for (Entry<Integer, String> sideRename : renameEntry.targetPaths.entrySet()) {
        renamePathsByTree.get(sideRename.getKey())
            .put(sideRename.getValue(), baseRename.getKey());
      }
      renameEntry.renameType = assignRenameType(renameEntry);
      renameEntry.targetPath = assignTargetPath(renameEntry);
    }

  }

  private RenameType assignRenameType(RenameEntry renameEntry){
    if(renameEntry.targetPaths.size() > 1) {
      return renameEntry.targetPaths.get(T_OURS)
          .equals(renameEntry.targetPaths.get(T_THEIRS)) ? RenameType.RENAME_BOTH_NO_CONFLICT : RenameType.RENAME_CONFLICT;
    } else if (renameEntry.targetPaths.size() == 1){
      return  renameEntry.targetPaths.containsKey(T_OURS)? RenameType.RENAME_IN_OURS: RenameType.RENAME_IN_THEIRS;
    } else {
      return RenameType.NO_RENAME;
    }
  }

  private String assignTargetPath(RenameEntry renameEntry){
    if(renameEntry.renameType.equals(RenameType.NO_RENAME) || renameEntry.renameType.equals(RenameType.RENAME_CONFLICT)) {
      return null;
    } else {
      return renameEntry.targetPaths.values().stream().findAny().get();
    }
  }

  private List<DiffEntry> computeRenames(RenameDetector renameDetector, RevTree baseTree,
      RevTree otherTree)
      throws IOException {
    TreeWalk tw = new NameConflictTreeWalk(db, reader);
    tw.reset();
    tw.addTree(baseTree);
    tw.addTree(otherTree);
    tw.setFilter(TreeFilter.ANY_DIFF);

    renameDetector.reset();
    // Break down all modifies, otherwise multiple renames can not be combined.
    renameDetector.setBreakScore(100);
    renameDetector.addAll(DiffEntry.scan(tw, true));
    return renameDetector.compute();
  }

  private void addRenameEntry(DiffEntry entry, int entrySide) {
    // With break score 100 modify entries are reported as renames
    if (!entry.getChangeType().equals(ChangeType.RENAME) || entry.getOldPath()
        .equals(entry.getNewPath())) {
      return;
    }
    if (FileMode.TREE.equals(entry.getNewMode()) || FileMode.TREE.equals(entry.getOldMode())) {
      // Do not handle directory renames for now.
      return;
    }
    if (!baseRenamePaths.containsKey(entry.getOldPath())) {
      baseRenamePaths.put(entry.getOldPath(), new RenameEntry(entry.getOldPath()));
    }

    baseRenamePaths.get(entry.getOldPath()).targetPaths.put(entrySide, entry.getNewPath());
  }


  private void detectRenameConflicts(List<DiffEntry> headRenames, List<DiffEntry> mergeRenames){
    Set<String> headDeletions = headRenames.stream().filter(x -> x.getChangeType().equals(
        ChangeType.DELETE)).map(x -> x.getOldPath()).collect(
        Collectors.toSet());
    Map<String, DiffEntry> headDiffByTarget = headRenames.stream().filter(x -> !x.getChangeType().equals(ChangeType.DELETE)).collect(Collectors.toMap(x -> x.getNewPath(),
        x-> x));

    Set<String> mergeDeletions = mergeRenames.stream().filter(x -> x.getChangeType().equals(ChangeType.DELETE)).map(x -> x.getOldPath()).collect(
        Collectors.toSet());
    Map<String, DiffEntry> mergeDiffByTarget = mergeRenames.stream().filter(x -> !x.getChangeType().equals(ChangeType.DELETE)).collect(Collectors.toMap(x -> x.getNewPath(),
        x-> x));
    Set<String> offSourceRenames = new HashSet<>();
    for (Entry<String, RenameEntry> baseRename: baseRenamePaths.entrySet()) {
      Map<Integer, String> renames = baseRename.getValue().targetPaths;
      if (renames.containsKey(T_OURS) && !renames.containsKey(T_THEIRS)) {
        offSourceRenames.addAll(
            detectRenameConflicts(baseRename.getKey(), renames.get(T_OURS), mergeDiffByTarget,
                mergeDeletions));
      } else if (renames.containsKey(T_THEIRS) && !renames.containsKey(T_OURS)) {
        offSourceRenames.addAll(
            detectRenameConflicts(baseRename.getKey(), renames.get(T_THEIRS), headDiffByTarget,
                headDeletions));
      } else if (!renames.get(T_OURS).equals(renames.get(T_THEIRS))) {
        // Attempt to resolve the conflict: if source path is still present on the side, that renamed this path, do not recognize that this side as a rename. On this side, treat it as a regular add. See test cases for the rationale.
        if (mergeDiffByTarget.containsKey(baseRename.getKey()) && !headDiffByTarget.containsKey(
            baseRename.getKey())) {
          baseRename.getValue().targetPaths.remove(T_THEIRS);
        } else if (headDiffByTarget.containsKey(baseRename.getKey())
            && !mergeDiffByTarget.containsKey(baseRename.getKey())) {
          baseRename.getValue().targetPaths.remove(T_OURS);
        } else if (headDiffByTarget.containsKey(baseRename.getKey())
            && mergeDiffByTarget.containsKey(baseRename.getKey())) {
          offSourceRenames.add(baseRename.getKey());
        } else if (headDiffByTarget.containsKey(renames.get(T_THEIRS))
            || mergeDiffByTarget.containsKey(renames.get(T_OURS))) {
          // If the target renames side was modified on the opposite side, this is also not resolvable.
          offSourceRenames.add(baseRename.getKey());
        } else {
          // we are attempting to rename same source to different things
          // Only report as rename conflict if there is no correlation on the sides.
          recordSourceConflict(baseRename.getKey(), RenameConflict.RENAME_BOTH_SIDES_CONFLICT);
        }
      }
    }
    offSourceRenames.forEach(x -> baseRenamePaths.remove(x));

  }

  private Set<String> detectRenameConflicts(String sourcePath, String targetPath, Map<String, DiffEntry> otherSideDiffsByTargetPath, Set<String> otherSideDeletions) {
    Set<String> offSourceRenames = new HashSet<>();
    if (otherSideDeletions.contains(sourcePath)) {
      recordSourceConflict(sourcePath, RenameConflict.RENAME_DELETE_CONFLICT);
    }

    if (!otherSideDiffsByTargetPath.containsKey(targetPath)) {
      return Set.of();
    }

    DiffEntry otherSideTargetDiffEntry = otherSideDiffsByTargetPath.get(targetPath);
    // The other entry has the target rename path and it's source does not match the source of the rename.
    if (!otherSideTargetDiffEntry.getOldPath().equals(sourcePath)) {
      if (!otherSideTargetDiffEntry.getChangeType().equals(ChangeType.RENAME)) {
        // Add and modify counts as conflict. If the entry is present but was not changed comparing to base, it can be safely dropped.
        recordSourceConflict(sourcePath, RenameConflict.RENAME_ADD_CONFLICT);
        offSourceRenames.add(sourcePath);
      } else if (!otherSideTargetDiffEntry.getOldPath().equals(targetPath)) {
        // modifies can be broken down as renames too, exclude this case
        // switch off renames for both colliding source renames.
        recordSourceConflict(sourcePath, RenameConflict.MULTIPLE_RENAME_CONFLICT);
        recordSourceConflict(otherSideTargetDiffEntry.getOldPath(),
            RenameConflict.MULTIPLE_RENAME_CONFLICT);
        offSourceRenames.add(sourcePath);
        offSourceRenames.add(otherSideTargetDiffEntry.getOldPath());
      }
    }
    return offSourceRenames;
  }

  private void recordSourceConflict(String path, RenameConflict renameConflict) {
    if (!conflictingRenames.containsKey(renameConflict)) {
      RenameEntry entry = baseRenamePaths.get(path);
      entry.renameConflict = renameConflict;
      conflictingRenames.put(path, entry);
    }
  }

  /**
   * Returns if the base tree is positioned at the source path of the rename.
   * @param base base tree of three-way merge.
   * @return true if the base tree is positioned at the source path of the rename.
   */
  public boolean isSourceOfRename(AbstractTreeIterator base) {
    return base != null && baseRenamePaths.containsKey(base.getEntryPathString());
  }

  /**
   * Returns if the path is the source path of the rename.
   * @param path path string to check.
   * @return true if the path is the source path of the rename.
   */
  public boolean isSourceOfRename(String path) {
    return baseRenamePaths.containsKey(path);
  }

  /**
   * Returns if the side tree is positioned at the target of the rename.
   * @param nth index of the tree in three-way merge ({@link ResolveMerger#T_OURS} or {@link ResolveMerger#T_THEIRS}).
   * @param side side tree of the three-way merge.
   * @return true if the side tree is positioned at the target of the rename.
   */
  public boolean isTargetOfRename(int nth, AbstractTreeIterator side) {
    return side != null && isTargetOfRename(nth, side.getEntryPathString());
  }

  /**
   * Returns if the path is the target of a rename in nth tree.
   * @param nth index of the tree in three-way merge ({@link ResolveMerger#T_OURS} or {@link ResolveMerger#T_THEIRS})
   * @param path path string to check.
   * @return true if the path is the target of a rename in nth tree.
   */
  public boolean isTargetOfRename(int nth, String path) {
    return (renamePathsByTree.containsKey(nth) && renamePathsByTree.get(nth)
        .containsKey(path));
  }

  /**
   * Returns if the path the target of the rename in any of the side trees.
   * @param path path string to check.
   * @return path true if the path the target of the rename in any of the side trees.
   */
  public boolean isTargetRename(String path){
    return renamePathsByTree.get(T_OURS).containsKey(path) || renamePathsByTree.get(T_THEIRS).containsKey(path);
  }

  /**
   * Returns if any of the trees are positioned at the path, that is involved into rename (eiter source or target).
   *
   * @param base {@link ResolveMerger#T_BASE} {@link AbstractTreeIterator} on the three-way merge.
   * @param ours {@link ResolveMerger#T_OURS} {@link AbstractTreeIterator} on the three-way merge.
   * @param theirs {@link ResolveMerger#T_THEIRS} {@link AbstractTreeIterator} on the three-way merge.
   * @return if the entry that the trees are positioned is involved into rename (eiter source or target).
   */
  public boolean isRenameEntry(AbstractTreeIterator base,
      AbstractTreeIterator ours, AbstractTreeIterator theirs) {
    return isSourceOfRename(base) || isTargetOfRename(T_OURS, ours) || isTargetOfRename(T_THEIRS,
        theirs);
  }

  /**
   * Returns {@link RenameType} by {@code sourcePath} of the rename.
   * @param sourcePath path to return rename type for.
   * @return {@link RenameType}.
   */
  public RenameType getRenameType(String sourcePath){
    if(!isSourceOfRename(sourcePath)){
      return RenameType.NO_RENAME;
    }
    return baseRenamePaths.get(sourcePath).renameType;
  }

  /**
   * Returns {@link RenameEntry} by {@code sourcePath} of the rename.
   * @param sourcePath path to return rename entry for.
   * @return {@link RenameEntry}.
   */
  public RenameEntry getRenameEntry(String sourcePath){
    return baseRenamePaths.get(sourcePath);
  }

  /**
   * Returns {@link RenameEntry} for a detected rename conflict by its {@code sourcePath}.
   * @param sourcePath path to return rename entry for.
   * @return {@link RenameEntry} for the detected rename conflict.
   */
  public RenameEntry getRenameConflict(String sourcePath){
    return conflictingRenames.get(sourcePath);
  }

  /**
   * Returns target path of the rename by the source path.
   * @param sourcePath source path of the rename.
   * @return target path of the rename.
   */
  public String getRenameTarget(String sourcePath){
    return baseRenamePaths.get(sourcePath).targetPath;
  }

  /**
   * Returns all detected renames conflicts.
   * <p>See {@link RenameConflict} for the recognized conflicts.
   * @return detected rename conflicts.
   */
  public Set<Entry<String, RenameEntry>> getSourceConflicts(){
    return conflictingRenames.entrySet();
  }

  /**
   * Returns all detected renames excluding rename conflicts.
   * @return detected renames.
   */
  public Set<Entry<String, RenameEntry>> getRenamesNoConflicts(){
    return this.baseRenamePaths.entrySet().stream().filter(x -> !conflictingRenames.containsKey(x.getKey())).collect(
        Collectors.toSet());
  }
}

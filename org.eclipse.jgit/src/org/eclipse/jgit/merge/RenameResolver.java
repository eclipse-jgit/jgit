package org.eclipse.jgit.merge;

import static org.eclipse.jgit.merge.ResolveMerger.T_FILE;
import static org.eclipse.jgit.merge.ResolveMerger.T_INDEX;
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
 * <p> It than processes each rename pair separately, as well as any rename conflict.
 */
public class RenameResolver {

  public enum RenameConflict{NO_CONFLICT, RENAME_BOTH_SIDES_CONFLICT, RENAME_ADD_CONFLICT, RENAME_DELETE_CONFLICT, MULTIPLE_RENAME_CONFLICT}
  public enum RenameType{NO_RENAME, RENAME_IN_OURS, RENAME_IN_THEIRS, RENAME_BOTH_NO_CONFLICT, RENAME_CONFLICT}

  public static class RenameEntry {
    RenameEntry(String sourcePath){
      this.sourcePath = sourcePath;
    }
    protected String sourcePath;
    protected String targetPath;
    protected RenameType renameType = RenameType.NO_RENAME;
    protected RenameConflict renameConflict = RenameConflict.NO_CONFLICT;

    protected Map<Integer, String> targetPaths = new HashMap<>();

    public String getSourcePath() {
      return sourcePath;
    }

    public String getTargetPath(){
      return targetPath;
    }

    public String getTargetPath(int nth){
      return targetPaths.get(nth);
    }

    public RenameType getRenameType(){
      return renameType;
    }

    public RenameConflict getRenameConflict(){
      return renameConflict;
    }
  }
  /**
   Map of source rename paths (present in {@link ResolveMerger#T_BASE} to target rename paths by the corresponding side trees({@link ResolveMerger#T_OURS}, {@link ResolveMerger#T_THEIRS})
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

  public RenameResolver(@Nullable Repository db, ObjectReader reader, DiffConfig diffCfg, RevTree baseTree,
      RevTree head, RevTree merge) throws IOException {
    this.db = db;
    this.reader = reader;
    this.diffCfg = diffCfg;
    addRenames(baseTree, head, merge);
  }

  public void addRenames(@Nullable RevTree baseTree,
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
        // Attempt to resolve the conflict: treat rename as regular add, if source is still present on one of the sides.
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

  public void recordSourceConflict(String path, RenameConflict renameConflict) {
    if (!conflictingRenames.containsKey(renameConflict)) {
      RenameEntry entry = baseRenamePaths.get(path);
      entry.renameConflict = renameConflict;
      conflictingRenames.put(path, entry);
    }
  }

  public boolean isBaseRename(AbstractTreeIterator base) {
    return base != null && baseRenamePaths.containsKey(base.getEntryPathString());
  }

  public boolean isBaseRename(String path) {
    return baseRenamePaths.containsKey(path);
  }

  public boolean isRenameFromBase(int nthA, AbstractTreeIterator side) {
    return side != null && isRenameFromBase(nthA, side.getEntryPathString());
  }

  public boolean isRenameFromBase(int nthA, String path) {
    return (renamePathsByTree.containsKey(nthA) && renamePathsByTree.get(nthA)
        .containsKey(path));
  }

  public boolean isTargetRename(String path){
    return renamePathsByTree.get(T_OURS).containsKey(path) || renamePathsByTree.get(T_THEIRS).containsKey(path);
  }

  public boolean isRenameEntry(AbstractTreeIterator base,
      AbstractTreeIterator ours, AbstractTreeIterator theirs) {
    return isBaseRename(base) || isRenameFromBase(T_OURS, ours) || isRenameFromBase(T_THEIRS,
        theirs);
  }

  public RenameType getRenameType(String sourcePath){
    if(!isBaseRename(sourcePath)){
      return RenameType.NO_RENAME;
    }
    return baseRenamePaths.get(sourcePath).renameType;
  }

  public RenameEntry getRenameEntry(String sourcePath){
    return baseRenamePaths.get(sourcePath);
  }

  public RenameEntry getRenameConflict(String sourcePath){
    return baseRenamePaths.get(sourcePath);
  }

  public String getRenameTarget(String sourcePath){
    return baseRenamePaths.get(sourcePath).targetPath;
  }

  public Set<Entry<String, RenameEntry>> getSourceConflicts(){
    return conflictingRenames.entrySet();
  }

  public Set<Entry<String, RenameEntry>> getRenamesNoConflicts(){
    return this.baseRenamePaths.entrySet().stream().filter(x -> !conflictingRenames.containsKey(x.getKey())).collect(
        Collectors.toSet());
  }
}

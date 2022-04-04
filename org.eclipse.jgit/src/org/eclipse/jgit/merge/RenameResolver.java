package org.eclipse.jgit.merge;

import static org.eclipse.jgit.merge.ResolveMerger.T_FILE;
import static org.eclipse.jgit.merge.ResolveMerger.T_INDEX;
import static org.eclipse.jgit.merge.ResolveMerger.T_OURS;
import static org.eclipse.jgit.merge.ResolveMerger.T_THEIRS;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.api.errors.CanceledException;
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

  public class RenameEntry {
    public RenameEntry(String sourcePath){
      this.sourcePath = sourcePath;
    }
    public String sourcePath;
    public String targetPath;
    public RenameType renameType = RenameType.NO_RENAME;
    public Map<Integer, String> targetPaths = new HashMap<>();
  }
  /**
   Map of source rename paths (present in {@link ResolveMerger#T_BASE} to target rename paths by the corresponding side trees({@link ResolveMerger#T_OURS}, {@link ResolveMerger#T_THEIRS})
   */
  public Map<String, RenameEntry> baseRenamePaths = new HashMap<>();

  /**
   * For side trees ({@link ResolveMerger#T_OURS}, {@link ResolveMerger#T_THEIRS}), holds the mapping of paths that were renamed from base in that side tree to their original paths in {@link ResolveMerger#T_BASE}.
   */
  public Map<Integer, Map<String, String>> renamePathsByTree = new HashMap<>();


  public Map<String, RenameConflict> conflictingSourceRenamePath = new HashMap<>();
  public Map<String, RenameConflict> conflictingTargetRenamePath = new HashMap<>();

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
      renameEntry.renameType = getRenameType(renameEntry);
      renameEntry.targetPath = getTargetPath(renameEntry);
    }

  }

  private RenameType getRenameType(RenameEntry renameEntry){
    if(renameEntry.targetPaths.size() > 1) {
      return renameEntry.targetPaths.get(T_OURS)
          .equals(renameEntry.targetPaths.get(T_THEIRS)) ? RenameType.RENAME_BOTH_NO_CONFLICT : RenameType.RENAME_CONFLICT;
    } else if (renameEntry.targetPaths.size() == 1){
      return  renameEntry.targetPaths.containsKey(T_OURS)? RenameType.RENAME_IN_OURS: RenameType.RENAME_IN_THEIRS;
    } else {
      return RenameType.NO_RENAME;
    }
  }
  private String getTargetPath(RenameEntry renameEntry){
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

  private Set<String> detectRenameConflicts(String sourcePath, String sideTargetPath, Map<String, DiffEntry> otherSideDiffsByTargetPath, Set<String> otherSideDeletions) {
    Set<String> offSourceRenames = new HashSet<>();
    if (otherSideDeletions.contains(sourcePath)) {
      recordSourceConflict(sourcePath, RenameConflict.RENAME_DELETE_CONFLICT);
      recordTargetConflict(sideTargetPath, RenameConflict.RENAME_DELETE_CONFLICT);
    }
    if(!otherSideDiffsByTargetPath.containsKey(sideTargetPath)){
      return Set.of();
    }

    DiffEntry otherSideTargetDiffEntry = otherSideDiffsByTargetPath.get(sideTargetPath);
    if (!otherSideTargetDiffEntry.getOldPath().equals(sourcePath)) {
      if(!otherSideDiffsByTargetPath.get(
          sideTargetPath).getChangeType().equals(ChangeType.RENAME)) {
        // both modifies and add counts, since modifies can entirely replace the paths
        // also covers the case when the target file was present in base
        recordSourceConflict(sourcePath, RenameConflict.RENAME_ADD_CONFLICT);
        recordTargetConflict(sideTargetPath, RenameConflict.RENAME_ADD_CONFLICT);
        offSourceRenames.add(sourcePath);
      } else if(!otherSideTargetDiffEntry.getOldPath().equals(sideTargetPath)){
        // modifies can be broken down as renames too.
        // switch off renames for both colliding source renames.
        recordSourceConflict(sourcePath, RenameConflict.MULTIPLE_RENAME_CONFLICT);
        recordSourceConflict(otherSideTargetDiffEntry.getOldPath(), RenameConflict.MULTIPLE_RENAME_CONFLICT);
        recordTargetConflict(sideTargetPath, RenameConflict.MULTIPLE_RENAME_CONFLICT);
        offSourceRenames.add(sourcePath);
        offSourceRenames.add(otherSideTargetDiffEntry.getOldPath());
      }
    }
    return offSourceRenames;
  }

  public void recordSourceConflict(String path, RenameConflict renameType){
    if(!conflictingSourceRenamePath.containsKey(renameType)) {
      conflictingSourceRenamePath.put(path, renameType);
    }
  }

  public void recordTargetConflict(String path, RenameConflict renameType){
    if(!conflictingTargetRenamePath.containsKey(renameType)) {
      conflictingTargetRenamePath.put(path, renameType);
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

  public boolean isRenameEntry(AbstractTreeIterator base,
      AbstractTreeIterator ours, AbstractTreeIterator theirs) {
    return isBaseRename(base) || isRenameFromBase(T_OURS, ours) || isRenameFromBase(T_THEIRS,
        theirs);
  }

  public RenameType getRenameType(AbstractTreeIterator base){
    if(base == null || !isBaseRename(base)){
      return RenameType.NO_RENAME;
    }
    return baseRenamePaths.get(base.getEntryPathString()).renameType;
  }
  public RenameEntry getRenameEntry(AbstractTreeIterator base){
    return baseRenamePaths.get(base.getEntryPathString());
  }

  public String getRenameTarget(String sourcePath){
    return baseRenamePaths.get(sourcePath).targetPath;
  }
}

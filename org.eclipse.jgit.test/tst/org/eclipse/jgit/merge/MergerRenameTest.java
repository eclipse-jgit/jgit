/*
 * Copyright (C) 2012, 2020 Robin Stocker <robin@nibor.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.merge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.util.Strings;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.junit.Before;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class MergerRenameTest extends RepositoryTestCase {

  @DataPoints
  public static MergeStrategy[] strategiesUnderTest = new MergeStrategy[] {
      MergeStrategy.RECURSIVE, MergeStrategy.RESOLVE };

  @Before
  public void enableRename() throws IOException, ConfigInvalidException {
    StoredConfig config = db.getConfig();
    config.setString(ConfigConstants.CONFIG_DIFF_SECTION, null, ConfigConstants.CONFIG_KEY_RENAMES, "true");
    config.save();
  }


  private AbstractTreeIterator getTreeIterator(String name) throws IOException {
    final ObjectId id = db.resolve(name);
    if (id == null)
      throw new IllegalArgumentException(name);
    final CanonicalTreeParser p = new CanonicalTreeParser();
    try (ObjectReader or = db.newObjectReader(); RevWalk rw = new RevWalk(db)) {
      p.reset(or, rw.parseTree(id));
      return p;
    }
  }

  public void testRename_merged(MergeStrategy strategy, String originalName, String originalContent, String oursName, String oursContent, String theirsName, String theirsContent, Map<String, String> expectedFileContents) throws Exception {

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalName, originalContent);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put(oursName, oursContent);
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put(theirsName, theirsContent);
    testRename_merged(strategy, originalFiles, oursFiles, theirsFiles, expectedFileContents);
  }

  public void testRename_merged(MergeStrategy strategy, Map<String, String> originalFilesToContents,  Map<String, String> oursFilesToContents,  Map<String, String> theirsFilesToContents, Map<String, String> expectedFileContents) throws Exception {
    if (!strategy.equals(MergeStrategy.RECURSIVE)) {
      return;
    }
    MergeResult mergeResult = mergeRename(strategy, originalFilesToContents, oursFilesToContents, theirsFilesToContents);
    assertEquals(mergeResult.getMergeStatus(), MergeStatus.MERGED);
    Set<String> expectedIndexContent = new HashSet<>();
    for (Entry<String, String> expectedFile : expectedFileContents.entrySet()) {
      if(expectedFile.getValue() != null) {
        assertEquals(expectedFile.getValue(), read(expectedFile.getKey()));
        expectedIndexContent.add(String.format("%s, mode:100644, content:%s", expectedFile.getKey(),
            expectedFile.getValue()));
      } else {
        assertFalse(check(expectedFile.getKey()));
      }
    }
    // index contains only the expected files. Everything was merged.
    Set<String> stagedFiles = Arrays.asList(indexState(CONTENT).split("\\[|\\]")).stream()
        .filter(s ->
            !Strings.isNullOrEmpty(s)).collect(Collectors.toSet());
    assertEquals(stagedFiles, expectedIndexContent);
  }

  public void testRename_withConflict(MergeStrategy strategy, Map<String, String> originalFilesToContents,  Map<String, String> oursFilesToContents,  Map<String, String> theirsFilesToContents, Map<String, String> expectedFileContents, Set<String> expectedConflicts, Set<String> expectedIndexContent) throws Exception {
    if (!strategy.equals(MergeStrategy.RECURSIVE)) {
      return;
    }
    MergeResult mergeResult = mergeRename(strategy, originalFilesToContents, oursFilesToContents, theirsFilesToContents);
    assertEquals(mergeResult.getMergeStatus(), MergeStatus.CONFLICTING);
    assertTrue(mergeResult.getFailingPaths() == null);
    assertEquals(mergeResult.getConflicts().keySet(), expectedConflicts);
    for (Entry<String, String> expectedFile : expectedFileContents.entrySet()) {
      if (expectedFile.getValue() == null) {
        assertFalse(check(expectedFile.getKey()));
      } else if (expectedFile.getValue().startsWith("<<<<<<<")) {
        assertTrue(read(expectedFile.getKey()).contains(expectedFile.getValue()));
        //assertEquals(read(expectedFile.getKey()), expectedFile.getValue());
      } else {
        assertEquals(expectedFile.getValue(), read(expectedFile.getKey()));
      }
    }

    Set<String> stagedFiles = Arrays.asList(indexState(CONTENT).split("\\[|\\]")).stream()
        .filter(s ->
            !Strings.isNullOrEmpty(s)).collect(Collectors.toSet());
    assertEquals(stagedFiles, expectedIndexContent);
  }

  private MergeResult mergeRename(MergeStrategy strategy, Map<String, String> originalFilesToContents,  Map<String, String> oursFilesToContents,  Map<String, String> theirsFilesToContents) throws Exception {
    Git git = Git.wrap(db);
    // master
    for (Entry<String, String> originalFile : originalFilesToContents.entrySet()) {
      writeTrashFile(originalFile.getKey(), originalFile.getValue());
      git.add().addFilepattern(originalFile.getKey()).call();
    }
    RevCommit commitI = git.commit().setMessage("Initial commit").call();

    git.checkout().setCreateBranch(true).setStartPoint(commitI).setName("second-branch").call();
    for (Entry<String, String> originalFile : originalFilesToContents.entrySet()) {
      // test/file1 is renamed to test/sub/file1 on second-branch
      git.rm().addFilepattern(originalFile.getKey()).call();
    }
    for (Entry<String, String> theirsFile : theirsFilesToContents.entrySet()) {
      writeTrashFile(theirsFile.getKey(), theirsFile.getValue());
      git.add().addFilepattern(theirsFile.getKey()).call();
    }
    RevCommit renameCommit = git.commit().setMessage("Rename file").call();

    // back to master, modify file
    git.checkout().setName("master").call();

    for (Entry<String, String> originalFile : originalFilesToContents.entrySet()) {
      git.rm().addFilepattern(originalFile.getKey()).call();
    }
    for (Entry<String, String> oursFile : oursFilesToContents.entrySet()) {
      writeTrashFile(oursFile.getKey(), oursFile.getValue());
      git.add().addFilepattern(oursFile.getKey()).call();
    }

    RevCommit modifyContentCommit = git.commit().setMessage("Commit slightly modified content")
        .call();

    // Merge master into second-branch
    return git.merge().include(renameCommit).setStrategy(strategy).call();
  }

  @DataPoints("singleRenamePairs")
  public static final List<Entry<String, String>> singleRenamePairsData = List.of(
      // Direct rename of file
      Map.entry("test/file2","test/file1"),
      Map.entry("test/file1","test/file2"),
      Map.entry("test/a/file1","test/z/file2"),
      Map.entry("test/z/file1","test/a/file2"),
      // Move file to subdir
      Map.entry("test/file1", "test/w/file1"),
      Map.entry("test/file1", "test/a/file1"),
      // Move to parent dir
      Map.entry("test/w/file1", "test/file1"),
      Map.entry("test/a/file1", "test/file1"));

  @DataPoints("renameLists")
  public static final List<List<Entry<String, String>>> renameListsData  = List.of(
      // Direct rename of file
      List.of(Map.entry("test/file2","test/file1")),
      List.of(Map.entry("test/file1","test/file2")),
      List.of(Map.entry("test/a/file1","test/z/file2")),
      List.of(Map.entry("test/z/file1","test/a/file2")),
      // Move file to subdir
      List.of(Map.entry("test/file1", "test/w/file1")),
      List.of(Map.entry("test/file1", "test/a/file1")),
      // Move to parent dir
      List.of(Map.entry("test/w/file1", "test/file1")),
       List.of(Map.entry("test/a/file1", "test/file1"))
  );

  @DataPoints("renameListsSplit")
  public static final List<List<Entry<String, String>>> renameListsSplitData  = List.of(
      // Direct rename of file
      List.of(Map.entry("test/a/file1","test/a1/file1"), Map.entry("test/a/file2","test/a2/file2")),
      List.of(Map.entry("test/a/file1","test/a/filex"), Map.entry("test/a/file2","test/a/w/file1"))
  );
  @DataPoints("fileAdditions")
  public static final List<String> fileAdditionsData = List.of("file1.1", "file3","file0","a/file", "z/file", "a/sub/file", "z/sub/file");

  public void checkRenameTo_merged(MergeStrategy strategy, List<Entry<String, String>> renamePairs, boolean isRenameInOurs) throws Exception {
    // classic conflict, renamed on one side, modified on other
    // take the rename with modified content
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    Map<String, String> originalFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      originalFiles.put(renamePair.getKey(), originalContent + renamePair.getKey());
    }
    Map<String, String> noRenameFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      noRenameFiles.put(renamePair.getKey(), slightlyModifiedContent + renamePair.getKey());
    }
    Map<String, String> renameFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      renameFiles.put(renamePair.getValue(), originalContent + renamePair.getKey());
    }
    String renameDir = getDir(renamePairs.get(0).getValue());
    Map<String, String> expectedFiles = new HashMap<>();
    // This should all be under rename dir?
    for (Entry<String, String> renamePair : renamePairs) {
      expectedFiles.put(renamePair.getValue(), slightlyModifiedContent + renamePair.getKey());
    }
    testRename_merged(strategy, originalFiles, isRenameInOurs ? renameFiles : noRenameFiles,
        isRenameInOurs ? noRenameFiles : renameFiles, expectedFiles);
  }

  /**
   * Example results:
   * B:"test/file1", X
   * O:"test/file1", Y
   * T:"test/file2", X
   *
   * Index:
   * "test/file2", Y
   *
   * B:"test/file1", X
   * O:"test/file1", Y
   * T:"test/a/file1", X
   *
   * Index:
   * "test/a/file1", Y
   *
   * B:"test/z/file1", X
   * O:"test/z/file1", Y
   * T:"test/file1", X
   *
   * Index:
   * "test/file1", X
   */
  @Theory
  public void checkRenameFile_modifyConflict_renameInOurs(MergeStrategy strategy, @FromDataPoints("renameLists") List<Entry<String, String>> renamePairs) throws Exception {
    checkRenameTo_merged(strategy, renamePairs, true);

  }

  @Theory
  public void checkRenameFile_modifyConflict_renameInTheirs(MergeStrategy strategy, @FromDataPoints("renameLists") List<Entry<String, String>> renamePairs) throws Exception {
    checkRenameTo_merged(strategy, renamePairs, false);
  }

  @Theory
  public void checkRenameSplitDir_modifyConflict_renameInOurs(MergeStrategy strategy, @FromDataPoints("renameListsSplit") List<Entry<String, String>> renamePairs) throws Exception {
    checkRenameTo_merged(strategy, renamePairs, true);

  }

  @Theory
  public void checkRenameSplitDir_modifyConflict_renameInTheirs(MergeStrategy strategy, @FromDataPoints("renameListsSplit") List<Entry<String, String>> renamePairs) throws Exception {
    checkRenameTo_merged(strategy, renamePairs, false);
  }

  /**
   * Example results:
   * B:"test/file1"
   * O:"test/file1", "test/file1.1"
   * T:"test/file2"
   *
   * Index:
   * "test/file2", "test/file1.1"
   *
   * Example results:
   * B:"test/file1"
   * O:"test/file1", "test/file1.1"
   * T:"test/sub/file1"
   *
   * Index:
   * // Ideally, everything moved to test/sub?
   * "test/sub/file1", "test/file1.1"
   *
   * B:"test/sub/file1"
   * O:"test/sub/file1", "test/sub/file1.1"
   * T:"test/file1"
   *
   * Index:
   * // Ideally, everything moved one level up?
   * "test/file1", "test/sub/file1.1"
   */
  @Theory
  public void checkRenameFile_noContentModification(MergeStrategy strategy, @FromDataPoints("singleRenamePairs") Entry<String, String> renamePair,  @FromDataPoints("fileAdditions") String fileAddition, boolean isRenameInOurs) throws Exception {
    String originalContent = "a\nb\nc";
    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(renamePair.getKey(), originalContent);
    Map<String, String> nonRenameFiles = new HashMap<>();
    nonRenameFiles.put(renamePair.getKey(), originalContent);
    String addedFile = getDir(renamePair.getKey()) + "/" + fileAddition;
    nonRenameFiles.put(addedFile, "One file was added in ours");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renamePair.getValue(), originalContent);
    Map<String, String> expectedFiles = new HashMap<>();
    // This is merged, however everything is under test/a/, because the directory was not detected as rename.
    // Ideally, this should be all under test/sub/
    // With prior logic, this would have test/sub/file1 and test/a/file1
    expectedFiles.put(renamePair.getValue(), originalContent);
    expectedFiles.put(addedFile, "One file was added in ours");
    testRename_merged(strategy, originalFiles,
        isRenameInOurs ? filesWithRename : nonRenameFiles,
        isRenameInOurs ? nonRenameFiles : filesWithRename, expectedFiles);
  }

  @Theory
  public void checkRenameMoveToParent_noContentModification(MergeStrategy strategy, boolean isRenameInOurs) throws Exception {
    String originalContent = "a\nb\nc";
    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put("test/sub/file1", originalContent);
    Map<String, String> nonRenameFiles = new HashMap<>();
    nonRenameFiles.put("test/sub/file1", originalContent);
    nonRenameFiles.put("test/sub/file1.1", "One file was added in ours");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put("test/file1", originalContent);
    Map<String, String> expectedFiles = new HashMap<>();
    // This is merged, however everything is under test/a/, because the directory was not detected as rename.
    // Ideally, this should be all under test/sub/
    // With prior logic, this would have test/sub/file1 and test/a/file1
    expectedFiles.put("test/file1", originalContent);
    expectedFiles.put("test/sub/file1.1", "One file was added in ours");
    testRename_merged(strategy, originalFiles,
        isRenameInOurs ? filesWithRename : nonRenameFiles,
        isRenameInOurs ? nonRenameFiles : filesWithRename, expectedFiles);
  }

  @Theory
  public void checkRenameToSubDir_merged(MergeStrategy strategy) throws Exception {
    // classic conflict, renamed in theirs, modified in ours
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String oursFilename = originalFilename;
    String oursContent = slightlyModifiedContent;
    String theirsFilename = "test/sub/file1";
    String theirsContent = originalContent;

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(theirsFilename, slightlyModifiedContent);
    testRename_merged(strategy, originalFilename, originalContent, oursFilename,
        oursContent, theirsFilename, theirsContent, expectedFiles);

  }


  @Theory
  public void checkRenameMoveToParent_merged(MergeStrategy strategy) throws Exception {
    // classic conflict, renamed in thiers, modified in ours
    String originalFilename = "test/sub/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String oursFilename = originalFilename;
    String oursContent = slightlyModifiedContent;
    String theirsFilename = "test/file2";
    String theirsContent = originalContent;
    Map<String, String > originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String > oursFiles = new HashMap<>();
    oursFiles.put(oursFilename, oursContent);
    Map<String, String > theirsFiles = new HashMap<>();
    theirsFiles.put(theirsFilename, theirsContent);

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put("test/file2", slightlyModifiedContent);
    testRename_merged(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles);

  }

  /**
   *  B: test/file1 X_1
   *  O: test/file1 X_1(2);
   *  T: test/file2 X_1(3);
   *  Expected:
   *  test/file2 X_1(2,3,4)
   *
   *  Merged with merged content, regardless of whether or not either side had modifications.
   */
  @Theory
  public void checkRenameModify_merged(MergeStrategy strategy, boolean isRenameInOurs, boolean modifyInRename, boolean modifyInOther) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent1 = "z\na\nb\nc";

    String slightlyModifiedContent2 = "a\nb\nc\nd";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> renameFiles = new HashMap<>();
    renameFiles.put(renameFilename, modifyInRename ? slightlyModifiedContent1 : originalContent);
    Map<String, String> otherFiles = new HashMap<>();
    otherFiles.put(originalFilename, modifyInOther ? slightlyModifiedContent2 : originalContent);

    String expectedRenameContent = modifyInRename && modifyInOther ? "z\na\nb\nc\nd"
        : modifyInRename ? slightlyModifiedContent1
            : modifyInOther ? slightlyModifiedContent2 : originalContent;
    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, expectedRenameContent);
    expectedFiles.put(originalFilename, null);

    testRename_merged(strategy, originalFiles, isRenameInOurs ? renameFiles : otherFiles,
        isRenameInOurs ? otherFiles : renameFiles, expectedFiles);
  }

  /**
   *  Note: those tests are not comprehensive and do not cover all possible content conflicts, e.g.
   *  git link merging, mode conflicts or file/dir conflicts.
   *
   *  B: test/file1 X_1
   *  O: test/file1 X(2);
   *  T: test/file2 X(3);
   *  Expected:
   *  test/file2 X_2 vs X_3 conflict
   *
   *  The rename conflict was resolved, but there is a merge conflict on the content.
   *
   * // The conflict reporting is inconsistent with the rename conflict reporting.
   * // The original file is completely missing from the merge commit, but maybe this is fine.
   */
  @Theory
  public void checkRenameModify_conflict(MergeStrategy strategy, boolean isRenameInOurs) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String oursContent = "a\nb\nc\nx";

    String theirsContent = "a\nb\nc\nd";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> renameFiles = new HashMap<>();
    renameFiles.put(renameFilename, isRenameInOurs ? oursContent : theirsContent);
    Map<String, String> otherFiles = new HashMap<>();
    otherFiles.put(originalFilename, isRenameInOurs ? theirsContent : oursContent);

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, "<<<<<<< HEAD\n"
        + "x\n"
        + "=======\n"
        + "d\n");
    expectedFiles.put(originalFilename, null);

    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename);
    Set<String> expectedIndex = new HashSet<>();
    expectedIndex.add(
        String.format("%s, mode:100644, stage:1, content:%s", renameFilename, originalContent));
    expectedIndex.add(String.format("%s, mode:100644, stage:2, content:%s",
        renameFilename, oursContent));
    expectedIndex.add(String.format("%s, mode:100644, stage:3, content:%s",
        renameFilename, theirsContent));

    testRename_withConflict(strategy, originalFiles, isRenameInOurs ? renameFiles : otherFiles,
        isRenameInOurs ? otherFiles : renameFiles, expectedFiles, expectedConflicts, expectedIndex);
  }

  /**
   *  B: test/file1 X_1
   *  O: test/file2 X(2);
   *  T: test/file2 X(3);
   *  Expected:
   *  test/file2 X_2 vs X_3 conflict
   *
   *  Same as above, but both sides renamed.
   *
   */
  @Theory
  public void checkRenameBothModify_conflict(MergeStrategy strategy) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String oursContent = "a\nb\nc\nx";

    String theirsContent = "a\nb\nc\nd";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> oursFiles= new HashMap<>();
    oursFiles.put(renameFilename, oursContent);
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put(renameFilename, theirsContent);

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, "<<<<<<< HEAD\n"
        + "x\n"
        + "=======\n"
        + "d\n");
    expectedFiles.put(originalFilename, null);

    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename);
    Set<String> expectedIndex = new HashSet<>();
    expectedIndex.add(
        String.format("%s, mode:100644, stage:1, content:%s", renameFilename, originalContent));
    expectedIndex.add(String.format("%s, mode:100644, stage:2, content:%s",
        renameFilename, oursContent));
    expectedIndex.add(String.format("%s, mode:100644, stage:3, content:%s",
        renameFilename, theirsContent));

    testRename_withConflict(strategy, originalFiles, oursFiles,
        theirsFiles, expectedFiles, expectedConflicts, expectedIndex);
  }


  /**
   *  B: test/file1 X_1
   *  O: test/file2 X_1(2);
   *  T: test/file3 X_1(3);
   *  Expected:
   *  test/file3 vs test/file2 conflict
   *
   *  Conflict with both file path present in the index.
   */
  @Theory
  public void checkRenameOnBothSides_conflict(MergeStrategy strategy, boolean modifyInOurs, boolean modifyInTheirs) throws Exception {
    // renamed on both sides to different names. Should be conflicting
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String oursFilename = "test/file2";
    String oursContent = modifyInOurs?slightlyModifiedContent: originalContent;
    String theirsFilename = "test/file3";
    String theirsContent = modifyInTheirs? slightlyModifiedContent:originalContent;

    Map<String, String > originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String > oursFiles = new HashMap<>();
    oursFiles.put(oursFilename, oursContent);
    Map<String, String > theirsFiles = new HashMap<>();
    theirsFiles.put(theirsFilename, theirsContent);

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(oursFilename, oursContent);
    expectedFiles.put(theirsFilename, theirsContent);
    expectedFiles.put(originalFilename, null);

    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(originalFilename);
    expectedConflicts.add(oursFilename);
    expectedConflicts.add(theirsFilename);

    Set<String> expectedIndex = new HashSet<>();
    expectedIndex.add(String.format("%s, mode:100644, stage:1, content:%s", originalFilename, originalContent));
    expectedIndex.add(String.format("%s, mode:100644, stage:2, content:%s", oursFilename, oursContent));
    expectedIndex.add(String.format("%s, mode:100644, stage:3, content:%s", theirsFilename, theirsContent));
    testRename_withConflict(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles, expectedConflicts, expectedIndex);
  }


  /**
   *  B: test/file1 X_1
   *  O: test/file2 X_1(2);
   *  T: test/file2 X_1(3);
   *  Expected:
   *  test/file2 X_1(2,3,4) ;
   *
   *  Merged with merged content
   */

  @Theory
  public void checkRenameOnBothSidesSameName_merged(MergeStrategy strategy, boolean modifyInOurs, boolean modifyInTheirs) throws Exception {
    // renamed on both sides the same name. Merges cleanly with the new content.
    // This should happen regardless of whether the content was modified
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent1 = "z\na\nb\nc";

    String slightlyModifiedContent2 = "a\nb\nc\nd";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put(renameFilename, modifyInOurs ? slightlyModifiedContent1 : originalContent);
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put(renameFilename, modifyInTheirs ? slightlyModifiedContent2 : originalContent);

    String expectedRenameContent = modifyInOurs && modifyInTheirs ? "z\na\nb\nc\nd"
        : modifyInOurs ? slightlyModifiedContent1
            : modifyInTheirs ? slightlyModifiedContent2 : originalContent;
    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, expectedRenameContent);
    expectedFiles.put(originalFilename, null);

    testRename_merged(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles);
  }


  /**
   * // File is missing on one side
   *  B: test/file1 X_1
   *  O:
   *  T: test/file1 X_2;
   */
  @Theory
  public void checkSimpleDelete_conflict(MergeStrategy strategy, boolean isDeletInOurs) throws Exception {
    // renamed on both sides to different names. Should be conflicting
    isDeletInOurs = true;
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> filesWithDelete = new HashMap<>();
    Map<String, String> filesWithModify = new HashMap<>();
    filesWithModify.put(originalFilename, slightlyModifiedContent);
    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(originalFilename);
    expectedConflicts.add(originalFilename);
    Set<String> expectedIndex = new HashSet<>();
    int modifyStage = isDeletInOurs ? DirCacheEntry.STAGE_3 : DirCacheEntry.STAGE_2;
    expectedIndex.add(
        String.format("%s, mode:100644, stage:1, content:%s", originalFilename, originalContent));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", originalFilename, modifyStage, slightlyModifiedContent));

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(originalFilename, slightlyModifiedContent);

    testRename_withConflict(strategy, originalFiles,
        isDeletInOurs ? filesWithDelete : filesWithModify,
        isDeletInOurs ? filesWithModify : filesWithDelete, expectedFiles, expectedConflicts,
        expectedIndex);
  }

  /**
   * // File is missing on one side
   *  B: test/file1 X_1
   *  O:
   *  T: test/file2 X_1;
   *  Expected:
   *  delete vs rename conflict
   *  M: test/file2 X_2 with conflict
   */
  @Theory
  public void checkRenameOnDelete_conflict(MergeStrategy strategy, boolean isRenameInOurs, boolean shouldModify) throws Exception {

    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> filesWithDelete = new HashMap<>();
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename, shouldModify ? slightlyModifiedContent : originalContent);
    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename);
    expectedConflicts.add(originalFilename);
    Set<String> expectedIndex = new HashSet<>();
    int renameStage = isRenameInOurs ? DirCacheEntry.STAGE_2 : DirCacheEntry.STAGE_3;
    int deleteStage = isRenameInOurs ? DirCacheEntry.STAGE_3 : DirCacheEntry.STAGE_2;
    expectedIndex.add(
        String.format("%s, mode:100644, stage:1, content:%s", originalFilename, originalContent));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename, renameStage,
            shouldModify ? slightlyModifiedContent : originalContent));

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, shouldModify ? slightlyModifiedContent : originalContent);
    expectedFiles.put(originalFilename, null);

    testRename_withConflict(strategy, originalFiles,
        isRenameInOurs ? filesWithRename : filesWithDelete,
        isRenameInOurs ? filesWithDelete : filesWithRename, expectedFiles, expectedConflicts,
        expectedIndex);
  }

  /**
   * File is present, but has completely different content
   *  B: test/file1 X_1
   *  O: test/file1 Y
   *  T: test/file2 X_1;
   *  Expected:
   *  Since we do not compute cross-side similarity, the rename is detacted and the files are content-merged.
   *  Without rename detaction, we would have an Modify/delete conflict.
   *  This case seems obvious, however see what happens if the file was replaced on both sides.
   *  M: test/file2 Y
   */
  @Theory
  public void checkRenameWithReplace_merged(MergeStrategy strategy, boolean isRenameInOurs) throws Exception {

    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> filesWithModify = new HashMap<>();
    filesWithModify.put(originalFilename, "Unrelated content");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename, originalContent);

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, "Unrelated content");
    expectedFiles.put(originalFilename, null);

    testRename_merged(strategy, originalFiles,
        isRenameInOurs ? filesWithRename : filesWithModify,
        isRenameInOurs ? filesWithModify : filesWithRename, expectedFiles);
  }


  /**
   *  B: test/file1 X
   *  O: test/file2 Z_1 ;
   *  T: test/file3 Z_2;
   *  Expected:
   *  Cross-side renames are no supported. Both files are present in the merge commit.
   *  M: test/file2 Z_1; test/file3 Z_2;
   */
  @Theory
  public void checkCrossSidesRename_notDetected_merged(MergeStrategy strategy) throws Exception {
    // renamed on both sides the same name. Merges cleanly with the new content.
    // This should happen regardless of whether the content was modified
    String originalFilename = "test/file1";
    String originalContent = "Unrelated base file";
    String renameFileContent = "Identical merge-side-file";
    String oursFilename = "test/file2";
    String theirsFilename = "test/file3";
    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put(oursFilename, renameFileContent);
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put(theirsFilename, renameFileContent);

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(oursFilename, renameFileContent);
    expectedFiles.put(theirsFilename, renameFileContent);
    expectedFiles.put(originalFilename, null);

    testRename_merged(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles);
  }


  /**
   *  One side renamed, another added a new file with the same name
   *  B: test/file1 X_1
   *  O: test/file2 Y
   *  T: test/file2 X_1;
   *  Expected:
   *  Rename/add conflict test/file2 Y vs test/file2 X_1 or rename/delete conflict.
   *  test/file2 with conflict X is completely missing from O.
   *  Without rename detection, that would be content merge conflict.
   *
   */
  @Theory
  public void checkRenameToAdded_missingOnModifySide_conflict(MergeStrategy strategy,  boolean isRenameInOurs, boolean shouldModifyRename) throws Exception {

    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> filesWithModify = new HashMap<>();
    filesWithModify.put(renameFilename, "Unrelated file");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename,
        shouldModifyRename ? slightlyModifiedContent : originalContent);
    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename);
    expectedConflicts.add(originalFilename);
    Set<String> expectedIndex = new HashSet<>();
    int renameStage = isRenameInOurs ? DirCacheEntry.STAGE_2 : DirCacheEntry.STAGE_3;
    int modifyStage = isRenameInOurs ? DirCacheEntry.STAGE_3 : DirCacheEntry.STAGE_2;
    expectedIndex.add(
        String.format("%s, mode:100644, stage:1, content:%s", originalFilename, originalContent));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename, modifyStage,
            "Unrelated file"));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename, renameStage,
            shouldModifyRename ? slightlyModifiedContent : originalContent));

    Map<String, String> expectedFiles = new HashMap<>();

    expectedFiles.put(renameFilename, "Unrelated file");

    testRename_withConflict(strategy, originalFiles,
        isRenameInOurs ? filesWithRename : filesWithModify,
        isRenameInOurs ? filesWithModify : filesWithRename, expectedFiles, expectedConflicts,
        expectedIndex);
  }

  /**
   *  B: test/file1 X_1
   *  O: test/file1 X_2 ; test/file2 Y
   *  T: test/file2 X_1;
   *  Expected:
   *  Rename/add conflict test/file2 Y vs test/file2 X_1
   *  test/file1 X_2 ; test/file2 with Y_X1 merge conflict? What do we keep in the merge commit?
   */
  @Theory
  public void checkRenameToAdded_modifyConflict(MergeStrategy strategy,  boolean isRenameInOurs, boolean shouldModifyRename, boolean shouldModifyOriginal) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> filesWithModify = new HashMap<>();
    filesWithModify.put(renameFilename, "Unrelated file");
    filesWithModify.put(originalFilename,
        shouldModifyOriginal ? slightlyModifiedContent : originalContent);
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename,
        shouldModifyRename ? slightlyModifiedContent : originalContent);
    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename);
    expectedConflicts.add(originalFilename);
    Set<String> expectedIndex = new HashSet<>();
    int renameStage = isRenameInOurs ? DirCacheEntry.STAGE_2 : DirCacheEntry.STAGE_3;
    int modifyStage = isRenameInOurs ? DirCacheEntry.STAGE_3 : DirCacheEntry.STAGE_2;
    expectedIndex.add(
        String.format("%s, mode:100644, stage:1, content:%s", originalFilename, originalContent));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", originalFilename, modifyStage,
            shouldModifyOriginal ? slightlyModifiedContent : originalContent));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename, modifyStage,
            "Unrelated file"));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename, renameStage,
            shouldModifyRename ? slightlyModifiedContent : originalContent));

    Map<String, String> expectedFiles = new HashMap<>();
    // here, it looks like we always keep ours, but what do we do?
    expectedFiles.put(originalFilename,
        shouldModifyOriginal || shouldModifyRename ? slightlyModifiedContent : originalContent);
    expectedFiles.put(renameFilename, "Unrelated file");

    testRename_withConflict(strategy, originalFiles,
        isRenameInOurs ? filesWithRename : filesWithModify,
        isRenameInOurs ? filesWithModify : filesWithRename, expectedFiles, expectedConflicts,
        expectedIndex);
  }


  /**
   * Handling of files with original filename and unrelated content:
   *  B: test/file1 X_1
   *  O: test/file1 X_2 ;
   *  T: test/file2 X_1; test/file1 Y
   *  Expected:
   *  test/file2 X_2 ; test/file1 Y
   *
   * // File was rename on both sides
   *  B: test/file1 X_1
   *  O: test/file2 X_2 ;
   *  T: test/file2 X_1; test/file1 Y
   *  Expected:
   *  test/file2 X_2 ; test/file1 Y
   *
   * // File is missing on one side
   *  B: test/file1 X_1
   *  O:
   *  T: test/file2 X_1; test/file1 Y
   *  Expected:
   *  delete vs rename conflict test/file2 X_2 ; test/file1 Y
   *
   * // File is present with unrelated content
   *  B: test/file1 X_1
   *  O: test/file1 Z ;
   *  T: test/file2 X_2; test/file1 Z
   *  Expected:
   *  file is present, but with different (non-rename) contents
   *  This is delete/rename-modify conflict, can be represented otherwise?
   *  Attempt content merge of O: test/file Z vs test/file2 X_2
   *
   * // The rename file name is present in base
   *  B: test/file1 X_1; test/file2 Z
   *  O: test/file1 X_2 ;
   *  T: test/file2 X_1; test/file1 Y
   *  Expected:
   *  // No conflict, since the file does not exist on either sides
   *  test/file2 X_2 ; test/file1 Y
   *
   * // The rename file name is present in base, missing on side
   *  B: test/file1 X_1; test/file2 Z_1
   *  O: test/file1 X_2 ;
   *  T: test/file2 X_1; test/file1 Z_2
   *  Expected:
   *  // This is again a delete/rename conflict on test/file2 Z_1 vs null vs test/file1 Z_2
   *  test/file2 X_2 ; test/file1 conflicting
   *
   *
   * // The rename file name is present in base and on side (swap)
   *  B: test/file1 X_1; test/file2 Z_1
   *  O: test/file1 X_2 ; test/file2 Z_1
   *  T: test/file2 X_1; test/file1 Z_2
   *  Expected:
   *  // This is a valid filenames swap
   *  test/file2 X_2 ; test/file1 Z_2
   *
   * // The rename file is present on side
   *  B: test/file1 X_1
   *  O: test/file1 X_2 ; test/file2 Y
   *  T: test/file2 X_1; test/file1 Y
   *  Expected:
   *  add/rename conflict
   *
   * // The rename file is present on side and in base = same -> swap
   *  B: test/file1 X_1; test/file2 Y
   *  O: test/file1 X_2 ; test/file2 Y_1
   *  T: test/file2 X_1; test/file1 Y
   *  Expected:
   *  add/rename conflict
   *
   */

  /**
   *
   *  B: test/file1 X_1
   *  O: test/file1 X_2 ;
   *  T: test/file2 X_1; test/file1 Y
   *  Expected:
   *  test/file2 X_2 ; test/file1 Y
   * # We should be able to merge O & T cleanly
   */
  @Theory
  public void checkOriginalAddedOnRenameSide_merged(MergeStrategy strategy, boolean isRenameInOurs, boolean modifyInRename, boolean modifyInOther) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent1 = "z\na\nb\nc";

    String slightlyModifiedContent2 = "a\nb\nc\nd";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> filesWithModify = new HashMap<>();
    filesWithModify.put(originalFilename,
        modifyInRename ? slightlyModifiedContent1 : originalContent);
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename, modifyInOther? slightlyModifiedContent2: originalContent);
    filesWithRename.put(originalFilename, "Unrelated file");

    String expectedRenameContent = modifyInRename && modifyInOther ? "z\na\nb\nc\nd"
        : modifyInRename ? slightlyModifiedContent1
            : modifyInOther ? slightlyModifiedContent2 : originalContent;
    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, expectedRenameContent);
    expectedFiles.put(originalFilename, "Unrelated file");
    testRename_merged(strategy, originalFiles, isRenameInOurs ? filesWithRename : filesWithModify,
        isRenameInOurs ? filesWithModify : filesWithRename, expectedFiles);
  }

  /**
   *
   *  B: test/file1 X_1
   *  O: test/file1 Y ;
   *  T: test/file2 X_1; test/file1 Y
   *  Expected:
   *  test/file2 X_2 ; test/file1 Y
   *  This is delete vs rename conflict on rename file, however, unclear how to detect and report it.
   *  With the current implementation, we always assume the non-rename side modified the file, while it is possible it completely replaced it.
   *  Detecting it would require either:
   *  1) content test/file1 OvsT content comparision, since they do not have a common matching base.
   *  2) similarity {@code org.eclipse.jgit.diff.DiffEntry#score} calculations for {@code ChangeType#MODIFY}
   *
   * Alternatively, we could always content-merge on rename, so such cases always fail with content-merge conflicts.
   */
  @Theory
  public void checkOriginalAddedOnBothSides_withModification_conflict(MergeStrategy strategy, boolean isRenameInOurs) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> filesWithModify = new HashMap<>();
    filesWithModify.put(originalFilename, "Unrelated file");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename, slightlyModifiedContent);
    filesWithRename.put(originalFilename, "Unrelated file");

    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename);

    Set<String> expectedIndex = new HashSet<>();
    int renameStage = isRenameInOurs ? DirCacheEntry.STAGE_2 : DirCacheEntry.STAGE_3;
    int modifyStage = isRenameInOurs ? DirCacheEntry.STAGE_3 : DirCacheEntry.STAGE_2;
    expectedIndex.add(
        String.format("%s, mode:100644, content:%s", originalFilename, "Unrelated file"));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:1, content:%s", renameFilename, originalContent));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename, renameStage,
            slightlyModifiedContent));
    expectedIndex.add(
        String.format("%s, mode:100644, stage:%s, content:%s", renameFilename, modifyStage,
            "Unrelated file"));

    String oursContent = isRenameInOurs ? filesWithRename.get(renameFilename) : "Unrelated file";
    String theirsContent =
        isRenameInOurs ? "Unrelated file" : filesWithRename.get(renameFilename);
    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename,
        String.format("<<<<<<< HEAD\n%s\n=======\n%s", oursContent, theirsContent));
    expectedFiles.put(originalFilename, "Unrelated file");
    testRename_withConflict(strategy, originalFiles,
        isRenameInOurs ? filesWithRename : filesWithModify,
        isRenameInOurs ? filesWithModify : filesWithRename, expectedFiles, expectedConflicts,
        expectedIndex);
  }

  /**
   *
   *  B: test/file1 X_1
   *  O: test/file1 Y ;
   *  T: test/file2 X_1; test/file1 Y
   *  Expected:
   *  test/file2 X_1 ; test/file1 Y
   *
   *  The results of this merge are meaningless. The unrelated file was chosen as a rename file, since the rename file was not modified comparing to base.
   *  In fact, this is a delete vs rename conflict on rename file.
   *
   *  With the current implementation, we always assume the non-rename side modified the file, while it is possible it completely replaced it.
   *  Detecting it would require either:
   *  1) content test/file1 OvsT content comparision, since they do not have a common matching base.
   *  2) similarity {@code org.eclipse.jgit.diff.DiffEntry#score} calculations for {@code ChangeType#MODIFY} (introduce replace)
   *
   * Without the rename detection, this paths would be content merged as test/file2 X_1 ; test/file1 Y
   */
  @Theory
  public void checkOriginalAddedOnBothSides_noModification_merged(MergeStrategy strategy, boolean isRenameInOurs) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> filesWithModify = new HashMap<>();
    filesWithModify.put(originalFilename, "Unrelated file");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename, originalContent);
    filesWithRename.put(originalFilename, "Unrelated file");

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, "Unrelated file");
    expectedFiles.put(originalFilename, "Unrelated file");
    testRename_merged(strategy, originalFiles,
        isRenameInOurs ? filesWithRename : filesWithModify,
        isRenameInOurs ? filesWithModify : filesWithRename, expectedFiles);
  }

  /**
   *
   *  B: test/file1 X_1 ;
   *  O: test/file2 X_2 ;
   *  T: test/file2 X_1; test/file1 Y
   *  Expected:
   *  test/file2 X_2 ; test/file1 Y
   * # We should be able to merge O & T cleanly
   */
  @Theory
  public void checkOriginalAdded_renameOnBothSides_merged(MergeStrategy strategy, boolean isAddInOurs, boolean shouldModify) throws Exception {
    // Both sides renamed, one side added a new file with the original name
    // Without rename detection, the original would be content merged, the rename file would be added
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> filesWithAdd = new HashMap<>();
    filesWithAdd.put(renameFilename, shouldModify ? slightlyModifiedContent : originalContent);
    filesWithAdd.put(originalFilename, "Unrelated file");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename, shouldModify ? slightlyModifiedContent : originalContent);

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, shouldModify ? slightlyModifiedContent : originalContent);
    expectedFiles.put(originalFilename, "Unrelated file");
    testRename_merged(strategy, originalFiles, isAddInOurs ? filesWithAdd : filesWithRename,
        isAddInOurs ? filesWithRename : filesWithAdd, expectedFiles);
  }

  /**
   *  There are 2 renames with swapped contents.
   *  B: test/file1 X_1 ; test/file2 Y
   *  O: test/file2 X_2 ; test/file1 Y_1
   *  T: test/file2 X_1; test/file1 Y_1
   *  Expected:
   *  test/file2 X_2 ; test/file1 Y
   * # We should be able to merge O & T cleanly
   */
  @Theory
  public void checkOriginalAdded_swapRename_merged(MergeStrategy strategy, boolean shouldModifyRename1, boolean shouldModifyRename2) throws Exception {
    String renameFilename1 = "test/file1";
    String originalContent1 = "a\nb\nc";
    String slightlyModifiedContent1 = "a\nb\nb";
    String renameFilename2 = "test/file2";
    String originalContent2 = "x\ny\nz";
    String slightlyModifiedContent2 = "x\ny\ny";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(renameFilename1, originalContent1);
    originalFiles.put(renameFilename2, originalContent2);
    Map<String, String> oursFilesWithRename = new HashMap<>();
    oursFilesWithRename.put(renameFilename2,
        shouldModifyRename1 ? slightlyModifiedContent1 : originalContent1);
    oursFilesWithRename.put(renameFilename1,
        shouldModifyRename2 ? slightlyModifiedContent2 : originalContent2);
    Map<String, String> theirsFilesWithRename = new HashMap<>();
    theirsFilesWithRename.put(renameFilename2,
        shouldModifyRename1 ? slightlyModifiedContent1 : originalContent1);
    theirsFilesWithRename.put(renameFilename1,
        shouldModifyRename2 ? slightlyModifiedContent2 : originalContent2);

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename2,
        shouldModifyRename1 ? slightlyModifiedContent1 : originalContent1);
    expectedFiles.put(renameFilename1,
        shouldModifyRename2 ? slightlyModifiedContent2 : originalContent2);
    testRename_merged(strategy, originalFiles, oursFilesWithRename, theirsFilesWithRename,
        expectedFiles);
  }

  /**
   *
   *  B: test/file1 X_1 ; test/file2 Y
   *  O: test/file2 X_2 ;
   *  T: test/file2 X_1; test/file1 Y
   *  Expected:
   *  There should be a delete/modify conflict on test/file1 Y. However, since a 2-way merge involving test/file2 X_2 is attempted,
   *  there is no conflict and test/file2 X_2 is chosen, since Y is not modified.
   *
   */
  @Theory
  public void checkOriginalAdded_renameOnBothSides_withRenameInBase_merged(MergeStrategy strategy, boolean isAddInOurs, boolean shouldModify) throws Exception {
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    originalFiles.put(renameFilename, "Unrelated file");
    Map<String, String> filesWithAdd = new HashMap<>();
    filesWithAdd.put(renameFilename, shouldModify ? slightlyModifiedContent : originalContent);
    filesWithAdd.put(originalFilename, "Unrelated file");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename, shouldModify ? slightlyModifiedContent : originalContent);

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, shouldModify ? slightlyModifiedContent : originalContent);
    // This is a result of 2-way merge  test/file2 Y vs test/file2 X_2 vs test/file1 Y
    expectedFiles.put(originalFilename, shouldModify ? slightlyModifiedContent : originalContent);
    testRename_merged(strategy, originalFiles, isAddInOurs ? filesWithAdd : filesWithRename,
        isAddInOurs ? filesWithRename : filesWithAdd, expectedFiles);
  }

  /**
   *
   *  B: test/file1 X_1 ; test/file2 Z;
   *  O: test/file1 X_2 ;
   *  T: test/file2 X_1; test/file1 Y
   *  Expected:
   *  test/file2 X_2 ; test/file1 Y
   * # We should be able to merge O & T cleanly.
   * It does not matter that test/file2 Z is present in base. It is missing in both commits, that are being merged.
   */
  @Theory
  public void checkOriginalAddedOnRenameSide_existedInBase_merged(MergeStrategy strategy, boolean isRenameInOurs) throws Exception {
    // One side renamed, another added a new file with the original name
    // Without rename detection, the original would be content merged, the rename file would be added
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    originalFiles.put(renameFilename, "Unrelated base content");
    Map<String, String> filesWithModify = new HashMap<>();
    filesWithModify.put(originalFilename, slightlyModifiedContent);
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename, originalContent);
    filesWithRename.put(originalFilename, "Unrelated file");

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, slightlyModifiedContent);
    expectedFiles.put(originalFilename, "Unrelated file");
    testRename_merged(strategy, originalFiles, isRenameInOurs ? filesWithRename : filesWithModify,
        isRenameInOurs ? filesWithModify : filesWithRename, expectedFiles);
  }

  /**
   *
   *  B: test/file1 X_1 ; test/file2 Z;
   *  O: test/file1 X_2 ; test/file2 Y;
   *  T: test/file2 X_1; test/file1 Y
   *  Expected:
   *  test/file2 X_2 ; test/file1 Y
   *  Mixed stages not allowed
   */
  @Theory
  public void checkFilesNamesSwapedOnSides_conflict(MergeStrategy strategy, boolean isRenameInOurs) throws Exception {
    // One side renamed, another added a new file with the original name
    // Without rename detection, the original would be content merged, the rename file would be added
    isRenameInOurs = true;
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    originalFiles.put(renameFilename, "Unrelated base content");
    Map<String, String> filesWithModify = new HashMap<>();
    filesWithModify.put(originalFilename, slightlyModifiedContent);
    filesWithModify.put(renameFilename, "Unrelated file");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renameFilename, originalContent);
    filesWithRename.put(originalFilename, "Unrelated file");

    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename);
    expectedConflicts.add(originalFilename);
    Set<String> expectedIndex = new HashSet<>();
    int renameStage = isRenameInOurs? DirCacheEntry.STAGE_2 :DirCacheEntry.STAGE_3;
    int modifyStage =isRenameInOurs? DirCacheEntry.STAGE_3 :DirCacheEntry.STAGE_2;
    expectedIndex.add(String.format("%s, mode:100644, stage:1, content:%s", originalFilename, originalContent));
    expectedIndex.add(String.format("%s, mode:100644, stage:1, content:%s", renameFilename, "Unrelated base content"));
    expectedIndex.add(String.format("%s, mode:100644, stage:%s, content:%s", originalFilename, modifyStage, slightlyModifiedContent));
    expectedIndex.add(String.format("%s, mode:100644, stage:%s, content:%s", originalFilename, renameStage, "Unrelated file"));
    expectedIndex.add(String.format("%s, mode:100644, stage:%s, content:%s", renameFilename, modifyStage, "Unrelated file"));
    expectedIndex.add(String.format("%s, mode:100644, stage:%s, content:%s", renameFilename, renameStage, originalContent));

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(originalFilename, "Unrelated file");
    expectedFiles.put(renameFilename, originalContent);
    testRename_withConflict(strategy, originalFiles, isRenameInOurs ? filesWithRename : filesWithModify,
        isRenameInOurs ? filesWithModify : filesWithRename, expectedFiles, expectedConflicts, expectedIndex);
  }

  /**
   *
   *  B: test/file1 A_1, test/file2 X_1
   *  O: test/file3 A_1, test/file2 X_1(2)
   *  T: test/file3 X_1; test/file1 A_1(2)
   *  Expected:
   *  The rename detection is off for this path. This will always result in
   *  test/file3 A_1 vs test/file3 X_1 conflict ad additional delete/modify conflicts if non-renamed files were modified.
   */
  @Theory
  public void checkRenameToSamePathFromDifferentOriginal_conflict(MergeStrategy strategy, boolean keepOriginalContent) throws Exception {
    String originalFilename1 = "test/file1";
    String originalFilename2 = "test/file2";
    String originalContent1 = "a\nb\nc";
    String originalContent2 = "x\ny\nz";
    String slightlyModifiedContent1 = "a\nb\nb";
    String slightlyModifiedContent2 = "x\ny\ny";
    String renameFilename = "test/file3";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename1, originalContent1);
    originalFiles.put(originalFilename2, originalContent2);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put(renameFilename,
        keepOriginalContent ? originalContent1 : slightlyModifiedContent1);
    oursFiles.put(originalFilename2,
        keepOriginalContent ? originalContent2 : slightlyModifiedContent2);
    Map<String, String> theirsFiles = new HashMap<>();
    // does this meter which content?
    theirsFiles.put(renameFilename,
        keepOriginalContent ? originalContent2 : slightlyModifiedContent2);
    theirsFiles.put(originalFilename1,
        keepOriginalContent ? originalContent1 : slightlyModifiedContent1);
    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename);
    if(!keepOriginalContent) {
      // we also get a modify/deite conflict in this case.
      expectedConflicts.add(originalFilename1);
      expectedConflicts.add(originalFilename2);
    }
    Set<String> expectedIndex = new HashSet<>();
    expectedIndex.add(String.format("%s, mode:100644, stage:2, content:%s", renameFilename,
        oursFiles.get(renameFilename)));
    expectedIndex.add(String.format("%s, mode:100644, stage:3, content:%s", renameFilename,
        theirsFiles.get(renameFilename)));
    if(!keepOriginalContent) {
      expectedIndex.add(
          String.format("%s, mode:100644, stage:1, content:%s", originalFilename1,
              originalContent1));
      expectedIndex.add(
          String.format("%s, mode:100644, stage:1, content:%s", originalFilename2,
              originalContent2));
      expectedIndex.add(String.format("%s, mode:100644, stage:2, content:%s", originalFilename2,
          oursFiles.get(originalFilename2)));
      expectedIndex.add(String.format("%s, mode:100644, stage:3, content:%s", originalFilename1,
          theirsFiles.get(originalFilename1)));
    }

    Map<String, String> expectedFiles = new HashMap<>();
    if(!keepOriginalContent) {
      expectedFiles.put(originalFilename1, slightlyModifiedContent1);
      expectedFiles.put(originalFilename2, slightlyModifiedContent2);
    } else {
      expectedFiles.put(originalFilename1, null);
      expectedFiles.put(originalFilename2, null);
    }
    expectedFiles.put(renameFilename,
        String.format("<<<<<<< HEAD\n%s\n=======\n%s\n>>>>>>>", oursFiles.get(renameFilename),
            theirsFiles.get(renameFilename)));

    testRename_withConflict(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles,
        expectedConflicts, expectedIndex);
  }

  /**
   *
   *  B: test/file1 A_1, test/file2 X_1
   *  O: test/file3 A_1, test/file2 X_1(2)
   *  T: test/file3 X_1; test/file1 A_1(2)
   *  Expected:
   *  The rename detection is off for test/file3 This will always result in
   *  test/file3 A_1 vs test/file3 X_1 conflict.
   *
   * Additionaly, we have a modify/delete conflict on test/file1, test/file2
   */
  @Theory
  public void checkRenameToSamePathFromDifferentOriginal_withOriginalPresentInBase_conflict(MergeStrategy strategy, boolean keepOriginalContent) throws Exception {
    String originalFilename1 = "test/file1";
    String originalFilename2 = "test/file2";
    String originalContent1 = "a\nb\nc";
    String originalContent2 = "x\ny\nz";
    String slightlyModifiedContent1 = "a\nb\nb";
    String slightlyModifiedContent2 = "x\ny\ny";
    String renameFilename = "test/file3";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename1, originalContent1);
    originalFiles.put(originalFilename2, originalContent2);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put(renameFilename,
        keepOriginalContent ? originalContent1 : slightlyModifiedContent1);
    oursFiles.put(originalFilename2,
        keepOriginalContent ? originalContent2 : slightlyModifiedContent2);
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put(renameFilename,
        keepOriginalContent ? originalContent2 : slightlyModifiedContent2);
    theirsFiles.put(originalFilename1,
        keepOriginalContent ? originalContent1 : slightlyModifiedContent1);

    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename);
    if(!keepOriginalContent){
      expectedConflicts.add(originalFilename1);
      expectedConflicts.add(originalFilename2);
    }

    Set<String> expectedIndex = new HashSet<>();
    expectedIndex.add(String.format("%s, mode:100644, stage:2, content:%s", renameFilename,
        oursFiles.get(renameFilename)));
    expectedIndex.add(String.format("%s, mode:100644, stage:3, content:%s", renameFilename,
        theirsFiles.get(renameFilename)));
    if(!keepOriginalContent) {
      expectedIndex.add(
          String.format("%s, mode:100644, stage:1, content:%s", originalFilename1,
              originalContent1));
      expectedIndex.add(
          String.format("%s, mode:100644, stage:1, content:%s", originalFilename2,
              originalContent2));
      expectedIndex.add(String.format("%s, mode:100644, stage:2, content:%s", originalFilename2,
          oursFiles.get(originalFilename2)));
      expectedIndex.add(String.format("%s, mode:100644, stage:3, content:%s", originalFilename1,
          theirsFiles.get(originalFilename1)));
    }

    Map<String, String> expectedFiles = new HashMap<>();
    if(!keepOriginalContent) {
      expectedFiles.put(originalFilename1, slightlyModifiedContent1);
      expectedFiles.put(originalFilename2, slightlyModifiedContent2);
    } else {
      expectedFiles.put(originalFilename1, null);
      expectedFiles.put(originalFilename2, null);
    }
    expectedFiles.put(renameFilename,
        String.format("<<<<<<< HEAD\n%s\n=======\n%s\n>>>>>>>", oursFiles.get(renameFilename),
            theirsFiles.get(renameFilename)));

    testRename_withConflict(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles,
        expectedConflicts, expectedIndex);
  }

  @Theory
  public void checkRenameDir_AllFilesMoved_modifyConflict(MergeStrategy strategy) throws Exception {
    String originalContent1 = "a\nb\nc";
    String originalContent2 = "x\ny\nz";
    String slightlyModifiedContent1 = "a\nb\nb";
    String slightlyModifiedContent2 = "x\nz\nz";
    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put("test/a/file1", originalContent1);
    originalFiles.put("test/a/file2", originalContent2);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put("test/a/file1", slightlyModifiedContent1);
    oursFiles.put("test/a/file2", slightlyModifiedContent2);
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put("test/sub/file1", originalContent1);
    theirsFiles.put("test/sub/file2", originalContent2);
    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put("test/sub/file1", slightlyModifiedContent1);
    expectedFiles.put("test/sub/file2", slightlyModifiedContent2);
    testRename_merged(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles);

  }

  @Theory
  public void checkRenameSubDir_AllFilesMoved_SomeFilesAddedOnRenameSide_modifyConflict(MergeStrategy strategy, @FromDataPoints("renameLists") List<Entry<String, String>> renamePairs, boolean isRenameInOurs) throws Exception {
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    Map<String, String> originalFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      originalFiles.put(renamePair.getKey(), originalContent + renamePair.getKey());
    }
    Map<String, String> noRenameFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      noRenameFiles.put(renamePair.getKey(), slightlyModifiedContent + renamePair.getKey());
    }
    Map<String, String> renameFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      renameFiles.put(renamePair.getValue(), originalContent + renamePair.getKey());
    }
    String renameDir = getDir(renamePairs.get(0).getValue());
    for (String fileAddition : fileAdditionsData) {
      renameFiles.put(String.format("%s/%s",renameDir, fileAddition),
          String.format("Another %s file was added in thiers", fileAddition));
    }
    Map<String, String> expectedFiles = new HashMap<>();
    // This should all be under rename dir?
    for (Entry<String, String> renamePair : renamePairs) {
      expectedFiles.put(renamePair.getValue(), slightlyModifiedContent + renamePair.getKey());
    }
    for (String fileAddition : fileAdditionsData) {
      expectedFiles.put(String.format("%s/%s",renameDir, fileAddition),
          String.format("Another %s file was added in thiers", fileAddition));
    }
    testRename_merged(strategy, originalFiles, isRenameInOurs ? renameFiles : noRenameFiles,
        isRenameInOurs ? noRenameFiles : renameFiles, expectedFiles);
  }

  @Theory
  public void checkRenameSubDir_AllFilesMoved_SomeFilesAddedOnModifySide_modifyConflict(MergeStrategy strategy, @FromDataPoints("renameLists") List<Entry<String, String>> renamePairs, boolean isRenameInOurs) throws Exception {
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    Map<String, String> originalFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      originalFiles.put(renamePair.getKey(), originalContent + renamePair.getKey());
    }
    Map<String, String> noRenameFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      noRenameFiles.put(renamePair.getKey(), slightlyModifiedContent + renamePair.getKey());
    }
    String originalDir = getDir(renamePairs.get(0).getKey());
    for (String fileAddition : fileAdditionsData) {
      noRenameFiles.put(String.format("%s/%s", originalDir, fileAddition),
          String.format("Another %s file was added in thiers", fileAddition));
    }
    Map<String, String> renameFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      renameFiles.put(renamePair.getValue(), originalContent + renamePair.getKey());
    }
    Map<String, String> expectedFiles = new HashMap<>();
    // This should all be under rename dir?
    for (Entry<String, String> renamePair : renamePairs) {
      expectedFiles.put(renamePair.getValue(), slightlyModifiedContent + renamePair.getKey());
    }
    for (String fileAddition : fileAdditionsData) {
      expectedFiles.put(String.format("%s/%s",originalDir, fileAddition),
          String.format("Another %s file was added in thiers", fileAddition));
    }
    testRename_merged(strategy, originalFiles, isRenameInOurs ? renameFiles : noRenameFiles,
        isRenameInOurs ? noRenameFiles : renameFiles, expectedFiles);
  }


  @Theory
  public void checkRenameSubDir_AllFilesMoved_SomeFilesAddedOnBothSides_modifyConflict(MergeStrategy strategy, @FromDataPoints("renameLists") List<Entry<String, String>> renamePairs, boolean isRenameInOurs) throws Exception {
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    Map<String, String> originalFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      originalFiles.put(renamePair.getKey(), originalContent + renamePair.getKey());
    }
    Map<String, String> noRenameFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      noRenameFiles.put(renamePair.getKey(), slightlyModifiedContent + renamePair.getKey());
    }
    String originalDir = getDir(renamePairs.get(0).getKey());
    for (String fileAddition : fileAdditionsData) {
      noRenameFiles.put(String.format("%s/%s", originalDir, fileAddition),
          String.format("Another %s file was added in thiers", fileAddition));
    }
    Map<String, String> renameFiles = new HashMap<>();
    for (Entry<String, String> renamePair : renamePairs) {
      renameFiles.put(renamePair.getValue(), originalContent + renamePair.getKey());
    }
    String renameDir = getDir(renamePairs.get(0).getValue());
    for (String fileAddition : fileAdditionsData) {
      renameFiles.put(String.format("%s/%s",renameDir, fileAddition),
          String.format("Another %s file was added in thiers", fileAddition));
    }
    Map<String, String> expectedFiles = new HashMap<>();
    // This should all be under rename dir?
    for (Entry<String, String> renamePair : renamePairs) {
      expectedFiles.put(renamePair.getValue(), slightlyModifiedContent + renamePair.getKey());
    }
    for (String fileAddition : fileAdditionsData) {
      expectedFiles.put(String.format("%s/%s",originalDir, fileAddition),
          String.format("Another %s file was added in thiers", fileAddition));
    }
    for (String fileAddition : fileAdditionsData) {
      expectedFiles.put(String.format("%s/%s",renameDir, fileAddition),
          String.format("Another %s file was added in thiers", fileAddition));
    }
    testRename_merged(strategy, originalFiles, isRenameInOurs ? renameFiles : noRenameFiles,
        isRenameInOurs ? noRenameFiles : renameFiles, expectedFiles);
  }

  @Theory
  public void checkRenameSubDir_AllFilesMoved_SomeFilesAddedInOurs_modifyConflict(MergeStrategy strategy) throws Exception {
    String originalContent1 = "a\nb\nc";
    String originalContent2 = "x\ny\nz";
    String slightlyModifiedContent1 = "a\nb\nb";
    String slightlyModifiedContent2 = "x\nz\nz";
    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put("test/a/file1", originalContent1);
    originalFiles.put("test/a/file2", originalContent2);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put("test/a/file1", slightlyModifiedContent1);
    oursFiles.put("test/a/file2", slightlyModifiedContent2);
    oursFiles.put("test/a/file3", "Added file");
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put("test/sub/file1", originalContent1);
    theirsFiles.put("test/sub/file2", originalContent2);
    Map<String, String> expectedFiles = new HashMap<>();
    // This should all be under test/sub/
    expectedFiles.put("test/sub/file1", slightlyModifiedContent1);
    expectedFiles.put("test/sub/file2", slightlyModifiedContent2);
    expectedFiles.put("test/a/file3", "Added file");
    testRename_merged(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles);
  }

  @Theory
  public void checkRenameSubDir_AllFilesMoved_SomeFilesAddedInBoth_modifyConflict(MergeStrategy strategy) throws Exception {
    String originalContent1 = "a\nb\nc";
    String originalContent2 = "x\ny\nz";
    String slightlyModifiedContent1 = "a\nb\nb";
    String slightlyModifiedContent2 = "x\nz\nz";
    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put("test/a/file1", originalContent1);
    originalFiles.put("test/a/file2", originalContent2);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put("test/a/file1", slightlyModifiedContent1);
    oursFiles.put("test/a/file2", slightlyModifiedContent2);
    oursFiles.put("test/a/file1.1","One file was added in ours");
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put("test/sub/file1", originalContent1);
    theirsFiles.put("test/sub/file2", originalContent2);
    theirsFiles.put("test/sub/file3", "Another file was added in thiers");
    Map<String, String> expectedFiles = new HashMap<>();
    // This should all be under test/sub/
    expectedFiles.put("test/sub/file1", slightlyModifiedContent1);
    expectedFiles.put("test/sub/file2", slightlyModifiedContent2);
    expectedFiles.put("test/a/file1.1", "One file was added in ours");
    expectedFiles.put("test/sub/file3", "Another file was added in thiers");
    testRename_merged(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles);
  }

  @Theory
  public void checkRename_dirSplit_modifyConflict(MergeStrategy strategy, boolean isRenameInOurs) throws Exception {
    String originalContent1 = "a\nb\nc";
    String originalContent2 = "x\ny\nz";
    String slightlyModifiedContent1 = "a\nb\nb";
    String slightlyModifiedContent2 = "x\nz\nz";
    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put("test/a/file1", originalContent1);
    originalFiles.put("test/a/file2", originalContent2);
    Map<String, String> noRenameFiles = new HashMap<>();
    noRenameFiles.put("test/a/file1", slightlyModifiedContent1);
    noRenameFiles.put("test/a/file2", slightlyModifiedContent2);
    Map<String, String> renameFiles = new HashMap<>();
    renameFiles.put("test/a1/file1", originalContent1);
    renameFiles.put("test/a2/file2", originalContent2);
    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put("test/a1/file1", slightlyModifiedContent1);
    expectedFiles.put("test/a2/file2", slightlyModifiedContent2);
    testRename_merged(strategy, originalFiles, isRenameInOurs ? renameFiles : noRenameFiles,
        isRenameInOurs ? noRenameFiles : renameFiles, expectedFiles);
  }

  @Theory
  public void checkRename_subDirSplit_modifyConflict(MergeStrategy strategy) throws Exception {
    String originalContent1 = "a\nb\nc";
    String originalContent2 = "x\ny\nz";
    String slightlyModifiedContent1 = "a\nb\nb";
    String slightlyModifiedContent2 = "x\nz\nz";
    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put("test/file1", originalContent1);
    originalFiles.put("test/file2", originalContent2);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put("test/file1", slightlyModifiedContent1);
    oursFiles.put("test/file2", slightlyModifiedContent2);
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put("test/a1/file1", originalContent1);
    theirsFiles.put("test/a2/file2", originalContent2);
    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put("test/a1/file1", slightlyModifiedContent1);
    expectedFiles.put("test/a2/file2", slightlyModifiedContent2);
    testRename_merged(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles);

  }

  private String getDir(String path){
    int endDir = path.lastIndexOf("/");
    return path.substring(0, endDir);
  }

}

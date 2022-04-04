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
      if(expectedFile.getValue()!=null) {
        assertEquals(expectedFile.getValue(), read(expectedFile.getKey()));
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

  public void checkRenameTo_modifyConflict(MergeStrategy strategy, List<Entry<String, String>> renamePairs, boolean isRenameInOurs) throws Exception {
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
    checkRenameTo_modifyConflict(strategy, renamePairs, true);

  }

  @Theory
  public void checkRenameFile_modifyConflict_renameInTheirs(MergeStrategy strategy, @FromDataPoints("renameLists") List<Entry<String, String>> renamePairs) throws Exception {
    checkRenameTo_modifyConflict(strategy, renamePairs, false);
  }

  @Theory
  public void checkRenameSplitDir_modifyConflict_renameInOurs(MergeStrategy strategy, @FromDataPoints("renameListsSplit") List<Entry<String, String>> renamePairs) throws Exception {
    checkRenameTo_modifyConflict(strategy, renamePairs, true);

  }

  @Theory
  public void checkRenameSplitDir_modifyConflict_renameInTheirs(MergeStrategy strategy, @FromDataPoints("renameListsSplit") List<Entry<String, String>> renamePairs) throws Exception {
    checkRenameTo_modifyConflict(strategy, renamePairs, false);
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
  public void checkRenameToSubDir_modifyConflict(MergeStrategy strategy) throws Exception {
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
  public void checkRenameMoveToParent_modifyConflict(MergeStrategy strategy) throws Exception {
    // classic conflict, renamed in thiers, modified in ours
    String originalFilename = "test/sub/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String oursFilename = originalFilename;
    String oursContent = slightlyModifiedContent;
    String theirsFilename = "test/file2";
    String theirsContent = originalContent;

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put("test/file2", slightlyModifiedContent);
    Map<String, String > originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String > oursFiles = new HashMap<>();
    oursFiles.put(oursFilename, oursContent);
    Map<String, String > theirsFiles = new HashMap<>();
    theirsFiles.put(theirsFilename, theirsContent);
    testRename_merged(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles);

  }

  @Theory
  public void checkRenameOnBothSides_modifyConflict(MergeStrategy strategy) throws Exception {
    // renamed on both sides to different names. Should be conflicting
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String oursFilename = "test/file2";
    String oursContent = slightlyModifiedContent;
    String theirsFilename = "test/file3";
    String theirsContent = originalContent;

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
    expectedConflicts.add(oursFilename);
    expectedConflicts.add(theirsFilename);

    Set<String> expectedIndex = new HashSet<>();
    expectedIndex.add(String.format("%s, mode:100644, stage:1, content:%s", originalFilename, originalContent));
    expectedIndex.add(String.format("%s, mode:100644, stage:2, content:%s", oursFilename, oursContent));
    expectedIndex.add(String.format("%s, mode:100644, stage:3, content:%s", theirsFilename, theirsContent));
    testRename_withConflict(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles, expectedConflicts, expectedIndex);
  }

  @Theory
  public void checkRenameOnBothSidesSameName_noConflict(MergeStrategy strategy, boolean isModifyInOurs) throws Exception {
    // renamed on both sides the same name. Merges cleanly with the new content.
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename = "test/file2";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put(renameFilename, isModifyInOurs ? slightlyModifiedContent : originalContent);
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put(renameFilename, isModifyInOurs ? originalContent : slightlyModifiedContent);

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, slightlyModifiedContent);
    expectedFiles.put(originalFilename, null);

    testRename_merged(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles);
  }

  @Theory
  public void checkRenameToAdded_modifyConflict(MergeStrategy strategy) throws Exception {
    // One side renamed, another added a new file with the same name
    // Without rename detection, the renameFile would be content merged, the original file would fail with delete/modify conflict?
    // Probably best to skip rename processing and let it fail?
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String renameFilename = "test/file2";

    Map<String, String > originalFiles = new HashMap<>();
    originalFiles.put(originalFilename, originalContent);
    Map<String, String > filesWithAddedRename = new HashMap<>();
    filesWithAddedRename.put(renameFilename, "Unrelated file");
    filesWithAddedRename.put(originalFilename, slightlyModifiedContent);
    Map<String, String > pureRenameFiles = new HashMap<>();
    pureRenameFiles.put(renameFilename, originalContent);
    Set<String> expectedConflicts = new HashSet<>();
    expectedConflicts.add(renameFilename);
    expectedConflicts.add(originalFilename);
    Set<String> expectedIndex = new HashSet<>();
    expectedIndex.add(String.format("%s, mode:100644, stage:1, content:%s", originalFilename, originalContent));
    expectedIndex.add(String.format("%s, mode:100644, stage:2, content:%s", originalFilename, slightlyModifiedContent));
    expectedIndex.add(String.format("%s, mode:100644, stage:2, content:%s", renameFilename, "Unrelated file"));
    expectedIndex.add(String.format("%s, mode:100644, stage:3, content:%s", renameFilename, originalContent));

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(originalFilename, slightlyModifiedContent);
    expectedFiles.put(renameFilename, "Unrelated file");
    testRename_merged(strategy, originalFiles, filesWithAddedRename, pureRenameFiles, expectedFiles);
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

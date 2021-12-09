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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
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

  public void testRename_modifyConflict(MergeStrategy strategy, String originalName, String originalContent, String oursName, String oursContent, String theirsName, String theirsContent, Map<String, String> expectedFileContents) throws Exception {

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalName, originalContent);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put(oursName, oursContent);
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put(theirsName, theirsContent);
    testRename_modifyConflict(strategy, originalFiles, oursFiles, theirsFiles, expectedFileContents);
  }

  public void testRename_modifyConflict(MergeStrategy strategy, Map<String, String> originalFilesToContents,  Map<String, String> oursFilesToContents,  Map<String, String> theirsFilesToContents, Map<String, String> expectedFileContents) throws Exception {
    if (!strategy.equals(MergeStrategy.RECURSIVE)) {
      return;
    }
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
    MergeResult mergeResult = git.merge().include(renameCommit).setStrategy(strategy).call();
    assertEquals(mergeResult.getMergeStatus(), MergeStatus.MERGED);
    Set<String> expectedIndexContent = new HashSet<>();
    for (Entry<String, String> expectedFile : expectedFileContents.entrySet()) {
      assertEquals(expectedFile.getValue(), read(expectedFile.getKey()));
      expectedIndexContent.add(String.format("%s, mode:100644, content:%s", expectedFile.getKey(),
          expectedFile.getValue()));
    }
    // index contains only the expected files. Everything was merged.
    Set<String> stagedFiles = Arrays.asList(indexState(CONTENT).split("\\[|\\]")).stream()
        .filter(s ->
            !Strings.isNullOrEmpty(s)).collect(Collectors.toSet());
    assertEquals(stagedFiles, expectedIndexContent);
  }

  public void checkRenameTo_modifyConflict(MergeStrategy strategy, String originalFilename, String renameFilename, boolean isRenameInOurs) throws Exception {

    // classic conflict, renamed on one side, modified on other
    // take the rename with modified content
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(renameFilename, slightlyModifiedContent);
    if(isRenameInOurs) {
      testRename_modifyConflict(strategy, originalFilename, originalContent, renameFilename, originalContent, originalFilename,
          slightlyModifiedContent, expectedFiles);
    } else {
      testRename_modifyConflict(strategy, originalFilename, originalContent, originalFilename,
          slightlyModifiedContent, renameFilename, originalContent, expectedFiles);
    }
  }

  @DataPoints("singleRenamePairs")
  public static final List<Entry<String, String>> renamePairs = List.of(
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
  public void checkRenameFile_modifyConflict_renameInOurs(MergeStrategy strategy, @FromDataPoints("singleRenamePairs") Entry<String, String> renamePair) throws Exception {
    checkRenameTo_modifyConflict(strategy, renamePair.getKey(), renamePair.getValue(), true);

  }

  @Theory
  public void checkRenameFile_modifyConflict_renameInTheirs(MergeStrategy strategy, @FromDataPoints("singleRenamePairs") Entry<String, String> renamePair) throws Exception {
    checkRenameTo_modifyConflict(strategy, renamePair.getKey(), renamePair.getValue(), false);

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
  public void checkRenameFile_noContentModification(MergeStrategy strategy, @FromDataPoints("singleRenamePairs") Entry<String, String> renamePair, boolean isRenameInOurs) throws Exception {
    String originalContent = "a\nb\nc";
    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(renamePair.getKey(), originalContent);
    Map<String, String> nonRenameFiles = new HashMap<>();
    nonRenameFiles.put(renamePair.getKey(), originalContent);
    String fileAddition =getDir( renamePair.getKey())+"/file1.1";
    nonRenameFiles.put(fileAddition, "One file was added in ours");
    Map<String, String> filesWithRename = new HashMap<>();
    filesWithRename.put(renamePair.getValue(), originalContent);
    Map<String, String> expectedFiles = new HashMap<>();
    // This is merged, however everything is under test/a/, because the directory was not detected as rename.
    // Ideally, this should be all under test/sub/
    // With prior logic, this would have test/sub/file1 and test/a/file1
    expectedFiles.put(renamePair.getValue(), originalContent);
    expectedFiles.put(fileAddition, "One file was added in ours");
    testRename_modifyConflict(strategy, originalFiles,
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
    testRename_modifyConflict(strategy, originalFiles,
        isRenameInOurs ? filesWithRename : nonRenameFiles,
        isRenameInOurs ? nonRenameFiles : filesWithRename, expectedFiles);
  }

  @Theory
  public void checkRenameToSubDir_modifyConflict(MergeStrategy strategy) throws Exception {
    // classic conflict, renamed in thiers, modified in ours
    String originalFilename = "test/file1";
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    String oursFilename = originalFilename;
    String oursContent = slightlyModifiedContent;
    String theirsFilename = "test/sub/file1";
    String theirsContent = originalContent;

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(originalFilename, slightlyModifiedContent);
    testRename_modifyConflict(strategy, originalFilename, originalContent, oursFilename,
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
    expectedFiles.put(originalFilename, slightlyModifiedContent);
    testRename_modifyConflict(strategy, originalFilename, originalContent, oursFilename,
        oursContent, theirsFilename, theirsContent, expectedFiles);

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
    testRename_modifyConflict(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles);

  }

  @DataPoints("singleRenamePairs")
  public static final List<List<Entry<String, String>>> renameLists  = List.of(
      // Direct rename of file
      //List.of(Map.entry("test/file2","test/file1"))
      //List.of(Map.entry("test/file1","test/file2"))
      List.of(Map.entry("test/a/file1","test/z/file2"))
      //List.of(Map.entry("test/z/file1","test/a/file2")),
      // Move file to subdir
      //List.of(Map.entry("test/file1", "test/w/file1")),
      //List.of(Map.entry("test/file1", "test/a/file1")),
      // Move to parent dir
      //List.of(Map.entry("test/w/file1", "test/file1")),
      //List.of(Map.entry("test/a/file1", "test/file1"))
      );

  public static final List<String> fileAdditions = List.of("file3","file0","a/file", "z/file");

  @Theory
  public void checkRenameSubDir_AllFilesMoved_SomeFilesAddedOnRenameSide_modifyConflict(MergeStrategy strategy, @FromDataPoints("singleRenamePairs") List<Entry<String, String>> renamePairs, boolean isRenameInOurs) throws Exception {
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
    for (String fileAddition : fileAdditions) {
      renameFiles.put(String.format("%s/%s",renameDir, fileAddition),
          String.format("Another %s file was added in thiers", fileAddition));
    }
    Map<String, String> expectedFiles = new HashMap<>();
    // This should all be under rename dir?
    for (Entry<String, String> renamePair : renamePairs) {
      expectedFiles.put(renamePair.getValue(), slightlyModifiedContent + renamePair.getKey());
    }
    for (String fileAddition : fileAdditions) {
      expectedFiles.put(String.format("%s/%s",renameDir, fileAddition),
          String.format("Another %s file was added in thiers", fileAddition));
    }
    testRename_modifyConflict(strategy, originalFiles, isRenameInOurs ? renameFiles : noRenameFiles,
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
    testRename_modifyConflict(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles);
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
    testRename_modifyConflict(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles);
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
    testRename_modifyConflict(strategy, originalFiles, isRenameInOurs ? renameFiles : noRenameFiles,
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
    testRename_modifyConflict(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles);

  }

  @Theory
  public void checkRenameDirModifySingle_dirSplit_modifyConflict(MergeStrategy strategy) throws Exception {
    if (!strategy.equals(MergeStrategy.RECURSIVE)) {
      return;
    }

    Git git = Git.wrap(db);
    String originalContent1 = "a\nb\nc";
    String originalContent2 = "x\ny\nz";
    String slightlyModifiedContent1 = "a\nb\nb";
    // master
    writeTrashFile("test/a/file1", originalContent1);
    git.add().addFilepattern("test/a/file1").call();
    writeTrashFile("test/a/file2", originalContent2);
    git.add().addFilepattern("test/a/file2").call();
    RevCommit commitI = git.commit().setMessage("Initial commit").call();

    git.checkout().setCreateBranch(true).setStartPoint(commitI).setName("second-branch").call();
    // test/file1 is renamed to test/sub/file1 on second-branch
    git.rm().addFilepattern("test/a/file1").call();
    git.rm().addFilepattern("test/a/file2").call();
    git.rm().addFilepattern("test/a").call();
    writeTrashFile("test/a1/file1", originalContent1);
    git.add().addFilepattern("test/a1/file1").call();
    writeTrashFile("test/a2/file2", originalContent2);
    git.add().addFilepattern("test/a2/file2").call();
    RevCommit renameCommit = git.commit().setMessage("Rename file").call();

    // back to master, modify file
    git.checkout().setName("master").call();
    writeTrashFile("test/a/file1", slightlyModifiedContent1);
    git.add().addFilepattern("test/a/file1").call();
    writeTrashFile("test/a/file2", originalContent2);
    git.add().addFilepattern("test/a/file2").call();

    RevCommit modifyContentCommit = git.commit().setMessage("Commit slightly modified content").call();

    // Merge master into second-branch
    MergeResult mergeResult = git.merge().include(renameCommit).setStrategy(strategy).call();
    //assertEquals(mergeResult.getNewHead(), null);
    // No merge conflict, rename was detacted.
    assertEquals(mergeResult.getMergeStatus(), MergeStatus.MERGED);
    // Rename was detacted, but the original (base) filename was kept.
    assertEquals(slightlyModifiedContent1, read("test/a/file1"));
    assertEquals(originalContent2, read("test/a/file2"));
    assertEquals(false, check("test/sub/file1"));
    assertEquals(false, check("test/sub/file2"));
    // We get conflicting content, rename was not detected by merge.
    // The merger assumed the file 'test/file1' was modified on master and deleted
    // by renameCommit on second-branch.
    //assertEquals(
    //		"[test/a/file1, mode:100644, stage:1, content:a\nb\nc][test/a/file1, mode:100644, stage:2, content:a\nb\nb][test/sub/file1, mode:100644, content:a\nb\nc]",
    //		indexState(CONTENT));
    //assertEquals(
    //		"[test/a/file1, mode:100644, content:a\nb\nb][test/a/file2, mode:100644, content:x\ny\nz]",
    //			indexState(CONTENT));
    assertEquals(
        "[]",
        indexState(CONTENT));
    // With enabled rename detection on repository, rename is detected by diff.
    OutputStream out = new ByteArrayOutputStream();
    List<DiffEntry> entries = git.diff().setOutputStream(out).setOldTree(getTreeIterator("master"))
        .setNewTree(getTreeIterator("second-branch")).call();
    assertEquals(2, entries.size());
    assertEquals(ChangeType.RENAME, entries.get(1).getChangeType());

    assertEquals("test/a/file2", entries.get(1).getOldPath());
    assertEquals("test/sub/file2", entries.get(1).getNewPath());
    // This is a bit meaningless: since contents are the same, file1 is reported as rename, file2 as a copy
    // This is detected as
		/*assertEquals("diff --git a/test/a/file1 b/test/sub/file1\n" + "similarity index 79%\n"
				+ "rename from test/file1\n" + "rename to test/sub/file1\n" + "index e8b9973..1c943a9 100644\n"
				+ "--- a/test/a/file1\n" + "+++ b/test/sub/file1\n" + "@@ -1,3 +1,3 @@\n" + " a\n" + " b\n" + "-b\n"
				+ "\\ No newline at end of file\n" + "+c\n" + "\\ No newline at end of file\n", out.toString());*/

  }

  @Theory
  public void checkRenameSubDir_differentNestingLevel_modifyConflict(MergeStrategy strategy) throws Exception {
    if (!strategy.equals(MergeStrategy.RECURSIVE)) {
      return;
    }

    Git git = Git.wrap(db);
    String originalContent1 = "a\nb\nc";
    String originalContent2 = "x\ny\nz";
    String slightlyModifiedContent1 = "a\nb\nb";
    String slightlyModifiedContent2 = "x\ny\nz";
    // master
    writeTrashFile("test/a/file1", originalContent1);
    git.add().addFilepattern("test/a/file1").call();
    writeTrashFile("test/a/file2", originalContent2);
    git.add().addFilepattern("test/a/file2").call();
    RevCommit commitI = git.commit().setMessage("Initial commit").call();

    git.checkout().setCreateBranch(true).setStartPoint(commitI).setName("second-branch").call();
    // test/file1 is renamed to test/a/sub/file1 on second-branch
    git.rm().addFilepattern("test/a/file1").call();
    git.rm().addFilepattern("test/a/file2").call();
    // git.rm().addFilepattern("test/a").call();
    writeTrashFile("test/a/sub/file1", originalContent1);
    git.add().addFilepattern("test/a/sub").call();
    git.add().addFilepattern("test/a/sub/file1").call();
    writeTrashFile("test/a/file2", originalContent2);
    git.add().addFilepattern("test/a/file2").call();
    RevCommit renameCommit = git.commit().setMessage("Rename file").call();

    // back to master, modify file
    git.checkout().setName("master").call();
    writeTrashFile("test/a/file1", slightlyModifiedContent1);
    git.add().addFilepattern("test/a/file1").call();
    writeTrashFile("test/a/file2", slightlyModifiedContent2);
    git.add().addFilepattern("test/a/file2").call();

    RevCommit modifyContentCommit = git.commit().setMessage("Commit slightly modified content").call();

    // Merge master into second-branch
    MergeResult mergeResult = git.merge().include(renameCommit).setStrategy(strategy).call();
    //assertEquals(mergeResult.getNewHead(), null);
    // No merge conflict, rename was detacted.
    assertEquals(mergeResult.getMergeStatus(), MergeStatus.CONFLICTING);
    // Rename was detacted, but the original (base) filename was kept.
    assertEquals(slightlyModifiedContent1, read("test/a/file1"));
    assertEquals(slightlyModifiedContent2, read("test/a/file2"));
    assertEquals(originalContent1, read("test/a/sub/file1"));
    // We get conflicting content, rename was not detected by merge.
    // The merger assumed the file 'test/file1' was modified on master and deleted
    // by renameCommit on second-branch.
    //assertEquals(
    //		"[test/a/file1, mode:100644, stage:1, content:a\nb\nc][test/a/file1, mode:100644, stage:2, content:a\nb\nb][test/sub/file1, mode:100644, content:a\nb\nc]",
    //		indexState(CONTENT));
    assertEquals(
        "[test/a/file1, mode:100644, stage:1, content:a\nb\nc][test/a/file1, mode:100644, stage:2, content:a\nb\nb][test/a/file2, mode:100644, content:x\ny\nz][test/a/sub/file1, mode:100644, content:a\nb\nc]",
        indexState(CONTENT));
    // With enabled rename detection on repository, rename is detected by diff.
    OutputStream out = new ByteArrayOutputStream();
    List<DiffEntry> entries = git.diff().setOutputStream(out).setOldTree(getTreeIterator("master"))
        .setNewTree(getTreeIterator("second-branch")).call();
    assertEquals(3, entries.size());
    assertEquals(ChangeType.RENAME, entries.get(1).getChangeType());

    assertEquals("test/a/file1", entries.get(1).getOldPath());
    assertEquals("test/sub/file1", entries.get(1).getNewPath());
    // This is a bit meaningless: since contents are the same, file1 is reported as rename, file2 as a copy
    // This is detected as
		/*assertEquals("diff --git a/test/a/file1 b/test/sub/file1\n" + "similarity index 79%\n"
				+ "rename from test/file1\n" + "rename to test/sub/file1\n" + "index e8b9973..1c943a9 100644\n"
				+ "--- a/test/a/file1\n" + "+++ b/test/sub/file1\n" + "@@ -1,3 +1,3 @@\n" + " a\n" + " b\n" + "-b\n"
				+ "\\ No newline at end of file\n" + "+c\n" + "\\ No newline at end of file\n", out.toString());*/

  }

  @Theory
  public void checkRenameBoth_modifyConflict(MergeStrategy strategy) throws Exception {
    if (!strategy.equals(MergeStrategy.RECURSIVE)) {
      return;
    }

    Git git = Git.wrap(db);
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    // master
    writeTrashFile("test/file1", originalContent);
    git.add().addFilepattern("test/file1").call();
    RevCommit commitI = git.commit().setMessage("Initial commit").call();

    git.checkout().setCreateBranch(true).setStartPoint(commitI).setName("second-branch").call();
    // test/file1 is renamed to test/file2 on second-branch
    git.rm().addFilepattern("test/file1").call();
    writeTrashFile("test/file2", originalContent);
    git.add().addFilepattern("test/file2").call();
    RevCommit renameCommit = git.commit().setMessage("Rename file").call();

    // back to master, modify file
    git.checkout().setName("master").call();
    git.rm().addFilepattern("test/file1").call();
    writeTrashFile("test/file3", originalContent);
    git.add().addFilepattern("test/file3").call();

    RevCommit modifyContentCommit = git.commit().setMessage("Commit slightly modified content").call();

    // Merge master into second-branch
    MergeResult mergeResult = git.merge().include(renameCommit).setStrategy(strategy).call();
    //assertEquals(mergeResult.getNewHead(), null);
    assertEquals(mergeResult.getMergeStatus(), MergeStatus.MERGED);
    assertEquals(originalContent, read("test/file2"));
    assertEquals(originalContent, read("test/file3"));

    // We get conflicting content, rename was not detected y merge.
    // The merger assumed the file 'test/file1' was modified on master and deleted
    // by renameCommit on second-branch.
    assertEquals(
        "[test/file2, mode:100644, content:a\nb\nc][test/file3, mode:100644, content:a\nb\nc]",
        indexState(CONTENT));
    // With enabled rename detection on repository, rename is detected by diff.
    OutputStream out = new ByteArrayOutputStream();
    List<DiffEntry> entries = git.diff().setOutputStream(out).setOldTree(getTreeIterator("master"))
        .setNewTree(getTreeIterator("second-branch")).call();
    assertEquals(1, entries.size());
    assertEquals(ChangeType.DELETE, entries.get(0).getChangeType());

    assertEquals("test/file3", entries.get(0).getOldPath());
    assertEquals("diff --git a/test/file3 b/test/file3\n"
        + "deleted file mode 100644\n"
        + "index 1c943a9..0000000\n"
        + "--- a/test/file3\n"
        + "+++ /dev/null\n"
        + "@@ -1,3 +0,0 @@\n"
        + "-a\n"
        + "-b\n"
        + "-c\n" + "\\ No newline at end of file\n", out.toString());

  }

  @Theory
  public void checkRenameSubDir_renameOnly_noConflict(MergeStrategy strategy) throws Exception {
    if (!strategy.equals(MergeStrategy.RECURSIVE)) {
      return;
    }

    Git git = Git.wrap(db);
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";
    // master
    writeTrashFile("test/file1", originalContent);
    git.add().addFilepattern("test/file1").call();
    RevCommit commitI = git.commit().setMessage("Initial commit").call();

    git.checkout().setCreateBranch(true).setStartPoint(commitI).setName("second-branch").call();
    // test/file1 is renamed to test/sub/file1 on second-branch
    git.rm().addFilepattern("test/file1").call();
    writeTrashFile("test/sub/file1", originalContent);
    git.add().addFilepattern("test/sub/file1").call();
    RevCommit renameCommit = git.commit().setMessage("Rename file").call();

    // back to master do not modify content.
    git.checkout().setName("master").call();
    writeTrashFile("test/file1", originalContent);
    git.add().addFilepattern("test/file1").call();

    RevCommit modifyContentCommit = git.commit().setMessage("Commit same content").call();

    // Merge master into second-branch
    MergeResult mergeResult = git.merge().include(renameCommit).setStrategy(strategy).call();
    assertEquals(mergeResult.getMergeStatus(), MergeStatus.MERGED);
    assertEquals(originalContent, read("test/sub/file1"));

    // Change was merged, since the merger assumed the renameCommit just deleted the
    // original file, and the content was not modified on master.
    assertEquals("[test/sub/file1, mode:100644, content:a\nb\nc]", indexState(CONTENT));
    // With enabled rename detection on repository, rename is detected by diff.
    OutputStream out = new ByteArrayOutputStream();
    List<DiffEntry> entries = git.diff().setOutputStream(out).setOldTree(getTreeIterator("master"))
        .setNewTree(getTreeIterator("second-branch")).call();
    assertEquals(1, entries.size());
    assertEquals(ChangeType.RENAME, entries.get(0).getChangeType());

    assertEquals("test/file1", entries.get(0).getOldPath());
    assertEquals("test/sub/file1", entries.get(0).getNewPath());
    assertEquals("", out.toString());
  }

  private String getDir(String path){
    int endDir = path.lastIndexOf("/");
    return path.substring(0, endDir);
  }

}

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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Instant.EPOCH;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.Map.Entry;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.NoMergeBaseException;
import org.eclipse.jgit.errors.NoMergeBaseException.MergeBaseFailureReason;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.experimental.theories.DataPoints;
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

  public void testRename_modifyConflict(MergeStrategy strategy, String originalName, String originalContent, String oursName, String oursContent, String theirsName, String theirsContent, Map<String, String> expectedFileContents, String expectedIndexState) throws Exception {

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put(originalName, originalContent);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put(oursName, oursContent);
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put(theirsName, theirsContent);
    testRename_modifyConflict(strategy, originalFiles, oursFiles, theirsFiles, expectedFileContents,
        expectedIndexState);
  }

  public void testRename_modifyConflict(MergeStrategy strategy, Map<String, String> originalFilesToContents,  Map<String, String> oursFilesToContents,  Map<String, String> theirsFilesToContents, Map<String, String> expectedFileContents, String expectedIndexState) throws Exception {
    if (!strategy.equals(MergeStrategy.RECURSIVE)) {
      return;
    }
    Git git = Git.wrap(db);

    // master
    for(Entry<String, String> originalFile : originalFilesToContents.entrySet()) {
      writeTrashFile(originalFile.getKey(), originalFile.getValue());
      git.add().addFilepattern(originalFile.getKey()).call();
    }
    RevCommit commitI = git.commit().setMessage("Initial commit").call();

    git.checkout().setCreateBranch(true).setStartPoint(commitI).setName("second-branch").call();
    for(Entry<String, String> originalFile : originalFilesToContents.entrySet()) {
      // test/file1 is renamed to test/sub/file1 on second-branch
      git.rm().addFilepattern(originalFile.getKey()).call();
    }
    for(Entry<String, String> theirsFile : theirsFilesToContents.entrySet()) {
      writeTrashFile(theirsFile.getKey(), theirsFile.getValue());
      git.add().addFilepattern(theirsFile.getKey()).call();
    }
    RevCommit renameCommit = git.commit().setMessage("Rename file").call();

    // back to master, modify file
    git.checkout().setName("master").call();

    for(Entry<String, String> originalFile : originalFilesToContents.entrySet()) {
      git.rm().addFilepattern(originalFile.getKey()).call();
    }
    for(Entry<String, String> oursFile : oursFilesToContents.entrySet()) {
      writeTrashFile(oursFile.getKey(), oursFile.getValue());
      git.add().addFilepattern(oursFile.getKey()).call();
    }

    RevCommit modifyContentCommit = git.commit().setMessage("Commit slightly modified content")
        .call();

    // Merge master into second-branch
    MergeResult mergeResult = git.merge().include(renameCommit).setStrategy(strategy).call();
    assertEquals(mergeResult.getMergeStatus(), MergeStatus.MERGED);
    for (Entry<String, String> expectedFile : expectedFileContents.entrySet()) {
      assertEquals(expectedFile.getValue(), read(expectedFile.getKey()));
    }
    assertEquals(
        expectedIndexState,
        indexState(CONTENT));
  }

  public void checkRenameTo_modifyConflict(MergeStrategy strategy, String originalFilename, String renameFilename, boolean isRenameInOurs) throws Exception {

    // classic conflict, renamed on one side, modified on other
    String originalContent = "a\nb\nc";
    String slightlyModifiedContent = "a\nb\nb";

    Map<String, String> expectedFiles = new HashMap<>();
    expectedFiles.put(originalFilename, slightlyModifiedContent);
    if(isRenameInOurs) {
      testRename_modifyConflict(strategy, originalFilename, originalContent, renameFilename, originalContent, originalFilename,
          slightlyModifiedContent, expectedFiles, "[]");
    } else {
      testRename_modifyConflict(strategy, originalFilename, originalContent, originalFilename,
          slightlyModifiedContent, renameFilename, originalContent, expectedFiles, "[]");
    }
  }

  @Theory
  public void checkRenameFile_modifyConflict(MergeStrategy strategy) throws Exception {
    //checkRenameTo_modifyConflict(strategy, "test/file1", "test/file2", false);
    checkRenameTo_modifyConflict(strategy, "test/file1", "test/file2", false);

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
        oursContent, theirsFilename, theirsContent, expectedFiles, "[]");

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
        oursContent, theirsFilename, theirsContent, expectedFiles, "[]");

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
    expectedFiles.put("test/a/file1", slightlyModifiedContent1);
    expectedFiles.put("test/a/file2", slightlyModifiedContent2);
    testRename_modifyConflict(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles, "[]");

  }

  @Theory
  public void checkRenameSubDir_AllFilesMoved_SomeFilesAddedInTheirs_modifyConflict(MergeStrategy strategy) throws Exception {
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
    theirsFiles.put("test/sub/file3", "Another file was added in thiers");
    Map<String, String> expectedFiles = new HashMap<>();
    // This should all be under test/sub/
    expectedFiles.put("test/a/file1", slightlyModifiedContent1);
    expectedFiles.put("test/a/file2", slightlyModifiedContent2);
    expectedFiles.put("test/sub/file3", "Another file was added in thiers");
    testRename_modifyConflict(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles, "[]");
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
    expectedFiles.put("test/a/file1", slightlyModifiedContent1);
    expectedFiles.put("test/a/file2", slightlyModifiedContent2);
    expectedFiles.put("test/a/file3", "Added file");
    testRename_modifyConflict(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles, "[]");
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
    expectedFiles.put("test/a/file1", slightlyModifiedContent1);
    expectedFiles.put("test/a/file2", slightlyModifiedContent2);
    expectedFiles.put("test/a/file1.1", "One file was added in ours");
    expectedFiles.put("test/sub/file3", "Another file was added in thiers");
    testRename_modifyConflict(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles, "[]");
  }

  @Theory
  public void checkRenameDir_noContentModification(MergeStrategy strategy) throws Exception {
    String originalContent1 = "a\nb\nc";

    Map<String, String> originalFiles = new HashMap<>();
    originalFiles.put("test/a/file1", originalContent1);
    Map<String, String> oursFiles = new HashMap<>();
    oursFiles.put("test/a/file1", originalContent1);
    oursFiles.put("test/a/file1.1","One file was added in ours");
    Map<String, String> theirsFiles = new HashMap<>();
    theirsFiles.put("test/sub/file1", originalContent1);
    Map<String, String> expectedFiles = new HashMap<>();
    // This is merged, however everything is under test/a/, because the directory was not detected as rename.
    // Ideally, this should be all under test/sub/
    // With prior logic, this would have test/sub/file1 and test/a/file1
    expectedFiles.put("test/a/file1", originalContent1);
    expectedFiles.put("test/a/file1.1", "One file was added in ours");
    testRename_modifyConflict(strategy, originalFiles, oursFiles, theirsFiles, expectedFiles, "[]");
  }

  @Theory
  public void checkRenameSubDirModifySingle_modifyConflict(MergeStrategy strategy) throws Exception {
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
    writeTrashFile("test/sub/file1", originalContent1);
    git.add().addFilepattern("test/sub/file1").call();
    writeTrashFile("test/sub/file2", originalContent2);
    git.add().addFilepattern("test/sub/file2").call();
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
    assertEquals(
        "[test/a/file1, mode:100644, content:a\nb\nb][test/a/file2, mode:100644, content:x\ny\nz]",
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
  public void checkRenameSubDirModifySingle_allTo_SubDir_modifyConflict(MergeStrategy strategy) throws Exception {
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
    writeTrashFile("test/a/sub/file1", originalContent1);
    git.add().addFilepattern("test/a/sub/file1").call();
    writeTrashFile("test/a/sub/file2", originalContent2);
    git.add().addFilepattern("test/a/sub/file2").call();
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
    //		indexState(CONTENT));

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
  public void checkRenameSubDirModifySingle_allTo_ParentDir_modifyConflict(MergeStrategy strategy) throws Exception {
    if (!strategy.equals(MergeStrategy.RECURSIVE)) {
      return;
    }

    Git git = Git.wrap(db);
    String originalContent1 = "a\nb\nc";
    String originalContent2 = "x\ny\nz";
    String slightlyModifiedContent1 = "a\nb\nb";
    // master
    writeTrashFile("test/a/sub/file1", originalContent1);
    git.add().addFilepattern("test/a/sub/file1").call();
    writeTrashFile("test/a/sub/file2", originalContent2);
    git.add().addFilepattern("test/a/sub/file2").call();
    RevCommit commitI = git.commit().setMessage("Initial commit").call();

    git.checkout().setCreateBranch(true).setStartPoint(commitI).setName("second-branch").call();
    // test/file1 is renamed to test/sub/file1 on second-branch
    git.rm().addFilepattern("test/a/sub/file1").call();
    git.rm().addFilepattern("test/a/sub/file2").call();
    git.rm().addFilepattern("test/a/sub").call();
    writeTrashFile("test/a/file1", originalContent1);
    git.add().addFilepattern("test/a/file1").call();
    writeTrashFile("test/a/file2", originalContent2);
    git.add().addFilepattern("test/a/file2").call();
    RevCommit renameCommit = git.commit().setMessage("Rename file").call();

    // back to master, modify file
    git.checkout().setName("master").call();
    writeTrashFile("test/a/sub/file1", slightlyModifiedContent1);
    git.add().addFilepattern("test/a/sub/file1").call();
    writeTrashFile("test/a/sub/file2", originalContent2);
    git.add().addFilepattern("test/a/sub/file2").call();

    RevCommit modifyContentCommit = git.commit().setMessage("Commit slightly modified content").call();

    // Merge master into second-branch
    MergeResult mergeResult = git.merge().include(renameCommit).setStrategy(strategy).call();
    //assertEquals(mergeResult.getNewHead(), null);
    // No merge conflict, rename was detacted.
    assertEquals(mergeResult.getMergeStatus(), MergeStatus.MERGED);
    // Rename was detacted, but the original (base) filename was kept.
    //assertEquals(slightlyModifiedContent1, read("test/a/file1"));
    //assertEquals(originalContent2, read("test/a/file2"));
    // We get conflicting content, rename was not detected by merge.
    // The merger assumed the file 'test/file1' was modified on master and deleted
    // by renameCommit on second-branch.
    //assertEquals(
    //		"[test/a/file1, mode:100644, stage:1, content:a\nb\nc][test/a/file1, mode:100644, stage:2, content:a\nb\nb][test/sub/file1, mode:100644, content:a\nb\nc]",
    //		indexState(CONTENT));
    //assertEquals(
    //		"[test/a/file1, mode:100644, content:a\nb\nb][test/a/file2, mode:100644, content:x\ny\nz]",
    //		indexState(CONTENT));
    assertEquals(
        "",
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
  public void checkRenameSubDirModifySingle_dirSplit_modifyConflict(MergeStrategy strategy) throws Exception {
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
    writeTrashFile("test/a/sub1/file1", originalContent1);
    git.add().addFilepattern("test/a/sub1/file1").call();
    writeTrashFile("test/a/sub2/file2", originalContent2);
    git.add().addFilepattern("test/a/sub2/file2").call();
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
    //		indexState(CONTENT));
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

  private void writeSubmodule(String path, ObjectId commit)
      throws IOException, ConfigInvalidException {
    addSubmoduleToIndex(path, commit);
    new File(db.getWorkTree(), path).mkdir();

    StoredConfig config = db.getConfig();
    config.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
        ConfigConstants.CONFIG_KEY_URL,
        db.getDirectory().toURI().toString());
    config.save();

    FileBasedConfig modulesConfig = new FileBasedConfig(
        new File(db.getWorkTree(), Constants.DOT_GIT_MODULES),
        db.getFS());
    modulesConfig.load();
    modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path,
        ConfigConstants.CONFIG_KEY_PATH, path);
    modulesConfig.save();

  }

  private void addSubmoduleToIndex(String path, ObjectId commit)
      throws IOException {
    DirCache cache = db.lockDirCache();
    DirCacheEditor editor = cache.editor();
    editor.add(new DirCacheEditor.PathEdit(path) {

      @Override
      public void apply(DirCacheEntry ent) {
        ent.setFileMode(FileMode.GITLINK);
        ent.setObjectId(commit);
      }
    });
    editor.commit();
  }

  // Assert that every specified index entry has the same last modification
  // timestamp as the associated file
  private void checkConsistentLastModified(String... pathes)
      throws IOException {
    DirCache dc = db.readDirCache();
    File workTree = db.getWorkTree();
    for (String path : pathes)
      assertEquals(
          "IndexEntry with path "
              + path
              + " has lastmodified which is different from the worktree file",
          FS.DETECTED.lastModifiedInstant(new File(workTree, path)),
          dc.getEntry(path)
              .getLastModifiedInstant());
  }

  // Assert that modification timestamps of working tree files are as
  // expected. You may specify n files. It is asserted that every file
  // i+1 is not older than file i. If a path of file i+1 is prefixed with "<"
  // then this file must be younger then file i. A path "*<modtime>"
  // represents a file with a modification time of <modtime>
  // E.g. ("a", "b", "<c", "f/a.txt") means: a<=b<c<=f/a.txt
  private void checkModificationTimeStampOrder(String... pathes) {
    Instant lastMod = EPOCH;
    for (String p : pathes) {
      boolean strong = p.startsWith("<");
      boolean fixed = p.charAt(strong ? 1 : 0) == '*';
      p = p.substring((strong ? 1 : 0) + (fixed ? 1 : 0));
      Instant curMod = fixed ? Instant.parse(p)
          : FS.DETECTED
              .lastModifiedInstant(new File(db.getWorkTree(), p));
      if (strong) {
        assertTrue("path " + p + " is not younger than predecesssor",
            curMod.compareTo(lastMod) > 0);
      } else {
        assertTrue("path " + p + " is older than predecesssor",
            curMod.compareTo(lastMod) >= 0);
      }
    }
  }

  private String readBlob(ObjectId treeish, String path) throws Exception {
    try (TestRepository<?> tr = new TestRepository<>(db);
        RevWalk rw = tr.getRevWalk()) {
      RevTree tree = rw.parseTree(treeish);
      RevObject obj = tr.get(tree, path);
      if (obj == null) {
        return null;
      }
      return new String(
          rw.getObjectReader().open(obj, OBJ_BLOB).getBytes(), UTF_8);
    }
  }
}

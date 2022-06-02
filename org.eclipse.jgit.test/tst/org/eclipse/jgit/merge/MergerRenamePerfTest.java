package org.eclipse.jgit.merge;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Rule;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * Performance tests for rename detection in {@link ResolveMerger}
 */
@RunWith(Theories.class)
public class MergerRenamePerfTest extends RepositoryTestCase {

  private static final String ORIGINAL_CONTENT = "a\nb\nc\n";

  private static final String SLIGHTLY_MODIFIED_CONTENT = "z\na\nb\nc";

  String ORIGINAL_DIR = "test-dir";
  String RENAMED_DIR = "renamed-dir";
  @Rule
  public TestName testName = new TestName();

  public void enableRename(boolean enableRename) throws IOException {
    StoredConfig config = db.getConfig();
    config.setString(ConfigConstants.CONFIG_DIFF_SECTION, null,
        ConfigConstants.CONFIG_KEY_RENAMES, enableRename ? "true" : "false");
    config.setString(ConfigConstants.CONFIG_MERGE_SECTION, null,
        ConfigConstants.CONFIG_KEY_RENAMES, enableRename ? "true" : "false");
    config.save();
  }

  /**
   * Perf test
   */
  public MergeResult setUpMergeRename(int numFiles, String origDir, String newDir,
      boolean isRenameInTheirs) throws Exception {
    Git git = Git.wrap(db);

    // master
    for (int i = 0; i < numFiles; i++) {
      String originalFilename = String.format("%s/file%s", origDir, i);
      String originalContent = ORIGINAL_CONTENT + originalFilename;
      writeTrashFile(originalFilename, originalContent);
      git.add().addFilepattern(originalFilename).call();
    }
    RevCommit commitI = git.commit().setMessage("Initial commit").call();

    git.checkout().setCreateBranch(true).setStartPoint(commitI)
        .setName("second-branch").call();

    for (int i = 0; i < numFiles; i++) {
      String originalFilename = String.format("%s/file%s", origDir, i);
      String originalContent = ORIGINAL_CONTENT + originalFilename;
      String renameFilename = String.format("%s/file%s", newDir, i);
      String modifiedContent = SLIGHTLY_MODIFIED_CONTENT + originalFilename;
      git.rm().addFilepattern(originalFilename).call();
      String theirsName = isRenameInTheirs ? renameFilename : originalFilename;
      String theirsContent = isRenameInTheirs ? originalContent : modifiedContent;
      writeTrashFile(theirsName, theirsContent);
      git.add().addFilepattern(theirsName).call();
    }

    RevCommit mergeCommit = git.commit()
        .setMessage("Commit on second-branch").call();

    git.checkout().setName("master").call();

    for (int i = 0; i < numFiles; i++) {
      String originalFilename = String.format("%s/file%s", origDir, i);
      String originalContent = ORIGINAL_CONTENT + originalFilename;
      String renameFilename = String.format("%s/file%s", newDir, i);
      String modifiedContent = SLIGHTLY_MODIFIED_CONTENT + originalFilename;
      git.rm().addFilepattern(originalFilename).call();
      String oursName = isRenameInTheirs ? originalFilename : renameFilename;
      String oursContent = isRenameInTheirs ? modifiedContent : originalContent;
      writeTrashFile(oursName, oursContent);
      git.add().addFilepattern(oursName).call();
    }

    // headCommit
    git.commit().setMessage("Commit on master").call();

    // Merge master into second-branch
    long start = System.nanoTime();
    MergeResult mergeResult = git.merge().include(mergeCommit).setStrategy(MergeStrategy.RECURSIVE)
        .call();
    long end = System.nanoTime();
    long timeElapsed = end - start;
    System.out.println(
        String.format("Time elapsed:\n Nanos:%s, Millis:%s", timeElapsed, timeElapsed / 1000000.0));
    return mergeResult;

  }

  @Theory
  public void mergeRename_renameInTheirs(boolean renameEnabled) throws Exception {
    System.out.println(String.format("Test: %s_renameEnabled_%s",
        testName.getMethodName(), renameEnabled));
    enableRename(renameEnabled);
    int numFiles = 1000;
    MergeResult mergeResult = setUpMergeRename(numFiles, ORIGINAL_DIR,
        RENAMED_DIR, /* renameInTheirs= */ true);
    assertEquals(mergeResult.getMergeStatus(),
        renameEnabled ? MergeStatus.MERGED : MergeStatus.CONFLICTING);
  }

  @Theory
  public void mergeRename_renameInOurs(boolean renameEnabled) throws Exception {
    System.out.println(String.format("Test: %s_renameEnabled_%s",
        testName.getMethodName(), renameEnabled));
    enableRename(renameEnabled);
    int numFiles = 1000;
    MergeResult mergeResult = setUpMergeRename(numFiles, ORIGINAL_DIR,
        RENAMED_DIR, /* renameInTheirs= */ false);
    assertEquals(mergeResult.getMergeStatus(),
        renameEnabled ? MergeStatus.MERGED : MergeStatus.CONFLICTING);
  }

  @Theory
  public void mergeNoRename_renameInTheirs(boolean renameEnabled) throws Exception {
    System.out.println(String.format("Test: %s_renameEnabled_%s",
        testName.getMethodName(), renameEnabled));
    enableRename(renameEnabled);
    int numFiles = 1000;
    MergeResult mergeResult = setUpMergeRename(numFiles, ORIGINAL_DIR, ORIGINAL_DIR,
        /* renameInTheirs= */ true);
    assertEquals(mergeResult.getMergeStatus(), MergeStatus.MERGED);
  }

  @Theory
  public void mergeNoRename_renameInOurs(boolean renameEnabled) throws Exception {
    System.out.println(String.format("Test: %s_renameEnabled_%s",
        testName.getMethodName(), renameEnabled));
    enableRename(renameEnabled);
    int numFiles = 1000;
    MergeResult mergeResult = setUpMergeRename(numFiles, ORIGINAL_DIR, ORIGINAL_DIR,
        /* renameInTheirs= */ false);
    assertEquals(mergeResult.getMergeStatus(), MergeStatus.MERGED);
  }

}

package org.eclipse.jgit.merge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/**
 * Tests for rename detection in {@link ResolveMerger} on criss-cross merges.
 */
@RunWith(Theories.class)
public class MergerRenameCrissCrossTest extends RepositoryTestCase {

	private static final String ORIGINAL_CONTENT = "a\nb\nc\n";

	private static final String SLIGHTLY_MODIFIED_CONTENT1 = "z\na\nb\nc";

	String SLIGHTLY_MODIFIED_CONTENT2 = "a\nb\nc\nd";

	String ORIGINAL_FILENAME = "file1";

	String RENAME_FILENAME1 = "file2";

	String RENAME_FILENAME2 = "file3";

	private static final ThreeWayMergeStrategy STRATEGY = MergeStrategy.RECURSIVE;

	@Before
	public void enableRename() throws IOException {
		StoredConfig config = db.getConfig();
		config.setString(ConfigConstants.CONFIG_DIFF_SECTION, null,
				ConfigConstants.CONFIG_KEY_RENAMES, "true");
		config.setString(ConfigConstants.CONFIG_MERGE_SECTION, null,
				ConfigConstants.CONFIG_KEY_RENAMES, "true");
		config.save();
	}

	/**
	 * Merging two commits with a rename/rename conflict in the virtual
	 * ancestor.
	 *
	 * <p>
	 * Those conflicts should be ignored, otherwise the found base can not be
	 * used by the RecursiveMerger.
	 * 
	 * <pre>
	 *  --------------
	 * |              \
	 * |         C1 - C4 --- ?     master
	 * |        /          /
	 * |  I - A1 - C2 - C3         second-branch
	 * |   \            /
	 * \    \          /
	 *  ----A2--------             branch-to-merge
	 * </pre>
	 * <p>
	 * <p>
	 * ORIGINAL_FILENAME is renamed to different paths in A1 and A2
	 * ("branch-to-merge").
	 * <p>
	 * A2 is merged into "master" and "second-branch". The rename/rename merge
	 * conflict is resolved manually, results in C4 and C3.
	 * <p>
	 * While merging C3 and C4, A1 and A2 are the base commits found by the
	 * recursive merge that have the rename/rename conflict.
	 */
	public MergeResult setUpRenameRenameConflictInVirtualAncestor(
			String resolveName1, String resolveContent1, String resolveName2,
			String resolveContent2) throws Exception {

		Git git = Git.wrap(db);

		// master
		writeTrashFile(ORIGINAL_FILENAME, ORIGINAL_CONTENT);
		git.add().addFilepattern(ORIGINAL_FILENAME).call();
		RevCommit commitI = git.commit().setMessage("Initial commit").call();

		// "file1" becomes "file2" in A1
		writeTrashFile(RENAME_FILENAME1, ORIGINAL_CONTENT);
		git.rm().addFilepattern(ORIGINAL_FILENAME).call();
		git.add().addFilepattern(RENAME_FILENAME1).call();
		RevCommit commitA1 = git.commit().setMessage("Ancestor 1").call();

		writeTrashFile(RENAME_FILENAME1, SLIGHTLY_MODIFIED_CONTENT1);
		git.rm().addFilepattern(ORIGINAL_FILENAME).call();
		git.add().addFilepattern(RENAME_FILENAME1).call();
		// commit C1M
		git.commit().setMessage("Child 1 on master").call();

		git.checkout().setCreateBranch(true).setStartPoint(commitI)
				.setName("branch-to-merge").call();
		// "file1" becomes "file3" in A2
		git.rm().addFilepattern(ORIGINAL_FILENAME).call();
		writeTrashFile(RENAME_FILENAME2, ORIGINAL_CONTENT);
		git.add().addFilepattern(RENAME_FILENAME2).call();
		RevCommit commitA2 = git.commit().setMessage("Ancestor 2").call();

		// second branch
		git.checkout().setCreateBranch(true).setStartPoint(commitA1)
				.setName("second-branch").call();
		writeTrashFile(RENAME_FILENAME1, SLIGHTLY_MODIFIED_CONTENT2);
		git.add().addFilepattern(RENAME_FILENAME1).call();
		// commit C2S
		git.commit().setMessage("Child 2 on second-branch").call();
		// Merge branch-to-merge into second-branch
		MergeResult mergeResult = git.merge().include(commitA2)
				.setStrategy(MergeStrategy.RECURSIVE).call();
		assertEquals(mergeResult.getNewHead(), null);
		assertEquals(mergeResult.getMergeStatus(), MergeStatus.CONFLICTING);

		// Resolve the conflict manually
		git.rm().addFilepattern(ORIGINAL_FILENAME).call();
		git.rm().addFilepattern(RENAME_FILENAME1).call();
		git.rm().addFilepattern(RENAME_FILENAME2).call();
		writeTrashFile(resolveName1, resolveContent1);
		git.add().addFilepattern(resolveName1).call();
		RevCommit commitC3S = git.commit()
				.setMessage("Child 3 on second bug - resolve merge conflict")
				.call();

		// Merge branch-to-merge into master
		git.checkout().setName("master").call();
		mergeResult = git.merge().include(commitA2)
				.setStrategy(MergeStrategy.RECURSIVE).call();
		assertEquals(mergeResult.getNewHead(), null);
		assertEquals(mergeResult.getMergeStatus(), MergeStatus.CONFLICTING);

		git.rm().addFilepattern(ORIGINAL_FILENAME).call();
		git.rm().addFilepattern(RENAME_FILENAME1).call();
		git.rm().addFilepattern(RENAME_FILENAME2).call();
		// Resolve the conflict manually
		writeTrashFile(resolveName2, resolveContent2);
		git.add().addFilepattern(resolveName2).call();
		// commit C4M
		git.commit().setMessage("Child 4 on master - resolve merge conflict")
				.call();

		// Merge C4M (second-branch) into master (C3S)
		// Conflict in virtual base should be here, but there are no conflicts
		// in
		// children
		return git.merge().setStrategy(STRATEGY).include(commitC3S).call();

	}

	@Theory
	public void checkRenameMergeConflictInVirtualAncestor_resolveToDifferentNamesInChildren_renameNotDetected(
			boolean keepInOurs) throws Exception {
		MergeResult mergeResult = setUpRenameRenameConflictInVirtualAncestor(
				keepInOurs ? RENAME_FILENAME1 : RENAME_FILENAME2,
				SLIGHTLY_MODIFIED_CONTENT1,
				keepInOurs ? RENAME_FILENAME2 : RENAME_FILENAME1,
				SLIGHTLY_MODIFIED_CONTENT2);
		assertFalse(check(ORIGINAL_FILENAME));
		// Since rename was not detected, both files are present in the final
		// version
		assertEquals(SLIGHTLY_MODIFIED_CONTENT1,
				read(keepInOurs ? RENAME_FILENAME1 : RENAME_FILENAME2));
		assertEquals(SLIGHTLY_MODIFIED_CONTENT2,
				read(keepInOurs ? RENAME_FILENAME2 : RENAME_FILENAME1));
		assertEquals(mergeResult.getMergeStatus(), MergeStatus.MERGED);

	}

	@Theory
	public void checkRenameMergeConflictInVirtualAncestor_resolveToSameName_conflicting()
			throws Exception {
		MergeResult mergeResult = setUpRenameRenameConflictInVirtualAncestor(
				RENAME_FILENAME2, SLIGHTLY_MODIFIED_CONTENT2, RENAME_FILENAME2,
				SLIGHTLY_MODIFIED_CONTENT1);
		assertEquals(mergeResult.getMergeStatus(), MergeStatus.CONFLICTING);
		assertFalse(check(ORIGINAL_FILENAME));
		assertFalse(check(RENAME_FILENAME1));
		// Since virtual base did not have the source file for content merge,
		// the three-way content merge resulted in a conflict.
		assertTrue(read(RENAME_FILENAME2).contains(String.format(
				"<<<<<<< HEAD\n%s\n" + "=======\n%s\n",
				SLIGHTLY_MODIFIED_CONTENT1, SLIGHTLY_MODIFIED_CONTENT2)));
	}
}

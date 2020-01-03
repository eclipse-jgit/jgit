/*
 * Copyright (C) 2012, 2015 François Rey <eclipse.org_@_francois_._rey_._name> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import static org.eclipse.jgit.lib.Constants.MASTER;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class StatusTest extends CLIRepositoryTestCase {

	@Test
	public void testPathOptionHelp() throws Exception {
		String[] result = execute("git status -h");
		assertTrue("Unexpected argument: " + result[1],
				result[1].endsWith("[-- path ...]"));
	}

	@Test
	public void testStatusDefault() throws Exception {
		executeTest("git status", false, true);
	}

	@Test
	public void testStatusU() throws Exception {
		executeTest("git status -u", false, true);
	}

	@Test
	public void testStatusUno() throws Exception {
		executeTest("git status -uno", false, false);
	}

	@Test
	public void testStatusUall() throws Exception {
		executeTest("git status -uall", false, true);
	}

	@Test
	public void testStatusUntrackedFiles() throws Exception {
		executeTest("git status --untracked-files", false, true);
	}

	@Test
	public void testStatusUntrackedFilesNo() throws Exception {
		executeTest("git status --untracked-files=no", false, false);
	}

	@Test
	public void testStatusUntrackedFilesAll() throws Exception {
		executeTest("git status --untracked-files=all", false, true);
	}

	@Test
	public void testStatusPorcelain() throws Exception {
		executeTest("git status --porcelain", true, true);
	}

	@Test
	public void testStatusPorcelainU() throws Exception {
		executeTest("git status --porcelain -u", true, true);
	}

	@Test
	public void testStatusPorcelainUno() throws Exception {
		executeTest("git status --porcelain -uno", true, false);
	}

	@Test
	public void testStatusPorcelainUall() throws Exception {
		executeTest("git status --porcelain -uall", true, true);
	}

	@Test
	public void testStatusPorcelainUntrackedFiles() throws Exception {
		executeTest("git status --porcelain --untracked-files", true, true);
	}

	@Test
	public void testStatusPorcelainUntrackedFilesNo() throws Exception {
		executeTest("git status --porcelain --untracked-files=no", true, false);
	}

	@Test
	public void testStatusPorcelainUntrackedFilesAll() throws Exception {
		executeTest("git status --porcelain --untracked-files=all", true, true);
	}

	/**
	 * Executes the test sequence.
	 *
	 * @param command
	 *            full git command and parameters to be used
	 * @param porcelain
	 *            indicates that porcelain format is expected in the output
	 * @param untrackedFiles
	 *            indicates that untracked files are expected in the output
	 *
	 * @throws Exception
	 *             if error during test execution
	 */
	private void executeTest(String command, boolean porcelain,
			boolean untrackedFiles) throws Exception {
		Git git = new Git(db);
		// Write all files
		writeAllFiles();
		// Test untracked
		assertUntrackedFiles(command, porcelain, untrackedFiles);
		// Add to index
		addFilesToIndex(git);
		// Test staged count
		assertStagedFiles(command, porcelain, untrackedFiles);
		// Commit
		makeInitialCommit(git);
		assertAfterInitialCommit(command, porcelain, untrackedFiles);
		// Make some changes and stage them
		makeSomeChangesAndStageThem(git);
		// Test staged/not-staged status
		assertStagedStatus(command, porcelain, untrackedFiles);
		// Create unmerged file
		createUnmergedFile(git);
		// Commit pending changes
		commitPendingChanges(git);
		assertUntracked(command, porcelain, untrackedFiles, "master");
		// Checkout new branch
		checkoutTestBranch(git);
		// Test branch status
		assertUntracked(command, porcelain, untrackedFiles, "test");
		// Commit change and checkout master again
		RevCommit testBranch = commitChangesInTestBranch(git);
		assertUntracked(command, porcelain, untrackedFiles, "test");
		checkoutMasterBranch(git);
		// Change the same file and commit
		changeUnmergedFileAndCommit(git);
		assertUntracked(command, porcelain, untrackedFiles, "master");
		// Merge test branch into master
		mergeTestBranchInMaster(git, testBranch);
		// Test unmerged status
		assertUntrackedAndUnmerged(command, porcelain, untrackedFiles, "master");
		// Test detached head
		detachHead(git);
		assertUntrackedAndUnmerged(command, porcelain, untrackedFiles, null);
	}

	private void writeAllFiles() throws IOException {
		writeTrashFile("tracked", "tracked");
		writeTrashFile("stagedNew", "stagedNew");
		writeTrashFile("stagedModified", "stagedModified");
		writeTrashFile("stagedDeleted", "stagedDeleted");
		writeTrashFile("trackedModified", "trackedModified");
		writeTrashFile("trackedDeleted", "trackedDeleted");
		writeTrashFile("untracked", "untracked");
	}

	private void addFilesToIndex(Git git) throws GitAPIException {
		git.add().addFilepattern("tracked").call();
		git.add().addFilepattern("stagedModified").call();
		git.add().addFilepattern("stagedDeleted").call();
		git.add().addFilepattern("trackedModified").call();
		git.add().addFilepattern("trackedDeleted").call();
	}

	private void makeInitialCommit(Git git) throws GitAPIException {
		git.commit().setMessage("initial commit").call();
	}

	private void makeSomeChangesAndStageThem(Git git) throws IOException,
			GitAPIException {
		writeTrashFile("stagedModified", "stagedModified modified");
		deleteTrashFile("stagedDeleted");
		writeTrashFile("trackedModified", "trackedModified modified");
		deleteTrashFile("trackedDeleted");
		git.add().addFilepattern("stagedModified").call();
		git.rm().addFilepattern("stagedDeleted").call();
		git.add().addFilepattern("stagedNew").call();
	}

	private void createUnmergedFile(Git git) throws IOException,
			GitAPIException {
		writeTrashFile("unmerged", "unmerged");
		git.add().addFilepattern("unmerged").call();
	}

	private void commitPendingChanges(Git git) throws GitAPIException {
		git.add().addFilepattern("trackedModified").call();
		git.rm().addFilepattern("trackedDeleted").call();
		git.commit().setMessage("commit before branching").call();
	}

	private void checkoutTestBranch(Git git) throws GitAPIException {
		git.checkout().setCreateBranch(true).setName("test").call();
	}

	private RevCommit commitChangesInTestBranch(Git git) throws IOException,
			GitAPIException {
		writeTrashFile("unmerged", "changed in test branch");
		git.add().addFilepattern("unmerged").call();
		return git.commit()
				.setMessage("changed unmerged in test branch").call();
	}

	private void checkoutMasterBranch(Git git) throws GitAPIException {
		git.checkout().setName("master").call();
	}

	private void changeUnmergedFileAndCommit(Git git) throws IOException,
			GitAPIException {
		writeTrashFile("unmerged", "changed in master branch");
		git.add().addFilepattern("unmerged").call();
		git.commit().setMessage("changed unmerged in master branch").call();
	}

	private void mergeTestBranchInMaster(Git git, RevCommit aCommit)
			throws GitAPIException {
		git.merge().include(aCommit.getId()).call();
	}

	private void detachHead(Git git) throws IOException, GitAPIException {
		String commitId = db.exactRef(R_HEADS + MASTER).getObjectId().name();
		git.checkout().setName(commitId).call();
	}

	private void assertUntrackedFiles(String command, boolean porcelain,
			boolean untrackedFiles) throws Exception {
		String[] output = new String[0];

		if (porcelain) {
			if (untrackedFiles) {
				output = new String[] { //
						"?? stagedDeleted", //
						"?? stagedModified", //
						"?? stagedNew", //
						"?? tracked", //
						"?? trackedDeleted", //
						"?? trackedModified", //
						"?? untracked", //
						"" //
				};
			} else {
				output = new String[] { //
						"" //
				};
			}
		} else {
			if (untrackedFiles) {
				output = new String[] { //
						"On branch master", //
						"Untracked files:", //
						"",//
						"\tstagedDeleted", //
						"\tstagedModified", //
						"\tstagedNew", //
						"\ttracked", //
						"\ttrackedDeleted", //
						"\ttrackedModified", //
						"\tuntracked", //
						"" //
				};
			} else {
				output = new String[] { //
						"On branch master", //
						"" //
				};
			}
		}

		assertArrayOfLinesEquals(output, execute(command));
	}

	private void assertStagedFiles(String command, boolean porcelain,
			boolean untrackedFiles) throws Exception {
		String[] output = new String[0];

		if (porcelain) {
			if (untrackedFiles) {
				output = new String[] { //
						"A  stagedDeleted", //
						"A  stagedModified", //
						"A  tracked", //
						"A  trackedDeleted", //
						"A  trackedModified", //
						"?? stagedNew", //
						"?? untracked", //
						"" //
				};
			} else {
				output = new String[] { //
						"A  stagedDeleted", //
						"A  stagedModified", //
						"A  tracked", //
						"A  trackedDeleted", //
						"A  trackedModified", //
						"" //
				};
			}
		} else {
			if (untrackedFiles) {
				output = new String[] { //
						"On branch master", //
						"Changes to be committed:", //
						"", //
						"\tnew file:   stagedDeleted", //
						"\tnew file:   stagedModified", //
						"\tnew file:   tracked", //
						"\tnew file:   trackedDeleted", //
						"\tnew file:   trackedModified", //
						"", //
						"Untracked files:", //
						"", //
						"\tstagedNew", //
						"\tuntracked", //
						"" //
				};
			} else {
				output = new String[] { //
						"On branch master", //
						"Changes to be committed:", //
						"", //
						"\tnew file:   stagedDeleted", //
						"\tnew file:   stagedModified", //
						"\tnew file:   tracked", //
						"\tnew file:   trackedDeleted", //
						"\tnew file:   trackedModified", //
						"" //
				};
			}
		}

		assertArrayOfLinesEquals(output, execute(command));
	}

	private void assertAfterInitialCommit(String command, boolean porcelain,
			boolean untrackedFiles) throws Exception {
		String[] output = new String[0];

		if (porcelain) {
			if (untrackedFiles) {
				output = new String[] { //
						"?? stagedNew", //
						"?? untracked", //
						"" //
				};
			} else {
				output = new String[] { //
						"" //
				};
			}
		} else {
			if (untrackedFiles) {
				output = new String[] { //
						"On branch master", //
						"Untracked files:", //
						"", //
						"\tstagedNew", //
						"\tuntracked", //
						"" //
				};
			} else {
				output = new String[] { //
						"On branch master", //
						"" //
				};
			}
		}

		assertArrayOfLinesEquals(output, execute(command));
	}

	private void assertStagedStatus(String command, boolean porcelain,
			boolean untrackedFiles) throws Exception {
		String[] output = new String[0];

		if (porcelain) {
			if (untrackedFiles) {
				output = new String[] { //
						"D  stagedDeleted", //
						"M  stagedModified", //
						"A  stagedNew", //
						" D trackedDeleted", //
						" M trackedModified", //
						"?? untracked", //
						"" //
				};
			} else {
				output = new String[] { //
						"D  stagedDeleted", //
						"M  stagedModified", //
						"A  stagedNew", //
						" D trackedDeleted", //
						" M trackedModified", //
						"" //
				};
			}
		} else {
			if (untrackedFiles) {
				output = new String[] { //
						"On branch master", //
						"Changes to be committed:", //
						"", //
						"\tdeleted:    stagedDeleted", //
						"\tmodified:   stagedModified", //
						"\tnew file:   stagedNew", //
						"", //
						"Changes not staged for commit:", //
						"", //
						"\tdeleted:    trackedDeleted", //
						"\tmodified:   trackedModified", //
						"", //
						"Untracked files:", //
						"", //
						"\tuntracked", //
						"" //
				};
			} else {
				output = new String[] { //
						"On branch master", //
						"Changes to be committed:", //
						"", //
						"\tdeleted:    stagedDeleted", //
						"\tmodified:   stagedModified", //
						"\tnew file:   stagedNew", //
						"", //
						"Changes not staged for commit:", //
						"", //
						"\tdeleted:    trackedDeleted", //
						"\tmodified:   trackedModified", //
						"", //
				};
			}
		}

		assertArrayOfLinesEquals(output, execute(command));
	}

	private void assertUntracked(String command,
			boolean porcelain,
			boolean untrackedFiles, String branch) throws Exception {
		String[] output = new String[0];
		String branchHeader = "On branch " + branch;

		if (porcelain) {
			if (untrackedFiles) {
				output = new String[] { //
						"?? untracked", //
						"" //
				};
			} else {
				output = new String[] { //
						"" //
				};
			}
		} else {
			if (untrackedFiles) {
				output = new String[] { //
						branchHeader, //
						"Untracked files:", //
						"", //
						"\tuntracked", //
						"" //
				};
			} else {
				output = new String[] { //
						branchHeader, //
						"" //
				};
			}
		}

		assertArrayOfLinesEquals(output, execute(command));
	}

	private void assertUntrackedAndUnmerged(String command, boolean porcelain,
			boolean untrackedFiles, String branch) throws Exception {
		String[] output = new String[0];
		String branchHeader = (branch == null) //
				? "Not currently on any branch." //
				: "On branch " + branch;

		if (porcelain) {
			if (untrackedFiles) {
				output = new String[] { //
						"UU unmerged", //
						"?? untracked", //
						"" //
				};
			} else {
				output = new String[] { //
						"UU unmerged", //
						"" //
				};
			}
		} else {
			if (untrackedFiles) {
				output = new String[] { //
						branchHeader, //
						"Unmerged paths:", //
						"", //
						"\tboth modified:      unmerged", //
						"", //
						"Untracked files:", //
						"", //
						"\tuntracked", //
						"" //
				};
			} else {
				output = new String[] { //
						branchHeader, //
						"Unmerged paths:", //
						"", //
						"\tboth modified:      unmerged", //
						"" //
				};
			}
		}

		assertArrayOfLinesEquals(output, execute(command));
	}
}

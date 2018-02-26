/*
 * Copyright (C) 2017, Obeo (mathieu.cartaud@obeo.fr)
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.attributes.merge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.function.Consumer;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidMergeHeadsException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.attributes.Attribute;
import org.eclipse.jgit.attributes.Attributes;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.junit.Ignore;
import org.junit.Test;

public class MergeGitAttributeTest extends RepositoryTestCase {

	private static final String REFS_HEADS_RIGHT = "refs/heads/right";

	private static final String REFS_HEADS_MASTER = "refs/heads/master";

	private static final String REFS_HEADS_LEFT = "refs/heads/left";

	private static final String DISABLE_CHECK_BRANCH = "refs/heads/disabled_checked";

	private static final String ENABLE_CHECKED_BRANCH = "refs/heads/enabled_checked";

	private static final String ENABLED_CHECKED_GIF = "enabled_checked.gif";

	public Git createRepositoryBinaryConflict(Consumer<Git> initialCommit,
			Consumer<Git> leftCommit, Consumer<Git> rightCommit)
			throws NoFilepatternException, GitAPIException, NoWorkTreeException,
			IOException {
		// Set up a git whith conflict commits on images
		Git git = new Git(db);

		// First commit
		initialCommit.accept(git);
		git.add().addFilepattern(".").call();
		RevCommit firstCommit = git.commit().setAll(true)
				.setMessage("initial commit adding git attribute file").call();

		// Create branch and add an icon Checked_Boxe (enabled_checked)
		createBranch(firstCommit, REFS_HEADS_LEFT);
		checkoutBranch(REFS_HEADS_LEFT);
		leftCommit.accept(git);
		git.add().addFilepattern(".").call();
		git.commit().setMessage("Left").call();

		// Create a second branch from master Unchecked_Boxe
		checkoutBranch(REFS_HEADS_MASTER);
		createBranch(firstCommit, REFS_HEADS_RIGHT);
		checkoutBranch(REFS_HEADS_RIGHT);
		rightCommit.accept(git);
		git.add().addFilepattern(".").call();
		git.commit().setMessage("Right").call();

		checkoutBranch(REFS_HEADS_LEFT);
		return git;

	}

	@Test
	public void mergeTextualFile_NoAttr() throws NoWorkTreeException,
			NoFilepatternException, GitAPIException, IOException {
		try (Git git = createRepositoryBinaryConflict(g -> {
			try {
				writeTrashFile("main.cat", "A\n" + "B\n" + "C\n" + "D\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}, g -> {
			try {
				writeTrashFile("main.cat", "A\n" + "B\n" + "C\n" + "F\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}, g -> {
			try {
				writeTrashFile("main.cat", "A\n" + "E\n" + "C\n" + "D\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		})) {
			checkoutBranch(REFS_HEADS_LEFT);
			// Merge refs/heads/enabled_checked -> refs/heads/disabled_checked

			MergeResult mergeResult = git.merge()
					.include(git.getRepository().resolve(REFS_HEADS_RIGHT))
					.call();
			assertEquals(MergeStatus.MERGED, mergeResult.getMergeStatus());

			assertNull(mergeResult.getConflicts());

			// Check that the image was not modified (not conflict marker added)
			String result = read(
					writeTrashFile("res.cat", "A\n" + "E\n" + "C\n" + "F\n"));
			assertEquals(result, read(git.getRepository().getWorkTree().toPath()
					.resolve("main.cat").toFile()));
		}
	}

	@Test
	public void mergeTextualFile_UnsetMerge_Conflict()
			throws NoWorkTreeException, NoFilepatternException, GitAPIException,
			IOException {
		try (Git git = createRepositoryBinaryConflict(g -> {
			try {
				writeTrashFile(".gitattributes", "*.cat -merge");
				writeTrashFile("main.cat", "A\n" + "B\n" + "C\n" + "D\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}, g -> {
			try {
				writeTrashFile("main.cat", "A\n" + "B\n" + "C\n" + "F\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}, g -> {
			try {
				writeTrashFile("main.cat", "A\n" + "E\n" + "C\n" + "D\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		})) {
			// Check that the merge attribute is unset
			assertAddMergeAttributeUnset(REFS_HEADS_LEFT, "main.cat");
			assertAddMergeAttributeUnset(REFS_HEADS_RIGHT, "main.cat");

			checkoutBranch(REFS_HEADS_LEFT);
			// Merge refs/heads/enabled_checked -> refs/heads/disabled_checked

			String catContent = read(git.getRepository().getWorkTree().toPath()
					.resolve("main.cat").toFile());

			MergeResult mergeResult = git.merge()
					.include(git.getRepository().resolve(REFS_HEADS_RIGHT))
					.call();
			assertEquals(MergeStatus.CONFLICTING, mergeResult.getMergeStatus());

			// Check that the image was not modified (not conflict marker added)
			assertEquals(catContent, read(git.getRepository().getWorkTree()
					.toPath().resolve("main.cat").toFile()));
		}
	}

	@Test
	public void mergeTextualFile_UnsetMerge_NoConflict()
			throws NoWorkTreeException, NoFilepatternException, GitAPIException,
			IOException {
		try (Git git = createRepositoryBinaryConflict(g -> {
			try {
				writeTrashFile(".gitattributes", "*.txt -merge");
				writeTrashFile("main.cat", "A\n" + "B\n" + "C\n" + "D\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}, g -> {
			try {
				writeTrashFile("main.cat", "A\n" + "B\n" + "C\n" + "F\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}, g -> {
			try {
				writeTrashFile("main.cat", "A\n" + "E\n" + "C\n" + "D\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		})) {
			// Check that the merge attribute is unset
			assertAddMergeAttributeUndefined(REFS_HEADS_LEFT, "main.cat");
			assertAddMergeAttributeUndefined(REFS_HEADS_RIGHT, "main.cat");

			checkoutBranch(REFS_HEADS_LEFT);
			// Merge refs/heads/enabled_checked -> refs/heads/disabled_checked

			MergeResult mergeResult = git.merge()
					.include(git.getRepository().resolve(REFS_HEADS_RIGHT))
					.call();
			assertEquals(MergeStatus.MERGED, mergeResult.getMergeStatus());

			// Check that the image was not modified (not conflict marker added)
			String result = read(
					writeTrashFile("res.cat", "A\n" + "E\n" + "C\n" + "F\n"));
			assertEquals(result, read(git.getRepository().getWorkTree()
					.toPath().resolve("main.cat").toFile()));
		}
	}

	@Test
	public void mergeTextualFile_SetBinaryMerge_Conflict()
			throws NoWorkTreeException, NoFilepatternException, GitAPIException,
			IOException {
		try (Git git = createRepositoryBinaryConflict(g -> {
			try {
				writeTrashFile(".gitattributes", "*.cat merge=binary");
				writeTrashFile("main.cat", "A\n" + "B\n" + "C\n" + "D\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}, g -> {
			try {
				writeTrashFile("main.cat", "A\n" + "B\n" + "C\n" + "F\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}, g -> {
			try {
				writeTrashFile("main.cat", "A\n" + "E\n" + "C\n" + "D\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		})) {
			// Check that the merge attribute is set to binary
			assertAddMergeAttributeCustom(REFS_HEADS_LEFT, "main.cat",
					"binary");
			assertAddMergeAttributeCustom(REFS_HEADS_RIGHT, "main.cat",
					"binary");

			checkoutBranch(REFS_HEADS_LEFT);
			// Merge refs/heads/enabled_checked -> refs/heads/disabled_checked

			String catContent = read(git.getRepository().getWorkTree().toPath()
					.resolve("main.cat").toFile());

			MergeResult mergeResult = git.merge()
					.include(git.getRepository().resolve(REFS_HEADS_RIGHT))
					.call();
			assertEquals(MergeStatus.CONFLICTING, mergeResult.getMergeStatus());

			// Check that the image was not modified (not conflict marker added)
			assertEquals(catContent, read(git.getRepository().getWorkTree()
					.toPath().resolve("main.cat").toFile()));
		}
	}

	/*
	 * This test is commented because JGit add conflict markers in binary files.
	 * cf. https://www.eclipse.org/forums/index.php/t/1086511/
	 */
	@Test
	@Ignore
	public void mergeBinaryFile_NoAttr_Conflict() throws IllegalStateException,
			IOException, NoHeadException, ConcurrentRefUpdateException,
			CheckoutConflictException, InvalidMergeHeadsException,
			WrongRepositoryStateException, NoMessageException, GitAPIException {

		RevCommit disableCheckedCommit;
		// Set up a git with conflict commits on images
		try (Git git = new Git(db)) {
			// First commit
			write(new File(db.getWorkTree(), ".gitattributes"), "");
			git.add().addFilepattern(".gitattributes").call();
			RevCommit firstCommit = git.commit()
					.setMessage("initial commit adding git attribute file")
					.call();

			// Create branch and add an icon Checked_Boxe (enabled_checked)
			createBranch(firstCommit, ENABLE_CHECKED_BRANCH);
			checkoutBranch(ENABLE_CHECKED_BRANCH);
			copy(ENABLED_CHECKED_GIF, ENABLED_CHECKED_GIF, "");
			git.add().addFilepattern(ENABLED_CHECKED_GIF).call();
			git.commit().setMessage("enabled_checked commit").call();

			// Create a second branch from master Unchecked_Boxe
			checkoutBranch(REFS_HEADS_MASTER);
			createBranch(firstCommit, DISABLE_CHECK_BRANCH);
			checkoutBranch(DISABLE_CHECK_BRANCH);
			copy("disabled_checked.gif", ENABLED_CHECKED_GIF, "");
			git.add().addFilepattern(ENABLED_CHECKED_GIF).call();
			disableCheckedCommit = git.commit()
					.setMessage("disabled_checked commit").call();

			// Check that the merge attribute is unset
			assertAddMergeAttributeUndefined(ENABLE_CHECKED_BRANCH,
					ENABLED_CHECKED_GIF);
			assertAddMergeAttributeUndefined(DISABLE_CHECK_BRANCH,
					ENABLED_CHECKED_GIF);

			checkoutBranch(ENABLE_CHECKED_BRANCH);
			// Merge refs/heads/enabled_checked -> refs/heads/disabled_checked
			MergeResult mergeResult = git.merge().include(disableCheckedCommit)
					.call();
			assertEquals(MergeStatus.CONFLICTING, mergeResult.getMergeStatus());

			// Check that the image was not modified (no conflict marker added)
			try (FileInputStream mergeResultFile = new FileInputStream(
					db.getWorkTree().toPath().resolve(ENABLED_CHECKED_GIF)
							.toFile())) {
				assertTrue(contentEquals(
						getClass().getResourceAsStream(ENABLED_CHECKED_GIF),
						mergeResultFile));
			}
		}
	}

	@Test
	public void mergeBinaryFile_UnsetMerge_Conflict()
			throws IllegalStateException,
			IOException, NoHeadException, ConcurrentRefUpdateException,
			CheckoutConflictException, InvalidMergeHeadsException,
			WrongRepositoryStateException, NoMessageException, GitAPIException {

		RevCommit disableCheckedCommit;
		// Set up a git whith conflict commits on images
		try (Git git = new Git(db)) {
			// First commit
			write(new File(db.getWorkTree(), ".gitattributes"), "*.gif -merge");
			git.add().addFilepattern(".gitattributes").call();
			RevCommit firstCommit = git.commit()
					.setMessage("initial commit adding git attribute file")
					.call();

			// Create branch and add an icon Checked_Boxe (enabled_checked)
			createBranch(firstCommit, ENABLE_CHECKED_BRANCH);
			checkoutBranch(ENABLE_CHECKED_BRANCH);
			copy(ENABLED_CHECKED_GIF, ENABLED_CHECKED_GIF, "");
			git.add().addFilepattern(ENABLED_CHECKED_GIF).call();
			git.commit().setMessage("enabled_checked commit").call();

			// Create a second branch from master Unchecked_Boxe
			checkoutBranch(REFS_HEADS_MASTER);
			createBranch(firstCommit, DISABLE_CHECK_BRANCH);
			checkoutBranch(DISABLE_CHECK_BRANCH);
			copy("disabled_checked.gif", ENABLED_CHECKED_GIF, "");
			git.add().addFilepattern(ENABLED_CHECKED_GIF).call();
			disableCheckedCommit = git.commit()
					.setMessage("disabled_checked commit").call();

			// Check that the merge attribute is unset
			assertAddMergeAttributeUnset(ENABLE_CHECKED_BRANCH,
					ENABLED_CHECKED_GIF);
			assertAddMergeAttributeUnset(DISABLE_CHECK_BRANCH,
					ENABLED_CHECKED_GIF);

			checkoutBranch(ENABLE_CHECKED_BRANCH);
			// Merge refs/heads/enabled_checked -> refs/heads/disabled_checked
			MergeResult mergeResult = git.merge().include(disableCheckedCommit)
					.call();
			assertEquals(MergeStatus.CONFLICTING, mergeResult.getMergeStatus());

			// Check that the image was not modified (not conflict marker added)
			try (FileInputStream mergeResultFile = new FileInputStream(
					db.getWorkTree().toPath().resolve(ENABLED_CHECKED_GIF)
							.toFile())) {
				assertTrue(contentEquals(
						getClass().getResourceAsStream(ENABLED_CHECKED_GIF),
						mergeResultFile));
			}
		}
	}

	@Test
	public void mergeBinaryFile_SetMerge_Conflict()
			throws IllegalStateException, IOException, NoHeadException,
			ConcurrentRefUpdateException, CheckoutConflictException,
			InvalidMergeHeadsException, WrongRepositoryStateException,
			NoMessageException, GitAPIException {

		RevCommit disableCheckedCommit;
		// Set up a git whith conflict commits on images
		try (Git git = new Git(db)) {
			// First commit
			write(new File(db.getWorkTree(), ".gitattributes"), "*.gif merge");
			git.add().addFilepattern(".gitattributes").call();
			RevCommit firstCommit = git.commit()
					.setMessage("initial commit adding git attribute file")
					.call();

			// Create branch and add an icon Checked_Boxe (enabled_checked)
			createBranch(firstCommit, ENABLE_CHECKED_BRANCH);
			checkoutBranch(ENABLE_CHECKED_BRANCH);
			copy(ENABLED_CHECKED_GIF, ENABLED_CHECKED_GIF, "");
			git.add().addFilepattern(ENABLED_CHECKED_GIF).call();
			git.commit().setMessage("enabled_checked commit").call();

			// Create a second branch from master Unchecked_Boxe
			checkoutBranch(REFS_HEADS_MASTER);
			createBranch(firstCommit, DISABLE_CHECK_BRANCH);
			checkoutBranch(DISABLE_CHECK_BRANCH);
			copy("disabled_checked.gif", ENABLED_CHECKED_GIF, "");
			git.add().addFilepattern(ENABLED_CHECKED_GIF).call();
			disableCheckedCommit = git.commit()
					.setMessage("disabled_checked commit").call();

			// Check that the merge attribute is set
			assertAddMergeAttributeSet(ENABLE_CHECKED_BRANCH,
					ENABLED_CHECKED_GIF);
			assertAddMergeAttributeSet(DISABLE_CHECK_BRANCH,
					ENABLED_CHECKED_GIF);

			checkoutBranch(ENABLE_CHECKED_BRANCH);
			// Merge refs/heads/enabled_checked -> refs/heads/disabled_checked
			MergeResult mergeResult = git.merge().include(disableCheckedCommit)
					.call();
			assertEquals(MergeStatus.CONFLICTING, mergeResult.getMergeStatus());

			// Check that the image was not modified (not conflict marker added)
			try (FileInputStream mergeResultFile = new FileInputStream(
					db.getWorkTree().toPath().resolve(ENABLED_CHECKED_GIF)
							.toFile())) {
				assertFalse(contentEquals(
						getClass().getResourceAsStream(ENABLED_CHECKED_GIF),
						mergeResultFile));
			}
		}
	}

	/*
	 * Copied from org.apache.commons.io.IOUtils
	 */
	private boolean contentEquals(InputStream input1, InputStream input2)
			throws IOException {
		if (input1 == input2) {
			return true;
		}
		if (!(input1 instanceof BufferedInputStream)) {
			input1 = new BufferedInputStream(input1);
		}
		if (!(input2 instanceof BufferedInputStream)) {
			input2 = new BufferedInputStream(input2);
		}

		int ch = input1.read();
		while (-1 != ch) {
			final int ch2 = input2.read();
			if (ch != ch2) {
				return false;
			}
			ch = input1.read();
		}

		final int ch2 = input2.read();
		return ch2 == -1;
	}

	private void assertAddMergeAttributeUnset(String branch, String fileName)
			throws IllegalStateException, IOException {
		checkoutBranch(branch);

		try (TreeWalk treeWaklEnableChecked = new TreeWalk(db)) {
			treeWaklEnableChecked.addTree(new FileTreeIterator(db));
			treeWaklEnableChecked.setFilter(PathFilter.create(fileName));

			assertTrue(treeWaklEnableChecked.next());
			Attributes attributes = treeWaklEnableChecked.getAttributes();
			Attribute mergeAttribute = attributes.get("merge");
			assertNotNull(mergeAttribute);
			assertEquals(Attribute.State.UNSET, mergeAttribute.getState());
		}
	}

	private void assertAddMergeAttributeSet(String branch, String fileName)
			throws IllegalStateException, IOException {
		checkoutBranch(branch);

		try (TreeWalk treeWaklEnableChecked = new TreeWalk(db)) {
			treeWaklEnableChecked.addTree(new FileTreeIterator(db));
			treeWaklEnableChecked.setFilter(PathFilter.create(fileName));

			assertTrue(treeWaklEnableChecked.next());
			Attributes attributes = treeWaklEnableChecked.getAttributes();
			Attribute mergeAttribute = attributes.get("merge");
			assertNotNull(mergeAttribute);
			assertEquals(Attribute.State.SET, mergeAttribute.getState());
		}
	}

	private void assertAddMergeAttributeUndefined(String branch,
			String fileName) throws IllegalStateException, IOException {
		checkoutBranch(branch);

		try (TreeWalk treeWaklEnableChecked = new TreeWalk(db)) {
			treeWaklEnableChecked.addTree(new FileTreeIterator(db));
			treeWaklEnableChecked.setFilter(PathFilter.create(fileName));

			assertTrue(treeWaklEnableChecked.next());
			Attributes attributes = treeWaklEnableChecked.getAttributes();
			Attribute mergeAttribute = attributes.get("merge");
			assertNull(mergeAttribute);
		}
	}

	private void assertAddMergeAttributeCustom(String branch, String fileName,
			String value) throws IllegalStateException, IOException {
		checkoutBranch(branch);

		try (TreeWalk treeWaklEnableChecked = new TreeWalk(db)) {
			treeWaklEnableChecked.addTree(new FileTreeIterator(db));
			treeWaklEnableChecked.setFilter(PathFilter.create(fileName));

			assertTrue(treeWaklEnableChecked.next());
			Attributes attributes = treeWaklEnableChecked.getAttributes();
			Attribute mergeAttribute = attributes.get("merge");
			assertNotNull(mergeAttribute);
			assertEquals(Attribute.State.CUSTOM, mergeAttribute.getState());
			assertEquals(value, mergeAttribute.getValue());
		}
	}

	private void copy(String resourcePath, String resourceNewName,
			String pathInRepo) throws IOException {
		InputStream input = getClass().getResourceAsStream(resourcePath);
		Files.copy(input, db.getWorkTree().toPath().resolve(pathInRepo)
				.resolve(resourceNewName));
	}

}

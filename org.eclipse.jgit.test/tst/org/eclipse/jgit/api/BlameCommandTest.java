/*
 * Copyright (C) 2011, GitHub Inc.
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
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;

import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.CoreConfig.AutoCRLF;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.Test;

/**
 * Unit tests of {@link BlameCommand}
 */
public class BlameCommandTest extends RepositoryTestCase {

	private static String join(String... lines) {
		StringBuilder joined = new StringBuilder();
		for (String line : lines)
			joined.append(line).append('\n');
		return joined.toString();
	}

	@Test
	public void testSingleRevision() throws Exception {
		Git git = new Git(db);

		String[] content = new String[] { "first", "second", "third" };

		writeTrashFile("file.txt", join(content));
		git.add().addFilepattern("file.txt").call();
		RevCommit commit = git.commit().setMessage("create file").call();

		BlameCommand command = new BlameCommand(db);
		command.setFilePath("file.txt");
		BlameResult lines = command.call();
		assertNotNull(lines);
		assertEquals(3, lines.getResultContents().size());

		for (int i = 0; i < 3; i++) {
			assertEquals(commit, lines.getSourceCommit(i));
			assertEquals(i, lines.getSourceLine(i));
		}
	}

	@Test
	public void testTwoRevisions() throws Exception {
		Git git = new Git(db);

		String[] content1 = new String[] { "first", "second" };
		writeTrashFile("file.txt", join(content1));
		git.add().addFilepattern("file.txt").call();
		RevCommit commit1 = git.commit().setMessage("create file").call();

		String[] content2 = new String[] { "first", "second", "third" };
		writeTrashFile("file.txt", join(content2));
		git.add().addFilepattern("file.txt").call();
		RevCommit commit2 = git.commit().setMessage("create file").call();

		BlameCommand command = new BlameCommand(db);
		command.setFilePath("file.txt");
		BlameResult lines = command.call();
		assertEquals(3, lines.getResultContents().size());

		assertEquals(commit1, lines.getSourceCommit(0));
		assertEquals(0, lines.getSourceLine(0));

		assertEquals(commit1, lines.getSourceCommit(1));
		assertEquals(1, lines.getSourceLine(1));

		assertEquals(commit2, lines.getSourceCommit(2));
		assertEquals(2, lines.getSourceLine(2));
	}

	@Test
	public void testRename() throws Exception {
		testRename("file1.txt", "file2.txt");
	}

	@Test
	public void testRenameInSubDir() throws Exception {
		testRename("subdir/file1.txt", "subdir/file2.txt");
	}

	@Test
	public void testMoveToOtherDir() throws Exception {
		testRename("subdir/file1.txt", "otherdir/file1.txt");
	}

	private void testRename(final String sourcePath, final String destPath)
			throws Exception {
		Git git = new Git(db);

		String[] content1 = new String[] { "a", "b", "c" };
		writeTrashFile(sourcePath, join(content1));
		git.add().addFilepattern(sourcePath).call();
		RevCommit commit1 = git.commit().setMessage("create file").call();

		writeTrashFile(destPath, join(content1));
		git.add().addFilepattern(destPath).call();
		git.rm().addFilepattern(sourcePath).call();
		git.commit().setMessage("moving file").call();

		String[] content2 = new String[] { "a", "b", "c2" };
		writeTrashFile(destPath, join(content2));
		git.add().addFilepattern(destPath).call();
		RevCommit commit3 = git.commit().setMessage("editing file").call();

		BlameCommand command = new BlameCommand(db);
		command.setFollowFileRenames(true);
		command.setFilePath(destPath);
		BlameResult lines = command.call();

		assertEquals(commit1, lines.getSourceCommit(0));
		assertEquals(0, lines.getSourceLine(0));
		assertEquals(sourcePath, lines.getSourcePath(0));

		assertEquals(commit1, lines.getSourceCommit(1));
		assertEquals(1, lines.getSourceLine(1));
		assertEquals(sourcePath, lines.getSourcePath(1));

		assertEquals(commit3, lines.getSourceCommit(2));
		assertEquals(2, lines.getSourceLine(2));
		assertEquals(destPath, lines.getSourcePath(2));
	}

	@Test
	public void testTwoRenames() throws Exception {
		Git git = new Git(db);

		// Commit 1: Add file.txt
		String[] content1 = new String[] { "a" };
		writeTrashFile("file.txt", join(content1));
		git.add().addFilepattern("file.txt").call();
		RevCommit commit1 = git.commit().setMessage("create file").call();

		// Commit 2: Rename to file1.txt
		writeTrashFile("file1.txt", join(content1));
		git.add().addFilepattern("file1.txt").call();
		git.rm().addFilepattern("file.txt").call();
		git.commit().setMessage("moving file").call();

		// Commit 3: Edit file1.txt
		String[] content2 = new String[] { "a", "b" };
		writeTrashFile("file1.txt", join(content2));
		git.add().addFilepattern("file1.txt").call();
		RevCommit commit3 = git.commit().setMessage("editing file").call();

		// Commit 4: Rename to file2.txt
		writeTrashFile("file2.txt", join(content2));
		git.add().addFilepattern("file2.txt").call();
		git.rm().addFilepattern("file1.txt").call();
		git.commit().setMessage("moving file again").call();

		BlameCommand command = new BlameCommand(db);
		command.setFollowFileRenames(true);
		command.setFilePath("file2.txt");
		BlameResult lines = command.call();

		assertEquals(commit1, lines.getSourceCommit(0));
		assertEquals(0, lines.getSourceLine(0));
		assertEquals("file.txt", lines.getSourcePath(0));

		assertEquals(commit3, lines.getSourceCommit(1));
		assertEquals(1, lines.getSourceLine(1));
		assertEquals("file1.txt", lines.getSourcePath(1));
	}

	@Test
	public void testDeleteTrailingLines() throws Exception {
		Git git = new Git(db);

		String[] content1 = new String[] { "a", "b", "c", "d" };
		String[] content2 = new String[] { "a", "b" };

		writeTrashFile("file.txt", join(content2));
		git.add().addFilepattern("file.txt").call();
		RevCommit commit1 = git.commit().setMessage("create file").call();

		writeTrashFile("file.txt", join(content1));
		git.add().addFilepattern("file.txt").call();
		git.commit().setMessage("edit file").call();

		writeTrashFile("file.txt", join(content2));
		git.add().addFilepattern("file.txt").call();
		git.commit().setMessage("edit file").call();

		BlameCommand command = new BlameCommand(db);

		command.setFilePath("file.txt");
		BlameResult lines = command.call();
		assertEquals(content2.length, lines.getResultContents().size());

		assertEquals(commit1, lines.getSourceCommit(0));
		assertEquals(commit1, lines.getSourceCommit(1));

		assertEquals(0, lines.getSourceLine(0));
		assertEquals(1, lines.getSourceLine(1));
	}

	@Test
	public void testDeleteMiddleLines() throws Exception {
		Git git = new Git(db);

		String[] content1 = new String[] { "a", "b", "c", "d", "e" };
		String[] content2 = new String[] { "a", "c", "e" };

		writeTrashFile("file.txt", join(content2));
		git.add().addFilepattern("file.txt").call();
		RevCommit commit1 = git.commit().setMessage("edit file").call();

		writeTrashFile("file.txt", join(content1));
		git.add().addFilepattern("file.txt").call();
		git.commit().setMessage("edit file").call();

		writeTrashFile("file.txt", join(content2));
		git.add().addFilepattern("file.txt").call();
		git.commit().setMessage("edit file").call();

		BlameCommand command = new BlameCommand(db);

		command.setFilePath("file.txt");
		BlameResult lines = command.call();
		assertEquals(content2.length, lines.getResultContents().size());

		assertEquals(commit1, lines.getSourceCommit(0));
		assertEquals(0, lines.getSourceLine(0));

		assertEquals(commit1, lines.getSourceCommit(1));
		assertEquals(1, lines.getSourceLine(1));

		assertEquals(commit1, lines.getSourceCommit(2));
		assertEquals(2, lines.getSourceLine(2));
	}

	@Test
	public void testEditAllLines() throws Exception {
		Git git = new Git(db);

		String[] content1 = new String[] { "a", "1" };
		String[] content2 = new String[] { "b", "2" };

		writeTrashFile("file.txt", join(content1));
		git.add().addFilepattern("file.txt").call();
		git.commit().setMessage("edit file").call();

		writeTrashFile("file.txt", join(content2));
		git.add().addFilepattern("file.txt").call();
		RevCommit commit2 = git.commit().setMessage("create file").call();

		BlameCommand command = new BlameCommand(db);

		command.setFilePath("file.txt");
		BlameResult lines = command.call();
		assertEquals(content2.length, lines.getResultContents().size());
		assertEquals(commit2, lines.getSourceCommit(0));
		assertEquals(commit2, lines.getSourceCommit(1));
	}

	@Test
	public void testMiddleClearAllLines() throws Exception {
		Git git = new Git(db);

		String[] content1 = new String[] { "a", "b", "c" };

		writeTrashFile("file.txt", join(content1));
		git.add().addFilepattern("file.txt").call();
		git.commit().setMessage("edit file").call();

		writeTrashFile("file.txt", "");
		git.add().addFilepattern("file.txt").call();
		git.commit().setMessage("create file").call();

		writeTrashFile("file.txt", join(content1));
		git.add().addFilepattern("file.txt").call();
		RevCommit commit3 = git.commit().setMessage("edit file").call();

		BlameCommand command = new BlameCommand(db);

		command.setFilePath("file.txt");
		BlameResult lines = command.call();
		assertEquals(content1.length, lines.getResultContents().size());
		assertEquals(commit3, lines.getSourceCommit(0));
		assertEquals(commit3, lines.getSourceCommit(1));
		assertEquals(commit3, lines.getSourceCommit(2));
	}

	@Test
	public void testCoreAutoCrlf1() throws Exception {
		testCoreAutoCrlf(AutoCRLF.INPUT, AutoCRLF.FALSE);
	}

	@Test
	public void testCoreAutoCrlf2() throws Exception {
		testCoreAutoCrlf(AutoCRLF.FALSE, AutoCRLF.FALSE);
	}

	@Test
	public void testCoreAutoCrlf3() throws Exception {
		testCoreAutoCrlf(AutoCRLF.INPUT, AutoCRLF.INPUT);
	}

	@Test
	public void testCoreAutoCrlf4() throws Exception {
		testCoreAutoCrlf(AutoCRLF.FALSE, AutoCRLF.INPUT);
	}

	@Test
	public void testCoreAutoCrlf5() throws Exception {
		testCoreAutoCrlf(AutoCRLF.INPUT, AutoCRLF.TRUE);
	}

	private void testCoreAutoCrlf(AutoCRLF modeForCommitting,
			AutoCRLF modeForReset) throws Exception {
		Git git = new Git(db);
		FileBasedConfig config = db.getConfig();
		config.setEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOCRLF, modeForCommitting);
		config.save();

		String joinedCrlf = "a\r\nb\r\nc\r\n";
		File trashFile = writeTrashFile("file.txt", joinedCrlf);
		git.add().addFilepattern("file.txt").call();
		RevCommit commit = git.commit().setMessage("create file").call();

		// re-create file from the repo
		trashFile.delete();
		config.setEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOCRLF, modeForReset);
		config.save();
		git.reset().setMode(ResetType.HARD).call();

		BlameCommand command = new BlameCommand(db);
		command.setFilePath("file.txt");
		BlameResult lines = command.call();

		assertEquals(3, lines.getResultContents().size());
		assertEquals(commit, lines.getSourceCommit(0));
		assertEquals(commit, lines.getSourceCommit(1));
		assertEquals(commit, lines.getSourceCommit(2));
	}
}

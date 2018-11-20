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
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.RawTextComparator;
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
		try (Git git = new Git(db)) {
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
	}

	@Test
	public void testTwoRevisions() throws Exception {
		try (Git git = new Git(db)) {
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

	private void testRename(String sourcePath, String destPath)
			throws Exception {
		try (Git git = new Git(db)) {
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
	}

	@Test
	public void testTwoRenames() throws Exception {
		try (Git git = new Git(db)) {
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
	}

	@Test
	public void testDeleteTrailingLines() throws Exception {
		try (Git git = new Git(db)) {
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
	}

	@Test
	public void testDeleteMiddleLines() throws Exception {
		try (Git git = new Git(db)) {
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
	}

	@Test
	public void testEditAllLines() throws Exception {
		try (Git git = new Git(db)) {
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
	}

	@Test
	public void testMiddleClearAllLines() throws Exception {
		try (Git git = new Git(db)) {
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
		try (Git git = new Git(db)) {
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

	@Test
	public void testConflictingMerge1() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit base = commitFile("file.txt", join("0", "1", "2", "3", "4"),
					"master");

			git.checkout().setName("side").setCreateBranch(true)
					.setStartPoint(base).call();
			RevCommit side = commitFile("file.txt",
					join("0", "1 side", "2", "3 on side", "4"), "side");

			commitFile("file.txt", join("0", "1", "2"), "master");

			checkoutBranch("refs/heads/master");
			git.merge().include(side).call();

			// The merge results in a conflict, which we resolve using mostly the
			// side branch contents. Especially the "4" survives.
			RevCommit merge = commitFile("file.txt",
					join("0", "1 side", "2", "3 resolved", "4"), "master");

			BlameCommand command = new BlameCommand(db);
			command.setFilePath("file.txt");
			BlameResult lines = command.call();

			assertEquals(5, lines.getResultContents().size());
			assertEquals(base, lines.getSourceCommit(0));
			assertEquals(side, lines.getSourceCommit(1));
			assertEquals(base, lines.getSourceCommit(2));
			assertEquals(merge, lines.getSourceCommit(3));
			assertEquals(base, lines.getSourceCommit(4));
		}
	}

	// this test inverts the order of the master and side commit and is
	// otherwise identical to testConflictingMerge1
	@Test
	public void testConflictingMerge2() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit base = commitFile("file.txt", join("0", "1", "2", "3", "4"),
					"master");

			commitFile("file.txt", join("0", "1", "2"), "master");

			git.checkout().setName("side").setCreateBranch(true)
					.setStartPoint(base).call();
			RevCommit side = commitFile("file.txt",
					join("0", "1 side", "2", "3 on side", "4"), "side");

			checkoutBranch("refs/heads/master");
			git.merge().include(side).call();

			// The merge results in a conflict, which we resolve using mostly the
			// side branch contents. Especially the "4" survives.
			RevCommit merge = commitFile("file.txt",
					join("0", "1 side", "2", "3 resolved", "4"), "master");

			BlameCommand command = new BlameCommand(db);
			command.setFilePath("file.txt");
			BlameResult lines = command.call();

			assertEquals(5, lines.getResultContents().size());
			assertEquals(base, lines.getSourceCommit(0));
			assertEquals(side, lines.getSourceCommit(1));
			assertEquals(base, lines.getSourceCommit(2));
			assertEquals(merge, lines.getSourceCommit(3));
			assertEquals(base, lines.getSourceCommit(4));
		}
	}

	@Test
	public void testWhitespaceMerge() throws Exception {
		try (Git git = new Git(db)) {
			RevCommit base = commitFile("file.txt", join("0", "1", "2"), "master");
			RevCommit side = commitFile("file.txt", join("0", "1", "   2 side  "),
					"side");

			checkoutBranch("refs/heads/master");
			git.merge().setFastForward(FastForwardMode.NO_FF).include(side).call();

			// change whitespace, so the merge content is not identical to side, but
			// is the same when ignoring whitespace
			writeTrashFile("file.txt", join("0", "1", "2 side"));
			RevCommit merge = git.commit().setAll(true).setMessage("merge")
					.setAmend(true)
					.call();

			BlameCommand command = new BlameCommand(db);
			command.setFilePath("file.txt")
					.setTextComparator(RawTextComparator.WS_IGNORE_ALL)
					.setStartCommit(merge.getId());
			BlameResult lines = command.call();

			assertEquals(3, lines.getResultContents().size());
			assertEquals(base, lines.getSourceCommit(0));
			assertEquals(base, lines.getSourceCommit(1));
			assertEquals(side, lines.getSourceCommit(2));
		}
	}

	@Test
	public void testBlameWithNulByteInHistory() throws Exception {
		try (Git git = new Git(db)) {
			String[] content1 = { "First line", "Another line" };
			writeTrashFile("file.txt", join(content1));
			git.add().addFilepattern("file.txt").call();
			RevCommit c1 = git.commit().setMessage("create file").call();

			String[] content2 = { "First line", "Second line with NUL >\000<",
					"Another line" };
			assertTrue("Content should contain a NUL byte",
					content2[1].indexOf(0) > 0);
			writeTrashFile("file.txt", join(content2));
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("add line with NUL").call();

			String[] content3 = { "First line", "Second line with NUL >\000<",
					"Third line" };
			writeTrashFile("file.txt", join(content3));
			git.add().addFilepattern("file.txt").call();
			RevCommit c3 = git.commit().setMessage("change third line").call();

			String[] content4 = { "First line", "Second line with NUL >\\000<",
					"Third line" };
			assertTrue("Content should not contain a NUL byte",
					content4[1].indexOf(0) < 0);
			writeTrashFile("file.txt", join(content4));
			git.add().addFilepattern("file.txt").call();
			RevCommit c4 = git.commit().setMessage("fix NUL line").call();

			BlameResult lines = git.blame().setFilePath("file.txt").call();
			assertEquals(3, lines.getResultContents().size());
			assertEquals(c1, lines.getSourceCommit(0));
			assertEquals(c4, lines.getSourceCommit(1));
			assertEquals(c3, lines.getSourceCommit(2));
		}
	}

	@Test
	public void testBlameWithNulByteInTopRevision() throws Exception {
		try (Git git = new Git(db)) {
			String[] content1 = { "First line", "Another line" };
			writeTrashFile("file.txt", join(content1));
			git.add().addFilepattern("file.txt").call();
			RevCommit c1 = git.commit().setMessage("create file").call();

			String[] content2 = { "First line", "Second line with NUL >\000<",
					"Another line" };
			assertTrue("Content should contain a NUL byte",
					content2[1].indexOf(0) > 0);
			writeTrashFile("file.txt", join(content2));
			git.add().addFilepattern("file.txt").call();
			RevCommit c2 = git.commit().setMessage("add line with NUL").call();

			String[] content3 = { "First line", "Second line with NUL >\000<",
					"Third line" };
			writeTrashFile("file.txt", join(content3));
			git.add().addFilepattern("file.txt").call();
			RevCommit c3 = git.commit().setMessage("change third line").call();

			BlameResult lines = git.blame().setFilePath("file.txt").call();
			assertEquals(3, lines.getResultContents().size());
			assertEquals(c1, lines.getSourceCommit(0));
			assertEquals(c2, lines.getSourceCommit(1));
			assertEquals(c3, lines.getSourceCommit(2));
		}
	}

}

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

import java.util.List;

import org.eclipse.jgit.blame.Line;
import org.eclipse.jgit.blame.Revision;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

/**
 * Unit tests of {@link LineHistoryCommand}
 */
public class LineHistoryCommandTest extends RepositoryTestCase {

	private String join(String... lines) {
		StringBuilder joined = new StringBuilder();
		for (String line : lines)
			joined.append(line).append('\n');
		return joined.toString();
	}

	@Test
	public void testSingleRevision() throws Exception {
		Git git = new Git(db);

		String[] lines = new String[] { "first", "second", "third" };

		writeTrashFile("file.txt", join(lines));
		git.add().addFilepattern("file.txt").call();
		RevCommit commit = git.commit().setMessage("create file").call();

		LineHistoryCommand command = new LineHistoryCommand(db);
		command.setFilePath("file.txt");
		List<Revision> revisions = command.call();
		assertNotNull(revisions);
		assertEquals(1, revisions.size());
		Revision first = revisions.get(0);
		assertNotNull(first);

		int count = first.getLineCount();
		assertEquals(count, lines.length);
		for (int i = 0; i < count; i++) {
			Line line = first.getLine(i);
			assertNotNull(line);
			assertEquals(commit, line.getStart());
			assertEquals(1, line.getAge());
			assertEquals(i, line.getNumber(0));
			assertEquals(lines[i], first.getLineContent(db, line.getNumber()));
		}

	}

	@Test
	public void testTwoRevisions() throws Exception {
		Git git = new Git(db);

		String[] lines1 = new String[] { "first", "second" };

		writeTrashFile("file.txt", join(lines1));
		git.add().addFilepattern("file.txt").call();
		RevCommit commit1 = git.commit().setMessage("create file").call();

		String[] lines2 = new String[] { "first", "second", "third" };

		writeTrashFile("file.txt", join(lines2));
		git.add().addFilepattern("file.txt").call();
		RevCommit commit2 = git.commit().setMessage("create file").call();

		LineHistoryCommand command = new LineHistoryCommand(db);
		command.setFilePath("file.txt");
		List<Revision> revisions = command.call();
		assertNotNull(revisions);
		assertEquals(2, revisions.size());

		int number = 0;
		for (Line line : revisions.get(0).getLines()) {
			assertNotNull(line);
			if (number == 2) {
				assertEquals(1, line.getAge());
				assertEquals(commit2, line.getStart());
			} else {
				assertEquals(2, line.getAge());
				assertEquals(commit1, line.getStart());
			}
			number++;
		}
	}

	@Test
	public void testLatest() throws Exception {
		Git git = new Git(db);

		String[] lines1 = new String[] { "a", "b", "c" };

		writeTrashFile("file.txt", join(lines1));
		git.add().addFilepattern("file.txt").call();
		RevCommit commit1 = git.commit().setMessage("create file").call();

		String[] lines2 = new String[] { "a", "b", "c2" };

		writeTrashFile("file.txt", join(lines2));
		git.add().addFilepattern("file.txt").call();
		RevCommit commit2 = git.commit().setMessage("create file").call();

		LineHistoryCommand command = new LineHistoryCommand(db);
		command.setFilePath("file.txt");
		command.setLatestOnly(true);
		List<Revision> revisions = command.call();
		assertNotNull(revisions);
		assertEquals(1, revisions.size());
		Revision revision = revisions.get(0);
		assertNotNull(revision);
		assertEquals(commit2, revision.getCommit());
		assertEquals(3, revision.getLineCount());

		int number = 0;
		for (Line line : revision.getLines()) {
			assertNotNull(line);
			if (number == 2) {
				assertEquals(1, line.getAge());
				assertEquals(commit2, line.getStart());
			} else {
				assertEquals(2, line.getAge());
				assertEquals(commit1, line.getStart());
			}
			assertEquals(lines2[number], revision.getLineContent(db, number));
			number++;
		}
	}

	@Test
	public void testRename() throws Exception {
		Git git = new Git(db);

		String[] lines1 = new String[] { "a", "b", "c" };

		writeTrashFile("file.txt", join(lines1));
		git.add().addFilepattern("file.txt").call();
		RevCommit commit1 = git.commit().setMessage("create file").call();

		writeTrashFile("file1.txt", join(lines1));
		git.add().addFilepattern("file1.txt").call();
		git.rm().addFilepattern("file.txt").call();
		git.commit().setMessage("moving file").call();

		String[] lines2 = new String[] { "a", "b", "c2" };

		writeTrashFile("file1.txt", join(lines2));
		git.add().addFilepattern("file1.txt").call();
		RevCommit commit3 = git.commit().setMessage("editing file").call();

		LineHistoryCommand command = new LineHistoryCommand(db);
		command.setFilePath("file1.txt");
		List<Revision> revisions = command.call();
		assertNotNull(revisions);
		assertEquals(3, revisions.size());
		assertEquals("file.txt", revisions.get(0).getPath());
		assertEquals("file1.txt", revisions.get(1).getPath());
		command.setLatestOnly(false);
		revisions = command.call();
		assertNotNull(revisions);
		assertEquals(3, revisions.size());
		assertEquals("file.txt", revisions.get(0).getPath());
		assertEquals("file1.txt", revisions.get(1).getPath());
		assertEquals("file1.txt", revisions.get(2).getPath());
		Revision revision = revisions.get(revisions.size() - 1);
		assertEquals(commit3, revision.getCommit());
		assertEquals(3, revision.getLineCount());

		int number = 0;
		for (Line line : revision.getLines()) {
			assertNotNull(line);
			if (number == 2) {
				assertEquals(1, line.getAge());
				assertEquals(commit3, line.getStart());
			} else {
				assertEquals(3, line.getAge());
				assertEquals(commit1, line.getStart());
			}
			assertEquals(lines2[number], revision.getLineContent(db, number));
			number++;
		}
	}
}

/*******************************************************************************
 *  Copyright (c) 2011 GitHub Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *    Kevin Sawicki (GitHub Inc.) - initial API and implementation
 *******************************************************************************/
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Set;

import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.lines.Line;
import org.eclipse.jgit.lines.Revision;
import org.eclipse.jgit.lines.RevisionContainer;
import org.junit.Test;

/**
 * Unit tests of {@link LineHistoryCommand}
 *
 * @author Kevin Sawicki (kevin@github.com)
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
		git.commit().setMessage("create file").call();

		LineHistoryCommand command = new LineHistoryCommand(db);
		command.setFilePath("file.txt");
		RevisionContainer revisions = command.call();
		assertNotNull(revisions);
		assertEquals(1, revisions.getRevisionCount());
		Revision first = revisions.getFirst();
		assertNotNull(first);
		assertEquals(first, revisions.getRevision(1));
		assertNotNull(revisions.getLast());
		assertEquals(first, revisions.getLast());

		int count = first.getLineCount();
		assertEquals(count, lines.length);
		for (int i = 0; i < count; i++) {
			Line line = first.getLine(i);
			assertNotNull(line);
			assertEquals(1, line.getStart());
			assertEquals(1, line.getEnd());
			assertEquals(1, line.getAge());
			assertEquals(i, line.getNumber());
			assertEquals(i, line.getNumber(first.getNumber()));
			assertEquals(-1, line.getNumber(2));
			assertEquals(lines[i], line.getContent());
		}

	}

	@Test
	public void testTwoRevisions() throws Exception {
		Git git = new Git(db);

		String[] lines1 = new String[] { "first", "second" };

		writeTrashFile("file.txt", join(lines1));
		git.add().addFilepattern("file.txt").call();
		git.commit().setMessage("create file").call();

		String[] lines2 = new String[] { "first", "second", "third" };

		writeTrashFile("file.txt", join(lines2));
		git.add().addFilepattern("file.txt").call();
		git.commit().setMessage("create file").call();

		LineHistoryCommand command = new LineHistoryCommand(db);
		command.setFilePath("file.txt");
		RevisionContainer revisions = command.call();
		assertNotNull(revisions);
		assertEquals(2, revisions.getRevisionCount());

		Set<Line> sorted = revisions.getSortedLines();
		assertNotNull(sorted);
		assertEquals(3, sorted.size());
		int number = 0;
		for (Line line : sorted) {
			assertNotNull(line);
			if (number == 2) {
				assertEquals(1, line.getAge());
				assertEquals(2, line.getStart());
			} else {
				assertEquals(2, line.getAge());
				assertEquals(1, line.getStart());
			}
			assertEquals(2, line.getEnd());
			number++;
		}

	}
}

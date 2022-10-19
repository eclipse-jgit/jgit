/*
 * Copyright (C) 2019, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.jupiter.api.Test;

public class RebaseTodoFileTest extends RepositoryTestCase {

	private static final String TEST_TODO = "test.todo";

	private void createTodoList(String... lines) throws IOException {
		Path p = Paths.get(db.getDirectory().getAbsolutePath(), TEST_TODO);
		Files.write(p, Arrays.asList(lines));
	}

	@Test
	void testReadTodoFile() throws Exception {
		String[] expected = {"reword " + ObjectId.zeroId().name() + " Foo",
				"# A comment in the todo list",
				"pick " + ObjectId.zeroId().name() + " Foo fie",
				"squash " + ObjectId.zeroId().name() + " F",
				"fixup " + ObjectId.zeroId().name(),
				"edit " + ObjectId.zeroId().name() + " f",
				"edit " + ObjectId.zeroId().name() + ' '};
		createTodoList(expected);
		RebaseTodoFile todo = new RebaseTodoFile(db);
		List<RebaseTodoLine> lines = todo.readRebaseTodo(TEST_TODO, true);
		assertEquals(7, lines.size(), "Expected 7 lines");
		int i = 0;
		for (RebaseTodoLine line : lines) {
			switch (i) {
				case 0:
					assertEquals(RebaseTodoLine.Action.REWORD,
							line.getAction(),
							"Expected REWORD");
					assertEquals(ObjectId.zeroId().abbreviate(40),
							line.getCommit(),
							"Unexpected ID");
					assertEquals("Foo",
							line.getShortMessage(),
							"Unexpected Message");
					break;
				case 1:
					assertEquals(RebaseTodoLine.Action.COMMENT,
							line.getAction(),
							"Expected COMMENT");
					assertEquals("# A comment in the todo list",
							line.getComment(),
							"Unexpected Message");
					break;
				case 2:
					assertEquals(RebaseTodoLine.Action.PICK,
							line.getAction(),
							"Expected PICK");
					assertEquals(ObjectId.zeroId().abbreviate(40),
							line.getCommit(),
							"Unexpected ID");
					assertEquals("Foo fie",
							line.getShortMessage(),
							"Unexpected Message");
					break;
				case 3:
					assertEquals(RebaseTodoLine.Action.SQUASH,
							line.getAction(),
							"Expected SQUASH");
					assertEquals(ObjectId.zeroId().abbreviate(40),
							line.getCommit(),
							"Unexpected ID");
					assertEquals("F", line.getShortMessage(), "Unexpected Message");
					break;
				case 4:
					assertEquals(RebaseTodoLine.Action.FIXUP,
							line.getAction(),
							"Expected FIXUP");
					assertEquals(ObjectId.zeroId().abbreviate(40),
							line.getCommit(),
							"Unexpected ID");
					assertEquals("", line.getShortMessage(), "Unexpected Message");
					break;
				case 5:
					assertEquals(RebaseTodoLine.Action.EDIT,
							line.getAction(),
							"Expected EDIT");
					assertEquals(ObjectId.zeroId().abbreviate(40),
							line.getCommit(),
							"Unexpected ID");
					assertEquals("f", line.getShortMessage(), "Unexpected Message");
					break;
				case 6:
					assertEquals(RebaseTodoLine.Action.EDIT,
							line.getAction(),
							"Expected EDIT");
					assertEquals(ObjectId.zeroId().abbreviate(40),
							line.getCommit(),
							"Unexpected ID");
					assertEquals("", line.getShortMessage(), "Unexpected Message");
					break;
				default:
					fail("Too many lines");
					return;
			}
			i++;
		}
	}
}

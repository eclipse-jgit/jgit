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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.Test;

public class RebaseTodoFileTest extends RepositoryTestCase {

	private static final String TEST_TODO = "test.todo";

	private void createTodoList(String... lines) throws IOException {
		Path p = Paths.get(db.getDirectory().getAbsolutePath(), TEST_TODO);
		Files.write(p, Arrays.asList(lines));
	}

	@Test
	public void testReadTodoFile() throws Exception {
		String[] expected = { "reword " + ObjectId.zeroId().name() + " Foo",
				"# A comment in the todo list",
				"pick " + ObjectId.zeroId().name() + " Foo fie",
				"squash " + ObjectId.zeroId().name() + " F",
				"fixup " + ObjectId.zeroId().name(),
				"edit " + ObjectId.zeroId().name() + " f",
				"edit " + ObjectId.zeroId().name() + ' ' };
		createTodoList(expected);
		RebaseTodoFile todo = new RebaseTodoFile(db);
		List<RebaseTodoLine> lines = todo.readRebaseTodo(TEST_TODO, true);
		assertEquals("Expected 7 lines", 7, lines.size());
		int i = 0;
		for (RebaseTodoLine line : lines) {
			switch (i) {
			case 0:
				assertEquals("Expected REWORD", RebaseTodoLine.Action.REWORD,
						line.getAction());
				assertEquals("Unexpected ID", ObjectId.zeroId().abbreviate(40),
						line.getCommit());
				assertEquals("Unexpected Message", "Foo",
						line.getShortMessage());
				break;
			case 1:
				assertEquals("Expected COMMENT", RebaseTodoLine.Action.COMMENT,
						line.getAction());
				assertEquals("Unexpected Message",
						"# A comment in the todo list",
						line.getComment());
				break;
			case 2:
				assertEquals("Expected PICK", RebaseTodoLine.Action.PICK,
						line.getAction());
				assertEquals("Unexpected ID", ObjectId.zeroId().abbreviate(40),
						line.getCommit());
				assertEquals("Unexpected Message", "Foo fie",
						line.getShortMessage());
				break;
			case 3:
				assertEquals("Expected SQUASH", RebaseTodoLine.Action.SQUASH,
						line.getAction());
				assertEquals("Unexpected ID", ObjectId.zeroId().abbreviate(40),
						line.getCommit());
				assertEquals("Unexpected Message", "F", line.getShortMessage());
				break;
			case 4:
				assertEquals("Expected FIXUP", RebaseTodoLine.Action.FIXUP,
						line.getAction());
				assertEquals("Unexpected ID", ObjectId.zeroId().abbreviate(40),
						line.getCommit());
				assertEquals("Unexpected Message", "", line.getShortMessage());
				break;
			case 5:
				assertEquals("Expected EDIT", RebaseTodoLine.Action.EDIT,
						line.getAction());
				assertEquals("Unexpected ID", ObjectId.zeroId().abbreviate(40),
						line.getCommit());
				assertEquals("Unexpected Message", "f", line.getShortMessage());
				break;
			case 6:
				assertEquals("Expected EDIT", RebaseTodoLine.Action.EDIT,
						line.getAction());
				assertEquals("Unexpected ID", ObjectId.zeroId().abbreviate(40),
						line.getCommit());
				assertEquals("Unexpected Message", "", line.getShortMessage());
				break;
			default:
				fail("Too many lines");
				return;
			}
			i++;
		}
	}
}

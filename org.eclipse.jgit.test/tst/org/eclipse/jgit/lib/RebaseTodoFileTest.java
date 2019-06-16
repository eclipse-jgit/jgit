/*
 * Copyright (C) 2019, Thomas Wolf <thomas.wolf@paranor.ch>
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

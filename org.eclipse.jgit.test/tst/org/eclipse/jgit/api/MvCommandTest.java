/*
 * Copyright (C) 2014, Tomasz Zarna <Tomasz.Zarna@tasktop.com>
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

import org.eclipse.jgit.api.errors.MoveException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.Before;
import org.junit.Test;

public class MvCommandTest extends RepositoryTestCase {

	private Git git;

	private static final String SRC = "tracked-src.txt";

	private static final String UNTRACKED_FILE = "untracked-src.txt";

	private static final String DST = "tracked-dst.txt";

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		writeTrashFile(SRC, "I am tracked");
		writeTrashFile(UNTRACKED_FILE, "I am not tracked");
		git.add().addFilepattern(SRC).call();
		git.commit().setMessage("Initial commit").call();
	}

	@Test
	public void testMoveTrackedFile() throws Exception {
		assertEquals("[tracked-src.txt, mode:100644, content:I am tracked]",
				indexState(CONTENT));
		MvCommand command = git.mv();
		command.setSource(SRC);
		command.setDestination(DST);
		command.call();
		assertEquals("[tracked-dst.txt, mode:100644, content:I am tracked]",
				indexState(CONTENT));
	}

	@Test(expected = MoveException.class)
	public void testMoveUntrackedFile() throws Exception {
		git.mv().setSource(UNTRACKED_FILE).setDestination(DST).call();
	}

	@Test(expected = NoFilepatternException.class)
	public void testNoSourceProvided() throws Exception {
		git.mv().setDestination(DST).call();
	}

	@Test(expected = NoFilepatternException.class)
	public void testNoDestinationProvided() throws Exception {
		git.mv().setSource(SRC).call();
	}

	// TODO From Robin: add a test for renaming a directory too
}

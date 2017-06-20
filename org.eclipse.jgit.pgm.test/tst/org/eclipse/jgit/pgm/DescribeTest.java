/*
 * Copyright (C) 2013, Matthias Sohn <matthias.sohn@sap.com>
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
package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.junit.Before;
import org.junit.Test;

public class DescribeTest extends CLIRepositoryTestCase {

	private Git git;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
	}

	private void initialCommitAndTag() throws Exception {
		git.commit().setMessage("initial commit").call();
		git.tag().setName("v1.0").call();
	}

	private void secondCommit() throws Exception {
		writeTrashFile("greeting", "Hello, world!");
		git.add().addFilepattern("greeting").call();
		git.commit().setMessage("2nd commit").call();
	}

	@Test
	public void testNoHead() throws Exception {
		assertEquals(CLIText.fatalError(CLIText.get().noNamesFound),
				toString(executeUnchecked("git describe")));
	}

	@Test
	public void testHeadNoTag() throws Exception {
		git.commit().setMessage("initial commit").call();
		assertEquals(CLIText.fatalError(CLIText.get().noNamesFound),
				toString(executeUnchecked("git describe")));
	}

	@Test
	public void testDescribeTag() throws Exception {
		initialCommitAndTag();
		assertArrayEquals(new String[] { "v1.0", "" },
				execute("git describe HEAD"));
	}

	@Test
	public void testDescribeCommit() throws Exception {
		initialCommitAndTag();
		secondCommit();
		assertArrayEquals(new String[] { "v1.0-1-g56f6ceb", "" },
				execute("git describe"));
	}

	@Test
	public void testDescribeTagLong() throws Exception {
		initialCommitAndTag();
		assertArrayEquals(new String[] { "v1.0-0-g6fd41be", "" },
				execute("git describe --long HEAD"));
	}

	@Test
	public void testDescribeCommitMatch() throws Exception {
		initialCommitAndTag();
		secondCommit();
		assertArrayEquals(new String[] { "v1.0-1-g56f6ceb", "" },
				execute("git describe --match v1.*"));
	}

	@Test
	public void testDescribeCommitMatch2() throws Exception {
		initialCommitAndTag();
		secondCommit();
		git.tag().setName("v2.0").call();
		assertArrayEquals(new String[] { "v1.0-1-g56f6ceb", "" },
				execute("git describe --match v1.*"));
	}

	@Test
	public void testDescribeCommitMultiMatch() throws Exception {
		initialCommitAndTag();
		secondCommit();
		git.tag().setName("v2.0.0").call();
		git.tag().setName("v2.1.1").call();
		assertArrayEquals("git yields v2.0.0", new String[] { "v2.0.0", "" },
				execute("git describe --match v2.0* --match v2.1.*"));
	}

	@Test
	public void testDescribeCommitNoMatch() throws Exception {
		initialCommitAndTag();
		writeTrashFile("greeting", "Hello, world!");
		secondCommit();
		try {
			execute("git describe --match 1.*");
			fail("git describe should not find any tag matching 1.*");
		} catch (Die e) {
			assertEquals("No names found, cannot describe anything.",
					e.getMessage());
		}
	}

	@Test
	public void testHelpArgumentBeforeUnknown() throws Exception {
		String[] output = execute("git describe -h -XYZ");
		String all = Arrays.toString(output);
		assertTrue("Unexpected help output: " + all,
				all.contains("jgit describe"));
		assertFalse("Unexpected help output: " + all, all.contains("fatal"));
	}

	@Test
	public void testHelpArgumentAfterUnknown() throws Exception {
		String[] output = executeUnchecked("git describe -XYZ -h");
		String all = Arrays.toString(output);
		assertTrue("Unexpected help output: " + all,
				all.contains("jgit describe"));
		assertTrue("Unexpected help output: " + all, all.contains("fatal"));
	}
}

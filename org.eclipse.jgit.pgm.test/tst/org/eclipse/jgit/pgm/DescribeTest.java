/*
 * Copyright (C) 2013, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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

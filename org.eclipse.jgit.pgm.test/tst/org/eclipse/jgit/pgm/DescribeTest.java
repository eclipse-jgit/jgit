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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DescribeTest extends CLIRepositoryTestCase {

	private Git git;

	@Override
	@BeforeEach
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
	void testNoHead() throws Exception {
		assertEquals(CLIText.fatalError(CLIText.get().noNamesFound),
				toString(executeUnchecked("git describe")));
	}

	@Test
	void testHeadNoTag() throws Exception {
		git.commit().setMessage("initial commit").call();
		assertEquals(CLIText.fatalError(CLIText.get().noNamesFound),
				toString(executeUnchecked("git describe")));
	}

	@Test
	void testDescribeTag() throws Exception {
		initialCommitAndTag();
		assertArrayEquals(new String[] { "v1.0", "" },
				execute("git describe HEAD"));
	}

	@Test
	void testDescribeCommit() throws Exception {
		initialCommitAndTag();
		secondCommit();
		assertArrayEquals(new String[] { "v1.0-1-g56f6ceb", "" },
				execute("git describe"));
	}

	@Test
	void testDescribeTagLong() throws Exception {
		initialCommitAndTag();
		assertArrayEquals(new String[] { "v1.0-0-g6fd41be", "" },
				execute("git describe --long HEAD"));
	}

	@Test
	void testDescribeCommitMatch() throws Exception {
		initialCommitAndTag();
		secondCommit();
		assertArrayEquals(new String[] { "v1.0-1-g56f6ceb", "" },
				execute("git describe --match v1.*"));
	}

	@Test
	void testDescribeCommitMatchAbbrev() throws Exception {
		initialCommitAndTag();
		secondCommit();
		assertArrayEquals(new String[] { "v1.0-1-g56f6cebdf3f5", "" },
				execute("git describe --abbrev 12 --match v1.*"));
	}

	@Test
	void testDescribeCommitMatchAbbrevMin() throws Exception {
		initialCommitAndTag();
		secondCommit();
		assertArrayEquals(new String[] { "v1.0-1-g56f6", "" },
				execute("git describe --abbrev -5 --match v1.*"));
	}

	@Test
	void testDescribeCommitMatchAbbrevMax() throws Exception {
		initialCommitAndTag();
		secondCommit();
		assertArrayEquals(new String[] {
				"v1.0-1-g56f6cebdf3f5ceeecd803365abf0996fb1fa006d", "" },
				execute("git describe --abbrev 50 --match v1.*"));
	}

	@Test
	void testDescribeCommitMatch2() throws Exception {
		initialCommitAndTag();
		secondCommit();
		git.tag().setName("v2.0").call();
		assertArrayEquals(new String[] { "v1.0-1-g56f6ceb", "" },
				execute("git describe --match v1.*"));
	}

	@Test
	void testDescribeCommitMultiMatch() throws Exception {
		initialCommitAndTag();
		secondCommit();
		git.tag().setName("v2.0.0").call();
		git.tag().setName("v2.1.1").call();
		assertArrayEquals(new String[] { "v2.0.0", "" },
				execute("git describe --match v2.0* --match v2.1.*"),
				"git yields v2.0.0");
	}

	@Test
	void testDescribeCommitNoMatch() throws Exception {
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
	void testHelpArgumentBeforeUnknown() throws Exception {
		String[] output = execute("git describe -h -XYZ");
		String all = Arrays.toString(output);
		assertTrue(all.contains("jgit describe"),
				"Unexpected help output: " + all);
		assertFalse(all.contains("fatal"), "Unexpected help output: " + all);
	}

	@Test
	void testHelpArgumentAfterUnknown() throws Exception {
		String[] output = executeUnchecked("git describe -XYZ -h");
		String all = Arrays.toString(output);
		assertTrue(all.contains("jgit describe"),
				"Unexpected help output: " + all);
		assertTrue(all.contains("fatal"), "Unexpected help output: " + all);
	}
}

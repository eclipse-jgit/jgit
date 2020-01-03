/*
 * Copyright (C) 2012, 2014 IBM Corporation and others. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class BranchTest extends CLIRepositoryTestCase {
	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();
		}
	}

	@Test
	public void testHelpAfterDelete() throws Exception {
		String err = toString(executeUnchecked("git branch -d"));
		String help = toString(executeUnchecked("git branch -h"));
		String errAndHelp = toString(executeUnchecked("git branch -d -h"));
		assertEquals(CLIText.fatalError(CLIText.get().branchNameRequired), err);
		assertEquals(toString(err, help), errAndHelp);
	}

	@Test
	public void testList() throws Exception {
		assertEquals("* master", toString(execute("git branch")));
		assertEquals("* master 6fd41be initial commit",
				toString(execute("git branch -v")));
	}

	@Test
	public void testListDetached() throws Exception {
		RefUpdate updateRef = db.updateRef(Constants.HEAD, true);
		updateRef.setNewObjectId(db.resolve("6fd41be"));
		updateRef.update();
		assertEquals(
				toString("* (no branch) 6fd41be initial commit",
						"master      6fd41be initial commit"),
				toString(execute("git branch -v")));
	}

	@Test
	public void testListContains() throws Exception {
		try (Git git = new Git(db)) {
			git.branchCreate().setName("initial").call();
			RevCommit second = git.commit().setMessage("second commit")
					.call();
			assertEquals(toString("  initial", "* master"),
					toString(execute("git branch --contains 6fd41be")));
			assertEquals("* master",
					toString(execute("git branch --contains " + second.name())));
		}
	}

	@Test
	public void testExistingBranch() throws Exception {
		assertEquals("fatal: A branch named 'master' already exists.",
				toString(executeUnchecked("git branch master")));
	}

	@Test
	public void testRenameSingleArg() throws Exception {
		try {
			toString(execute("git branch -m"));
			fail("Must die");
		} catch (Die e) {
			// expected, requires argument
		}
		String result = toString(execute("git branch -m slave"));
		assertEquals("", result);
		result = toString(execute("git branch -a"));
		assertEquals("* slave", result);
	}

	@Test
	public void testRenameTwoArgs() throws Exception {
		String result = toString(execute("git branch -m master slave"));
		assertEquals("", result);
		result = toString(execute("git branch -a"));
		assertEquals("* slave", result);
	}

	@Test
	public void testCreate() throws Exception {
		try {
			toString(execute("git branch a b"));
			fail("Must die");
		} catch (Die e) {
			// expected, too many arguments
		}
		String result = toString(execute("git branch second"));
		assertEquals("", result);
		result = toString(execute("git branch"));
		assertEquals(toString("* master", "second"), result);
		result = toString(execute("git branch -v"));
		assertEquals(toString("* master 6fd41be initial commit",
				"second 6fd41be initial commit"), result);
	}

	@Test
	public void testDelete() throws Exception {
		try {
			toString(execute("git branch -d"));
			fail("Must die");
		} catch (Die e) {
			// expected, requires argument
		}
		String result = toString(execute("git branch second"));
		assertEquals("", result);
		result = toString(execute("git branch -d second"));
		assertEquals("", result);
		result = toString(execute("git branch"));
		assertEquals("* master", result);
	}

	@Test
	public void testDeleteMultiple() throws Exception {
		String result = toString(execute("git branch second",
				"git branch third", "git branch fourth"));
		assertEquals("", result);
		result = toString(execute("git branch -d second third fourth"));
		assertEquals("", result);
		result = toString(execute("git branch"));
		assertEquals("* master", result);
	}

	@Test
	public void testDeleteForce() throws Exception {
		try {
			toString(execute("git branch -D"));
			fail("Must die");
		} catch (Die e) {
			// expected, requires argument
		}
		String result = toString(execute("git branch second"));
		assertEquals("", result);
		result = toString(execute("git checkout second"));
		assertEquals("Switched to branch 'second'", result);

		File a = writeTrashFile("a", "a");
		assertTrue(a.exists());
		execute("git add a", "git commit -m 'added a'");

		result = toString(execute("git checkout master"));
		assertEquals("Switched to branch 'master'", result);

		result = toString(execute("git branch"));
		assertEquals(toString("* master", "second"), result);

		try {
			toString(execute("git branch -d second"));
			fail("Must die");
		} catch (Die e) {
			// expected, the current HEAD is on second and not merged to master
		}
		result = toString(execute("git branch -D second"));
		assertEquals("", result);

		result = toString(execute("git branch"));
		assertEquals("* master", result);
	}

	@Test
	public void testDeleteForceMultiple() throws Exception {
		String result = toString(execute("git branch second",
				"git branch third", "git branch fourth"));

		assertEquals("", result);
		result = toString(execute("git checkout second"));
		assertEquals("Switched to branch 'second'", result);

		File a = writeTrashFile("a", "a");
		assertTrue(a.exists());
		execute("git add a", "git commit -m 'added a'");

		result = toString(execute("git checkout master"));
		assertEquals("Switched to branch 'master'", result);

		result = toString(execute("git branch"));
		assertEquals(toString("fourth", "* master", "second", "third"), result);

		try {
			toString(execute("git branch -d second third fourth"));
			fail("Must die");
		} catch (Die e) {
			// expected, the current HEAD is on second and not merged to master
		}
		result = toString(execute("git branch"));
		assertEquals(toString("fourth", "* master", "second", "third"), result);

		result = toString(execute("git branch -D second third fourth"));
		assertEquals("", result);

		result = toString(execute("git branch"));
		assertEquals("* master", result);
	}

	@Test
	public void testCreateFromOldCommit() throws Exception {
		File a = writeTrashFile("a", "a");
		assertTrue(a.exists());
		execute("git add a", "git commit -m 'added a'");
		File b = writeTrashFile("b", "b");
		assertTrue(b.exists());
		execute("git add b", "git commit -m 'added b'");
		String result = toString(execute("git log -n 1 --reverse"));
		String firstCommitId = result.substring("commit ".length(),
				result.indexOf('\n'));

		result = toString(execute("git branch -f second " + firstCommitId));
		assertEquals("", result);

		result = toString(execute("git branch"));
		assertEquals(toString("* master", "second"), result);

		result = toString(execute("git checkout second"));
		assertEquals("Switched to branch 'second'", result);
		assertFalse(b.exists());
	}
}

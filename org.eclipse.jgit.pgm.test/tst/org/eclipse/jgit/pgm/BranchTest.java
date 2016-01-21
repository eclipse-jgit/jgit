/*
 * Copyright (C) 2012, 2014 IBM Corporation and others.
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

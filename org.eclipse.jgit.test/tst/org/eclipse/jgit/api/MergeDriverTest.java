/*******************************************************************************
 * Copyright (C) 2014, Obeo
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
 *******************************************************************************/
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.merge.MergeDriver;
import org.eclipse.jgit.merge.MergeDriverRegistry;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MergeDriverTest extends RepositoryTestCase {
	private Git git;

	private RevCommit branchCommit;

	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);

		writeTrashFile("b.txt", "initial content");
		writeTrashFile("c.txt", "initial content");
		writeTrashFile("d.txt", "initial content");
		git.add().addFilepattern("b.txt").addFilepattern("c.txt").addFilepattern("d.txt").call();
		RevCommit initialCommit = git.commit().setMessage("initial commit")
				.call();

		createBranch(initialCommit, "refs/heads/side");

		writeTrashFile("a.txt", "new ours file");
		writeTrashFile("b.txt", "changed ours content");
		writeTrashFile("d.txt", "changed ours content");
		git.rm().addFilepattern("c.txt").call();
		git.add().addFilepattern("a.txt").addFilepattern("b.txt").addFilepattern("d.txt").call();
		git.commit().setMessage("master").call();

		checkoutBranch("refs/heads/side");

		writeTrashFile("a.txt", "new theirs file");
		writeTrashFile("b.txt", "changed theirs content");
		writeTrashFile("c.txt", "changed theirs content");
		git.rm().addFilepattern("d.txt").call();
		git.add().addFilepattern("a.txt").addFilepattern("b.txt").addFilepattern("c.txt").call();
		branchCommit = git.commit().setMessage("side").call();

		checkoutBranch("refs/heads/master");
	}

	@Override
	@After
	public void tearDown() throws Exception {
		MergeDriverRegistry.clear();
		super.tearDown();
	}

	@Test
	public void testDriverAssociation() {
		MergeDriver failing = new FailingDriver();
		MergeDriver ours = new Ours();
		MergeDriver theirs = new Theirs();

		MergeDriverRegistry.registerDriver(failing);
		MergeDriverRegistry.registerDriver(ours);
		MergeDriverRegistry.registerDriver(theirs);

		// empty registry : null (no failure)
		assertNull(MergeDriverRegistry.findMergeDriver("a.txt"));

		// register a single driver
		String namePattern = "*.txt";
		MergeDriverRegistry.associate(namePattern, failing.getName());
		assertSame(failing, MergeDriverRegistry.findMergeDriver("a.txt"));

		// register driver on existing pattern : override
		MergeDriverRegistry.associate(namePattern, ours.getName());
		assertSame(ours, MergeDriverRegistry.findMergeDriver("a.txt"));

		// asking driver on unmatched pattern : null (no failure)
		assertNull(MergeDriverRegistry.findMergeDriver("a"));

		// registering missing driver on pattern : null (no failure)
		MergeDriverRegistry.associate("abc", "missing");
		assertNull(MergeDriverRegistry.findMergeDriver("abc"));
	}

	@Test
	public void testOursMerge() throws Exception {
		// a.txt: ours added, theirs added : merge takes ours
		// b.txt : ours changed, theirs changed : merge takes ours

		// One side deleted, the other changed (c.txt and d.txt). Considered a
		// "trivial merge" by git, won't call our custom driver... and thus the
		// "changed" side is taken and a conflict marked.
		MergeDriver driver = new Ours();
		MergeDriverRegistry.registerDriver(driver);
		MergeDriverRegistry.associate("*.txt", driver.getName());
		MergeResult result = git.merge().include(branchCommit.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();
		assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());

		assertTrue(new File(db.getWorkTree(), "a.txt").exists());
		assertTrue(new File(db.getWorkTree(), "b.txt").exists());
		assertEquals("new ours file", read("a.txt"));
		assertEquals("changed ours content", read("b.txt"));

		assertEquals(2, result.getConflicts().size());
		assertTrue(result.getConflicts().containsKey("c.txt"));
		assertTrue(result.getConflicts().containsKey("d.txt"));

		assertEquals(RepositoryState.MERGING, db.getRepositoryState());
	}

	@Test
	public void testTheirsMerge() throws Exception {
		// a.txt: ours added, theirs added : merge takes theirs
		// b.txt : ours changed, theirs changed : merge takes theirs

		// One side deleted, the other changed (c.txt and d.txt). Considered a
		// "trivial merge" by git, won't call our custom driver... and thus the
		// "changed" side is taken and a conflict marked.
		MergeDriver driver = new Theirs();
		MergeDriverRegistry.registerDriver(driver);
		MergeDriverRegistry.associate("*.txt", driver.getName());
		MergeResult result = git.merge().include(branchCommit.getId())
				.setStrategy(MergeStrategy.RESOLVE).call();
		assertEquals(MergeStatus.CONFLICTING, result.getMergeStatus());

		assertTrue(new File(db.getWorkTree(), "a.txt").exists());
		assertTrue(new File(db.getWorkTree(), "b.txt").exists());
		assertEquals("new theirs file", read("a.txt"));
		assertEquals("changed theirs content", read("b.txt"));

		assertEquals(2, result.getConflicts().size());
		assertTrue(result.getConflicts().containsKey("c.txt"));
		assertTrue(result.getConflicts().containsKey("d.txt"));

		assertEquals(RepositoryState.MERGING, db.getRepositoryState());
	}

	private static class Theirs implements MergeDriver {
		public boolean merge(Config configuration, InputStream ours,
				InputStream theirs, InputStream base, OutputStream output,
				String[] commitNames) throws IOException {
			// Use their version
			byte[] buffer = new byte[8192];
			int read = theirs.read(buffer);
			while (read > 0) {
				output.write(buffer, 0, read);
				read = theirs.read(buffer);
			}

			// If we've been called, there was a conflict on this file. However,
			// we've resolved it by using "theirs" version, tell the caller that
			// there are no conflicting chunks left.
			return true;
		}

		public String getName() {
			return "theirs";
		}
	}

	private static class Ours implements MergeDriver {
		public boolean merge(Config configuration, InputStream ours,
				InputStream theirs, InputStream base, OutputStream output,
				String[] commitNames) throws IOException {
			// Use our version
			byte[] buffer = new byte[8192];
			int read = ours.read(buffer);
			while (read > 0) {
				output.write(buffer, 0, read);
				read = ours.read(buffer);
			}

			// If we've been called, there was a conflict on this file. However,
			// we've resolved it by using "ours" version, tell the caller that
			// there are no conflicting chunks left.
			return true;
		}

		public String getName() {
			return "ours";
		}
	}

	private static class FailingDriver implements MergeDriver {
		public boolean merge(Config configuration, InputStream ours,
				InputStream theirs, InputStream base, OutputStream output,
				String[] commitNames) throws IOException {
			throw new RuntimeException();
		}

		public String getName() {
			return "failing";
		}
	}
}

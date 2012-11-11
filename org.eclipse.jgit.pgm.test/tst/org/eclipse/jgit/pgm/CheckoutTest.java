/*
 * Copyright (C) 2012, IBM Corporation and others.
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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.junit.Assert;
import org.junit.Test;

public class CheckoutTest extends CLIRepositoryTestCase {

	@Test
	public void testCheckoutSelf() throws Exception {
		new Git(db).commit().setMessage("initial commit").call();

		assertEquals("Already on 'master'", execute("git checkout master"));
	}

	@Test
	public void testCheckoutBranch() throws Exception {
		new Git(db).commit().setMessage("initial commit").call();
		new Git(db).branchCreate().setName("side").call();

		assertEquals("Switched to branch 'side'", execute("git checkout side"));
	}

	@Test
	public void testCheckoutNewBranch() throws Exception {
		new Git(db).commit().setMessage("initial commit").call();

		assertEquals("Switched to a new branch 'side'",
				execute("git checkout -b side"));
	}

	@Test
	public void testCheckoutNonExistingBranch() throws Exception {
		assertEquals(
				"error: pathspec 'side' did not match any file(s) known to git.",
				execute("git checkout side"));
	}

	@Test
	public void testCheckoutNewBranchThatAlreadyExists() throws Exception {
		new Git(db).commit().setMessage("initial commit").call();

		assertEquals("fatal: A branch named 'master' already exists.",
				execute("git checkout -b master"));
	}

	@Test
	public void testCheckoutNewBranchOnBranchToBeBorn() throws Exception {
		assertEquals("fatal: You are on a branch yet to be born",
				execute("git checkout -b side"));
	}

	static private void assertEquals(String expected, String[] actual) {
		Assert.assertEquals(actual[actual.length - 1].equals("") ? 2 : 1,
				actual.length); // ignore last line if empty
		Assert.assertEquals(expected, actual[0]);
	}
}

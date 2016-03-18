/*
 * Copyright (C) 2016, Ned Twigg <ned.twigg@diffplug.com>
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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.junit.Test;

public class UpdateRefTest extends CLIRepositoryTestCase {
	@Test
	public void testUpdateRefUsage() throws Exception {
		execute("git update-ref --help");
	}

	@Test
	public void testUpdateRefMove() throws Exception {
		try (Git git = new Git(db)) {
			// create two commits
			git.commit().setMessage("initial commit").call();
			assertEquals("6fd41be HEAD@{0}: commit (initial): initial commit",
					execute("git reflog")[0]);
			git.commit().setMessage("second commit").call();
			assertEquals("5ac9776 HEAD@{0}: commit: second commit",
					execute("git reflog")[0]);

			// move it backwards
			assertArrayEquals(new String[] { "" },
					execute("git update-ref refs/heads/master 6fd41be"));
			assertEquals("6fd41be master@{0}: ",
					execute("git reflog refs/heads/master")[0]);

			// move it forwards, but with a message this time
			assertArrayEquals(
					 new String[] { "" },
					execute("git update-ref refs/heads/master 5ac9776 -m \"A reason\""));
			assertEquals("5ac9776 master@{0}: A reason",
					execute("git reflog refs/heads/master")[0]);
		}
	}
}

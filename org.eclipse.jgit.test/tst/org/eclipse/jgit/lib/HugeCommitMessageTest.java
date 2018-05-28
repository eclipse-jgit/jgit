/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch>
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

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.junit.Test;

public class HugeCommitMessageTest extends RepositoryTestCase {

	private static final int HUGE_SIZE = Math.max(15 * WindowCacheConfig.MB,
			PackConfig.DEFAULT_BIG_FILE_THRESHOLD + WindowCacheConfig.MB);
	// Larger than the 5MB fallback limit in RevWalk.getCachedBytes(RevObject
	// obj, ObjectLoader ldr), and also larger than the default
	// streamFileThreshold.

	@Test
	public void testHugeCommitMessage() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("foo", "foo");
			git.add().addFilepattern("foo").call();
			WindowCacheConfig wc = new WindowCacheConfig();
			wc.setStreamFileThreshold(HUGE_SIZE + WindowCacheConfig.MB);
			wc.install();
			RevCommit commit = git.commit()
					.setMessage(insanelyHugeCommitMessage()).call();
			Ref master = db.findRef("master");
			List<Ref> actual = git.branchList().setContains(commit.getName())
					.call();
			assertTrue("Should be contained in branch master",
					actual.contains(master));
		}
	}

	private String insanelyHugeCommitMessage() {
		final String oneLine = "012345678901234567890123456789012345678901234567890123456789\n";
		StringBuilder b = new StringBuilder(HUGE_SIZE + oneLine.length());
		// Give the message a real header; otherwise even writing the reflog
		// message may run into troubles because RevCommit.getShortMessage()
		// will return the whole message.
		b.append("An insanely huge commit message\n\n");
		while (b.length() < HUGE_SIZE) {
			b.append(oneLine);
		}
		return b.toString();
	}

}

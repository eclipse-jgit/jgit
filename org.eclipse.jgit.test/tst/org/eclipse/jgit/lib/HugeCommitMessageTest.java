/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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

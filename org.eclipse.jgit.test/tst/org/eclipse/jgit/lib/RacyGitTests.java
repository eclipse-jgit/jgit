/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.time.TimeUtil;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.WorkingTreeOptions;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

public class RacyGitTests extends RepositoryTestCase {

	@Test
	public void testRacyGitDetection() throws Exception {
		// Reset to force creation of index file
		try (Git git = new Git(db)) {
			git.reset().call();
		}

		// wait to ensure that modtimes of the file doesn't match last index
		// file modtime
		fsTick(db.getIndexFile());

		// create two files
		File a = writeToWorkDir("a", "a");
		File b = writeToWorkDir("b", "b");
		TimeUtil.setLastModifiedOf(a.toPath(), b.toPath());
		TimeUtil.setLastModifiedOf(b.toPath(), b.toPath());

		// wait to ensure that file-modTimes and therefore index entry modTime
		// doesn't match the modtime of index-file after next persistance
		fsTick(b);

		// now add both files to the index. No racy git expected
		resetIndex(new FileTreeIterator(db));

		assertEquals(
				"[a, mode:100644, time:t0, length:1, content:a]"
						+ "[b, mode:100644, time:t0, length:1, content:b]",
				indexState(SMUDGE | MOD_TIME | LENGTH | CONTENT));

		// wait to ensure the file 'a' is updated at t1.
		fsTick(db.getIndexFile());

		// Create a racy git situation. This is a situation that the index is
		// updated and then a file is modified within the same tick of the
		// filesystem timestamp resolution. By changing the index file
		// artificially, we create a fake racy situation.
		File updatedA = writeToWorkDir("a", "a2");
		Instant newLastModified = TimeUtil
				.setLastModifiedWithOffset(updatedA.toPath(), 100L);
		resetIndex(new FileTreeIterator(db));
		FS.DETECTED.setLastModified(db.getIndexFile().toPath(),
				newLastModified);

		DirCache dc = db.readDirCache();
		// check index state: although racily clean a should not be reported as
		// being dirty since we forcefully reset the index to match the working
		// tree
		assertEquals(
				"[a, mode:100644, time:t1, smudged, length:0, content:a2]"
						+ "[b, mode:100644, time:t0, length:1, content:b]",
				indexState(SMUDGE | MOD_TIME | LENGTH | CONTENT));

		// compare state of files in working tree with index to check that
		// FileTreeIterator.isModified() works as expected
		FileTreeIterator f = new FileTreeIterator(db.getWorkTree(), db.getFS(),
				db.getConfig().get(WorkingTreeOptions.KEY));
		assertTrue(f.findFile("a"));
		try (ObjectReader reader = db.newObjectReader()) {
			assertFalse(f.isModified(dc.getEntry("a"), false, reader));
		}
	}

	private File writeToWorkDir(String path, String content) throws IOException {
		File f = new File(db.getWorkTree(), path);
		try (FileOutputStream fos = new FileOutputStream(f)) {
			fos.write(content.getBytes(UTF_8));
			return f;
		}
	}
}

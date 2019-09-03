/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.WorkingTreeOptions;
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
		assertTrue(a.setLastModified(b.lastModified()));
		assertTrue(b.setLastModified(b.lastModified()));

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
		long newLastModified = updatedA.lastModified() + 100;
		assertTrue(updatedA.setLastModified(newLastModified));
		resetIndex(new FileTreeIterator(db));
		assertTrue(db.getIndexFile().setLastModified(newLastModified));

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
			fos.write(content.getBytes(Constants.CHARACTER_ENCODING));
			return f;
		}
	}
}

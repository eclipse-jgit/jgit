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

import static java.lang.Long.valueOf;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.TreeSet;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIteratorWithTimeControl;
import org.eclipse.jgit.treewalk.NameConflictTreeWalk;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Test;

public class RacyGitTests extends RepositoryTestCase {
	@Test
	public void testIterator()
			throws IllegalStateException, IOException, InterruptedException {
		TreeSet<Long> modTimes = new TreeSet<>();
		File lastFile = null;
		for (int i = 0; i < 10; i++) {
			lastFile = new File(db.getWorkTree(), "0." + i);
			FileUtils.createNewFile(lastFile);
			if (i == 5)
				fsTick(lastFile);
		}
		modTimes.add(valueOf(fsTick(lastFile)));
		for (int i = 0; i < 10; i++) {
			lastFile = new File(db.getWorkTree(), "1." + i);
			FileUtils.createNewFile(lastFile);
		}
		modTimes.add(valueOf(fsTick(lastFile)));
		for (int i = 0; i < 10; i++) {
			lastFile = new File(db.getWorkTree(), "2." + i);
			FileUtils.createNewFile(lastFile);
			if (i % 4 == 0)
				fsTick(lastFile);
		}
		FileTreeIteratorWithTimeControl fileIt = new FileTreeIteratorWithTimeControl(
				db, modTimes);
		try (NameConflictTreeWalk tw = new NameConflictTreeWalk(db)) {
			tw.addTree(fileIt);
			tw.setRecursive(true);
			FileTreeIterator t;
			long t0 = 0;
			for (int i = 0; i < 10; i++) {
				assertTrue(tw.next());
				t = tw.getTree(0, FileTreeIterator.class);
				if (i == 0) {
					t0 = t.getEntryLastModified();
				} else {
					assertEquals(t0, t.getEntryLastModified());
				}
			}
			long t1 = 0;
			for (int i = 0; i < 10; i++) {
				assertTrue(tw.next());
				t = tw.getTree(0, FileTreeIterator.class);
				if (i == 0) {
					t1 = t.getEntryLastModified();
					assertTrue(t1 > t0);
				} else {
					assertEquals(t1, t.getEntryLastModified());
				}
			}
			long t2 = 0;
			for (int i = 0; i < 10; i++) {
				assertTrue(tw.next());
				t = tw.getTree(0, FileTreeIterator.class);
				if (i == 0) {
					t2 = t.getEntryLastModified();
					assertTrue(t2 > t1);
				} else {
					assertEquals(t2, t.getEntryLastModified());
				}
			}
		}
	}

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
		File a = addToWorkDir("a", "a");
		File b = addToWorkDir("b", "b");
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
		// updated and then a file is modified within a second. By changing the
		// index file artificially, we create a fake racy situation.
		File updatedA = addToWorkDir("a", "a2");
		assertTrue(updatedA.setLastModified(updatedA.lastModified() + 100));
		resetIndex(new FileTreeIterator(db));
		assertTrue(db.getIndexFile()
				.setLastModified(updatedA.lastModified() + 90));

		db.readDirCache();
		// although racily clean a should not be reported as being dirty
		assertEquals(
				"[a, mode:100644, time:t1, smudged, length:0, content:a2]"
						+ "[b, mode:100644, time:t0, length:1, content:b]",
				indexState(SMUDGE | MOD_TIME | LENGTH | CONTENT));
	}

	private File addToWorkDir(String path, String content) throws IOException {
		File f = new File(db.getWorkTree(), path);
		try (FileOutputStream fos = new FileOutputStream(f)) {
			fos.write(content.getBytes(UTF_8));
			return f;
		}
	}
}

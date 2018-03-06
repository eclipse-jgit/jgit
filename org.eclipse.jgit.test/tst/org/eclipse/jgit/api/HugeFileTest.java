/*
 * Copyright (C) 2012, Robin Rosenberg <robin.rosenberg@dewire.com>
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
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class HugeFileTest extends RepositoryTestCase {

	private long t = System.currentTimeMillis();

	private long lastt = t;

	private Git git;

	private void measure(String name) {
		long c = System.currentTimeMillis();
		System.out.println(name + ", dt=" + (c - lastt) / 1000.0 + "s");
		lastt = c;
	}

	@Before
	public void before() {
		git = new Git(db);
	}

	@After
	public void after() {
		if (git != null) {
			git.close();
		}
	}

	@Ignore("Test takes way too long (~10 minutes) to be part of the standard suite")
	@Test
	public void testAddHugeFile() throws Exception {
		measure("Commencing test");
		File file = new File(db.getWorkTree(), "a.txt");
		try (RandomAccessFile rf = new RandomAccessFile(file, "rw")) {
			rf.setLength(4429185024L);
		}
		measure("Created file");

		git.add().addFilepattern("a.txt").call();
		measure("Added file");
		assertEquals(
				"[a.txt, mode:100644, length:134217728, sha1:b8cfba97c2b962a44f080b3ca4e03b3204b6a350]",
				indexState(LENGTH | CONTENT_ID));

		Status status = git.status().call();
		measure("Status after add");
		assertCollectionEquals(Arrays.asList("a.txt"), status.getAdded());
		assertEquals(0, status.getChanged().size());
		assertEquals(0, status.getConflicting().size());
		assertEquals(0, status.getMissing().size());
		assertEquals(0, status.getModified().size());
		assertEquals(0, status.getRemoved().size());
		assertEquals(0, status.getUntracked().size());

		// Does not change anything, but modified timestamp
		try (RandomAccessFile rf = new RandomAccessFile(file, "rw")) {
			rf.write(0);
		}

		status = git.status().call();
		measure("Status after non-modifying update");

		assertCollectionEquals(Arrays.asList("a.txt"), status.getAdded());
		assertEquals(0, status.getChanged().size());
		assertEquals(0, status.getConflicting().size());
		assertEquals(0, status.getMissing().size());
		assertEquals(0, status.getModified().size());
		assertEquals(0, status.getRemoved().size());
		assertEquals(0, status.getUntracked().size());

		// Change something
		try (RandomAccessFile rf = new RandomAccessFile(file, "rw")) {
			rf.write('a');
		}

		status = git.status().call();
		measure("Status after modifying update");

		assertCollectionEquals(Arrays.asList("a.txt"), status.getAdded());
		assertEquals(0, status.getChanged().size());
		assertEquals(0, status.getConflicting().size());
		assertEquals(0, status.getMissing().size());
		assertCollectionEquals(Arrays.asList("a.txt"), status.getModified());
		assertEquals(0, status.getRemoved().size());
		assertEquals(0, status.getUntracked().size());

		// Truncate mod 4G and re-establish equality
		try (RandomAccessFile rf = new RandomAccessFile(file, "rw")) {
			rf.setLength(134217728L);
			rf.write(0);
		}

		status = git.status().call();
		measure("Status after truncating update");

		assertCollectionEquals(Arrays.asList("a.txt"), status.getAdded());
		assertEquals(0, status.getChanged().size());
		assertEquals(0, status.getConflicting().size());
		assertEquals(0, status.getMissing().size());
		assertCollectionEquals(Arrays.asList("a.txt"), status.getModified());
		assertEquals(0, status.getRemoved().size());
		assertEquals(0, status.getUntracked().size());

		// Change something
		try (RandomAccessFile rf = new RandomAccessFile(file, "rw")) {
			rf.write('a');
		}

		status = git.status().call();
		measure("Status after modifying and truncating update");

		assertCollectionEquals(Arrays.asList("a.txt"), status.getAdded());
		assertEquals(0, status.getChanged().size());
		assertEquals(0, status.getConflicting().size());
		assertEquals(0, status.getMissing().size());
		assertCollectionEquals(Arrays.asList("a.txt"), status.getModified());
		assertEquals(0, status.getRemoved().size());
		assertEquals(0, status.getUntracked().size());

		// Truncate to entry length becomes negative int
		try (RandomAccessFile rf = new RandomAccessFile(file, "rw")) {
			rf.setLength(3429185024L);
			rf.write(0);
		}

		git.add().addFilepattern("a.txt").call();
		measure("Added truncated file");
		assertEquals(
				"[a.txt, mode:100644, length:-865782272, sha1:59b3282f8f59f22d953df956ad3511bf2dc660fd]",
				indexState(LENGTH | CONTENT_ID));

		status = git.status().call();
		measure("Status after status on truncated file");

		assertCollectionEquals(Arrays.asList("a.txt"), status.getAdded());
		assertEquals(0, status.getChanged().size());
		assertEquals(0, status.getConflicting().size());
		assertEquals(0, status.getMissing().size());
		assertEquals(0, status.getModified().size());
		assertEquals(0, status.getRemoved().size());
		assertEquals(0, status.getUntracked().size());

		// Change something
		try (RandomAccessFile rf = new RandomAccessFile(file, "rw")) {
			rf.write('a');
		}

		status = git.status().call();
		measure("Status after modifying and truncating update");

		assertCollectionEquals(Arrays.asList("a.txt"), status.getAdded());
		assertEquals(0, status.getChanged().size());
		assertEquals(0, status.getConflicting().size());
		assertEquals(0, status.getMissing().size());
		assertCollectionEquals(Arrays.asList("a.txt"), status.getModified());
		assertEquals(0, status.getRemoved().size());
		assertEquals(0, status.getUntracked().size());

		git.commit().setMessage("make a commit").call();
		measure("After commit");
		status = git.status().call();
		measure("After status after commit");

		assertEquals(0, status.getAdded().size());
		assertEquals(0, status.getChanged().size());
		assertEquals(0, status.getConflicting().size());
		assertEquals(0, status.getMissing().size());
		assertCollectionEquals(Arrays.asList("a.txt"), status.getModified());
		assertEquals(0, status.getRemoved().size());
		assertEquals(0, status.getUntracked().size());

		git.reset().setMode(ResetType.HARD).call();
		measure("After reset --hard");
		assertEquals(
				"[a.txt, mode:100644, length:-865782272, sha1:59b3282f8f59f22d953df956ad3511bf2dc660fd]",
				indexState(LENGTH | CONTENT_ID));

		status = git.status().call();
		measure("Status after hard reset");

		assertEquals(0, status.getAdded().size());
		assertEquals(0, status.getChanged().size());
		assertEquals(0, status.getConflicting().size());
		assertEquals(0, status.getMissing().size());
		assertEquals(0, status.getModified().size());
		assertEquals(0, status.getRemoved().size());
		assertEquals(0, status.getUntracked().size());
	}

	private static void assertCollectionEquals(Collection<?> asList,
			Collection<?> added) {
		assertEquals(asList.toString(), added.toString());
	}

}

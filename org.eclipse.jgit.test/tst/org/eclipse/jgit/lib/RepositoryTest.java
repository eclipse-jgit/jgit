/*
 * Copyright (C) 2015, Andrey Loskutov <loskutov@gmx.de>
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.internal.storage.file.ObjectDirectory;
import org.eclipse.jgit.internal.storage.file.PackFile;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

public class RepositoryTest extends RepositoryTestCase {

	@Test
	public void testIncrementOpen() throws Exception {
		db.incrementOpen();
		assertEquals(2, getOpenCount(db));
		assertFalse(db.isClosed());

		db.close();
		assertEquals(1, getOpenCount(db));
		assertFalse(db.isClosed());

		db.close();
		assertEquals(0, getOpenCount(db));
		assertTrue(db.isClosed());

		db.close();
		assertEquals(0, getOpenCount(db));
		assertTrue(db.isClosed());

		db.incrementOpen();
		assertEquals(1, getOpenCount(db));
		assertFalse(db.isClosed());
	}

	@Test
	public void testCloseForcibly() throws Exception {
		db.incrementOpen();
		assertEquals(2, getOpenCount(db));
		assertFalse(db.isClosed());

		db.closeForcibly();
		assertEquals(0, getOpenCount(db));
		assertTrue(db.isClosed());

		// now test with open pack file
		RevCommit commit1 = commitFile("a", "a", "master");

		// enforce single pack file
		new GC(db).repack();
		ObjectDirectory odb = db.getObjectDatabase();
		assertEquals(1, odb.getPacks().size());
		assertClosedFileDescriptor(odb.getPacks().iterator().next());
		assertEquals(0, getOpenCount(db));

		// open pack file for reading: this does not increment use count
		ObjectReader reader = db.newObjectReader();
		try (RevWalk rw = new RevWalk(reader)) {
			rw.parseAny(commit1.getId());
		}
		assertOpenFileDescriptor(odb.getPacks().iterator().next());
		assertEquals(1, getOpenCount(db));

		// forcibly close repo: this should close open pack files and decrement
		// use count
		db.closeForcibly();
		assertEquals(0, getOpenCount(db));
		assertClosedFileDescriptor(odb.getPacks().iterator().next());
	}

	@Test
	public void testOpenPackFileOnClosedRepo() throws Exception {
		RevCommit commit1 = commitFile("a", "a", "master");

		// enforce single pack file
		new GC(db).repack();
		ObjectDirectory odb = db.getObjectDatabase();
		assertEquals(1, odb.getPacks().size());
		assertClosedFileDescriptor(odb.getPacks().iterator().next());
		assertEquals(1, getOpenCount(db));

		// open pack file for reading: this does not increment use count
		ObjectReader reader = db.newObjectReader();
		try (RevWalk rw = new RevWalk(reader)) {
			rw.parseAny(commit1.getId());
		}
		assertOpenFileDescriptor(odb.getPacks().iterator().next());
		assertEquals(1, getOpenCount(db));

		// close repo: this should close open pack files and decrement use count
		db.close();
		assertEquals(0, getOpenCount(db));
		assertClosedFileDescriptor(odb.getPacks().iterator().next());

		// mistake: open pack file for reading on closed repo
		reader = db.newObjectReader();
		try (RevWalk rw = new RevWalk(reader)) {
			rw.parseAny(commit1.getId());
		}

		// the use count was incremented by pack file on opening
		assertEquals(1, getOpenCount(db));
		assertOpenFileDescriptor(odb.getPacks().iterator().next());

		db.close();

		assertClosedFileDescriptor(odb.getPacks().iterator().next());
	}

	private void assertOpenFileDescriptor(PackFile openPack)
			throws NoSuchFieldException, IllegalAccessException {
		Field field = PackFile.class.getDeclaredField("fd");
		field.setAccessible(true);
		assertNotNull(field.get(openPack));
	}

	private void assertClosedFileDescriptor(PackFile openPack)
			throws NoSuchFieldException, IllegalAccessException {
		Field field = PackFile.class.getDeclaredField("fd");
		field.setAccessible(true);
		assertNull(field.get(openPack));
	}

	int getOpenCount(Repository r) throws Exception {
		Field field = Repository.class.getDeclaredField("useCnt");
		field.setAccessible(true);
		AtomicInteger count = (AtomicInteger) field.get(r);
		return count.get();
	}

}

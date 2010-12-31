/*
 * Copyright (C) 2009, Google Inc.
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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.junit.Test;

public class RepositoryCacheTest extends RepositoryTestCase {
	@Test
	public void testNonBareFileKey() {
		File gitdir = db.getDirectory();
		File parent = gitdir.getParentFile();
		File other = new File(parent, "notagit");
		assertEquals(gitdir, FileKey.exact(gitdir, db.getFS()).getFile());
		assertEquals(parent, FileKey.exact(parent, db.getFS()).getFile());
		assertEquals(other, FileKey.exact(other, db.getFS()).getFile());

		assertEquals(gitdir, FileKey.lenient(gitdir, db.getFS()).getFile());
		assertEquals(gitdir, FileKey.lenient(parent, db.getFS()).getFile());
		assertEquals(other, FileKey.lenient(other, db.getFS()).getFile());
	}

	@Test
	public void testBareFileKey() throws IOException {
		Repository bare = createBareRepository();
		File gitdir = bare.getDirectory();
		File parent = gitdir.getParentFile();
		String name = gitdir.getName();
		assertTrue(name.endsWith(".git"));
		name = name.substring(0, name.length() - 4);

		assertEquals(gitdir, FileKey.exact(gitdir, db.getFS()).getFile());

		assertEquals(gitdir, FileKey.lenient(gitdir, db.getFS()).getFile());
		assertEquals(gitdir, FileKey.lenient(new File(parent, name), db.getFS()).getFile());
	}

	@Test
	public void testFileKeyOpenExisting() throws IOException {
		Repository r;

		r = new FileKey(db.getDirectory(), db.getFS()).open(true);
		assertNotNull(r);
		assertEquals(db.getDirectory(), r.getDirectory());
		r.close();

		r = new FileKey(db.getDirectory(), db.getFS()).open(false);
		assertNotNull(r);
		assertEquals(db.getDirectory(), r.getDirectory());
		r.close();
	}

	@Test
	public void testFileKeyOpenNew() throws IOException {
		final Repository n = createBareRepository();
		final File gitdir = n.getDirectory();
		n.close();
		recursiveDelete(gitdir);
		assertFalse(gitdir.exists());

		try {
			new FileKey(gitdir, db.getFS()).open(true);
			fail("incorrectly opened a non existant repository");
		} catch (RepositoryNotFoundException e) {
			assertEquals("repository not found: " + gitdir, e.getMessage());
		}

		final Repository o = new FileKey(gitdir, db.getFS()).open(false);
		assertNotNull(o);
		assertEquals(gitdir, o.getDirectory());
		assertFalse(gitdir.exists());
	}

	@Test
	public void testCacheRegisterOpen() throws Exception {
		final File dir = db.getDirectory();
		RepositoryCache.register(db);
		assertSame(db, RepositoryCache.open(FileKey.exact(dir, db.getFS())));

		assertEquals(".git", dir.getName());
		final File parent = dir.getParentFile();
		assertSame(db, RepositoryCache.open(FileKey.lenient(parent, db.getFS())));
	}

	@Test
	public void testCacheOpen() throws Exception {
		final FileKey loc = FileKey.exact(db.getDirectory(), db.getFS());
		final Repository d2 = RepositoryCache.open(loc);
		assertNotSame(db, d2);
		assertSame(d2, RepositoryCache.open(FileKey.exact(loc.getFile(), db.getFS())));
		d2.close();
		d2.close();
	}
}

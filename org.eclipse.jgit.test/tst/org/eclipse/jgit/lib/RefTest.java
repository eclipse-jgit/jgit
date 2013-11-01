/*
 * Copyright (C) 2009-2010, Google Inc.
 * Copyright (C) 2009, Robin Rosenberg
 * Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@dewire.com>
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

import static org.eclipse.jgit.junit.Assert.assertEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

import org.eclipse.jgit.lib.Ref.Storage;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.test.resources.SampleDataRepositoryTestCase;
import org.junit.Test;

/**
 * Misc tests for refs. A lot of things are tested elsewhere so not having a
 * test for a ref related method, does not mean it is untested.
 */
public class RefTest extends SampleDataRepositoryTestCase {

	private void writeSymref(String src, String dst) throws IOException {
		RefUpdate u = db.updateRef(src);
		switch (u.link(dst)) {
		case NEW:
		case FORCED:
		case NO_CHANGE:
			break;
		default:
			fail("link " + src + " to " + dst);
		}
	}

	@Test
	public void testReadAllIncludingSymrefs() throws Exception {
		ObjectId masterId = db.resolve("refs/heads/master");
		RefUpdate updateRef = db.updateRef("refs/remotes/origin/master");
		updateRef.setNewObjectId(masterId);
		updateRef.setForceUpdate(true);
		updateRef.update();
		writeSymref("refs/remotes/origin/HEAD",
					"refs/remotes/origin/master");

		ObjectId r = db.resolve("refs/remotes/origin/HEAD");
		assertEquals(masterId, r);

		Map<String, Ref> allRefs = db.getAllRefs();
		Ref refHEAD = allRefs.get("refs/remotes/origin/HEAD");
		assertNotNull(refHEAD);
		assertEquals(masterId, refHEAD.getObjectId());
		assertFalse(refHEAD.isPeeled());
		assertNull(refHEAD.getPeeledObjectId());

		Ref refmaster = allRefs.get("refs/remotes/origin/master");
		assertEquals(masterId, refmaster.getObjectId());
		assertFalse(refmaster.isPeeled());
		assertNull(refmaster.getPeeledObjectId());
	}

	@Test
	public void testReadSymRefToPacked() throws IOException {
		writeSymref("HEAD", "refs/heads/b");
		Ref ref = db.getRef("HEAD");
		assertEquals(Ref.Storage.LOOSE, ref.getStorage());
		assertTrue("is symref", ref.isSymbolic());
		ref = ref.getTarget();
		assertEquals("refs/heads/b", ref.getName());
		assertEquals(Ref.Storage.PACKED, ref.getStorage());
	}

	@Test
	public void testReadSymRefToLoosePacked() throws IOException {
		ObjectId pid = db.resolve("refs/heads/master^");
		RefUpdate updateRef = db.updateRef("refs/heads/master");
		updateRef.setNewObjectId(pid);
		updateRef.setForceUpdate(true);
		Result update = updateRef.update();
		assertEquals(Result.FORCED, update); // internal

		writeSymref("HEAD", "refs/heads/master");
		Ref ref = db.getRef("HEAD");
		assertEquals(Ref.Storage.LOOSE, ref.getStorage());
		ref = ref.getTarget();
		assertEquals("refs/heads/master", ref.getName());
		assertEquals(Ref.Storage.LOOSE, ref.getStorage());
	}

	@Test
	public void testReadLooseRef() throws IOException {
		RefUpdate updateRef = db.updateRef("ref/heads/new");
		updateRef.setNewObjectId(db.resolve("refs/heads/master"));
		Result update = updateRef.update();
		assertEquals(Result.NEW, update);
		Ref ref = db.getRef("ref/heads/new");
		assertEquals(Storage.LOOSE, ref.getStorage());
	}

	/**
	 * Let an "outsider" create a loose ref with the same name as a packed one
	 *
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@Test
	public void testReadLoosePackedRef() throws IOException,
			InterruptedException {
		Ref ref = db.getRef("refs/heads/master");
		assertEquals(Storage.PACKED, ref.getStorage());
		FileOutputStream os = new FileOutputStream(new File(db.getDirectory(),
				"refs/heads/master"));
		os.write(ref.getObjectId().name().getBytes());
		os.write('\n');
		os.close();

		ref = db.getRef("refs/heads/master");
		assertEquals(Storage.LOOSE, ref.getStorage());
	}

	/**
	 * Modify a packed ref using the API. This creates a loose ref too, ie.
	 * LOOSE_PACKED
	 *
	 * @throws IOException
	 */
	@Test
	public void testReadSimplePackedRefSameRepo() throws IOException {
		Ref ref = db.getRef("refs/heads/master");
		ObjectId pid = db.resolve("refs/heads/master^");
		assertEquals(Storage.PACKED, ref.getStorage());
		RefUpdate updateRef = db.updateRef("refs/heads/master");
		updateRef.setNewObjectId(pid);
		updateRef.setForceUpdate(true);
		Result update = updateRef.update();
		assertEquals(Result.FORCED, update);

		ref = db.getRef("refs/heads/master");
		assertEquals(Storage.LOOSE, ref.getStorage());
	}

	@Test
	public void testResolvedNamesBranch() throws IOException {
		Ref ref = db.getRef("a");
		assertEquals("refs/heads/a", ref.getName());
	}

	@Test
	public void testResolvedSymRef() throws IOException {
		Ref ref = db.getRef(Constants.HEAD);
		assertEquals(Constants.HEAD, ref.getName());
		assertTrue("is symbolic ref", ref.isSymbolic());
		assertSame(Ref.Storage.LOOSE, ref.getStorage());

		Ref dst = ref.getTarget();
		assertNotNull("has target", dst);
		assertEquals("refs/heads/master", dst.getName());

		assertSame(dst.getObjectId(), ref.getObjectId());
		assertSame(dst.getPeeledObjectId(), ref.getPeeledObjectId());
		assertEquals(dst.isPeeled(), ref.isPeeled());
	}
}

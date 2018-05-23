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
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

import org.eclipse.jgit.lib.Ref.Storage;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.storage.file.FileBasedConfig;
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

	private void writeNewRef(String name, ObjectId value) throws IOException {
		RefUpdate updateRef = db.updateRef(name);
		updateRef.setNewObjectId(value);
		assertEquals(RefUpdate.Result.NEW, updateRef.update());
	}

	@Test
	public void testRemoteNames() throws Exception {
		FileBasedConfig config = db.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_REMOTE_SECTION,
				"origin", "dummy", true);
		config.setBoolean(ConfigConstants.CONFIG_REMOTE_SECTION,
				"ab/c", "dummy", true);
		config.save();
		assertEquals("[ab/c, origin]",
				new TreeSet<>(db.getRemoteNames()).toString());

		// one-level deep remote branch
		assertEquals("master",
				db.shortenRemoteBranchName("refs/remotes/origin/master"));
		assertEquals("origin", db.getRemoteName("refs/remotes/origin/master"));

		// two-level deep remote branch
		assertEquals("masta/r",
				db.shortenRemoteBranchName("refs/remotes/origin/masta/r"));
		assertEquals("origin", db.getRemoteName("refs/remotes/origin/masta/r"));

		// Remote with slash and one-level deep branch name
		assertEquals("xmaster",
				db.shortenRemoteBranchName("refs/remotes/ab/c/xmaster"));
		assertEquals("ab/c", db.getRemoteName("refs/remotes/ab/c/xmaster"));

		// Remote with slash and two-level deep branch name
		assertEquals("xmasta/r",
				db.shortenRemoteBranchName("refs/remotes/ab/c/xmasta/r"));
		assertEquals("ab/c", db.getRemoteName("refs/remotes/ab/c/xmasta/r"));

		// no such remote
		assertNull(db.getRemoteName("refs/remotes/nosuchremote/x"));
		assertNull(db.shortenRemoteBranchName("refs/remotes/nosuchremote/x"));

		// no such remote too, no branch name either
		assertNull(db.getRemoteName("refs/remotes/abranch"));
		assertNull(db.shortenRemoteBranchName("refs/remotes/abranch"));

		// // local branch
		assertNull(db.getRemoteName("refs/heads/abranch"));
		assertNull(db.shortenRemoteBranchName("refs/heads/abranch"));
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

		List<Ref> allRefs = db.getRefDatabase().getRefs();
		Optional<Ref> refHEAD = allRefs.stream()
				.filter(ref -> ref.getName().equals("refs/remotes/origin/HEAD"))
				.findAny();
		assertTrue(refHEAD.isPresent());
		assertEquals(masterId, refHEAD.get().getObjectId());
		assertFalse(refHEAD.get().isPeeled());
		assertNull(refHEAD.get().getPeeledObjectId());

		Optional<Ref> refmaster = allRefs.stream().filter(
				ref -> ref.getName().equals("refs/remotes/origin/master"))
				.findAny();
		assertTrue(refmaster.isPresent());
		assertEquals(masterId, refmaster.get().getObjectId());
		assertFalse(refmaster.get().isPeeled());
		assertNull(refmaster.get().getPeeledObjectId());
	}

	@Test
	public void testReadSymRefToPacked() throws IOException {
		writeSymref("HEAD", "refs/heads/b");
		Ref ref = db.exactRef("HEAD");
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
		Ref ref = db.exactRef("HEAD");
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
		Ref ref = db.exactRef("ref/heads/new");
		assertEquals(Storage.LOOSE, ref.getStorage());
	}

	@Test
	public void testGetShortRef() throws IOException {
		Ref ref = db.exactRef("refs/heads/master");
		assertEquals("refs/heads/master", ref.getName());
		assertEquals(db.resolve("refs/heads/master"), ref.getObjectId());
	}

	@Test
	public void testGetShortExactRef() throws IOException {
		assertNull(db.getRefDatabase().exactRef("master"));

		Ref ref = db.getRefDatabase().exactRef("HEAD");
		assertEquals("HEAD", ref.getName());
		assertEquals("refs/heads/master", ref.getTarget().getName());
		assertEquals(db.resolve("refs/heads/master"), ref.getObjectId());
	}

	@Test
	public void testRefsUnderRefs() throws IOException {
		ObjectId masterId = db.resolve("refs/heads/master");
		writeNewRef("refs/heads/refs/foo/bar", masterId);

		assertNull(db.getRefDatabase().exactRef("refs/foo/bar"));

		Ref ref = db.findRef("refs/foo/bar");
		assertEquals("refs/heads/refs/foo/bar", ref.getName());
		assertEquals(db.resolve("refs/heads/master"), ref.getObjectId());
	}

	@Test
	public void testAmbiguousRefsUnderRefs() throws IOException {
		ObjectId masterId = db.resolve("refs/heads/master");
		writeNewRef("refs/foo/bar", masterId);
		writeNewRef("refs/heads/refs/foo/bar", masterId);

		Ref exactRef = db.getRefDatabase().exactRef("refs/foo/bar");
		assertEquals("refs/foo/bar", exactRef.getName());
		assertEquals(masterId, exactRef.getObjectId());

		Ref ref = db.findRef("refs/foo/bar");
		assertEquals("refs/foo/bar", ref.getName());
		assertEquals(masterId, ref.getObjectId());
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
		Ref ref = db.exactRef("refs/heads/master");
		assertEquals(Storage.PACKED, ref.getStorage());
		try (FileOutputStream os = new FileOutputStream(
				new File(db.getDirectory(), "refs/heads/master"))) {
			os.write(ref.getObjectId().name().getBytes());
			os.write('\n');
		}

		ref = db.exactRef("refs/heads/master");
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
		Ref ref = db.exactRef("refs/heads/master");
		ObjectId pid = db.resolve("refs/heads/master^");
		assertEquals(Storage.PACKED, ref.getStorage());
		RefUpdate updateRef = db.updateRef("refs/heads/master");
		updateRef.setNewObjectId(pid);
		updateRef.setForceUpdate(true);
		Result update = updateRef.update();
		assertEquals(Result.FORCED, update);

		ref = db.exactRef("refs/heads/master");
		assertEquals(Storage.LOOSE, ref.getStorage());
	}

	@Test
	public void testResolvedNamesBranch() throws IOException {
		Ref ref = db.findRef("a");
		assertEquals("refs/heads/a", ref.getName());
	}

	@Test
	public void testResolvedSymRef() throws IOException {
		Ref ref = db.exactRef(Constants.HEAD);
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

	private static void checkContainsRef(List<Ref> haystack, Ref needle) {
		for (Ref ref : haystack) {
			if (ref.getName().equals(needle.getName()) &&
					ref.getObjectId().equals(needle.getObjectId())) {
				return;
			}
		}
		fail("list " + haystack + " does not contain ref " + needle);
	}

	@Test
	public void testGetRefsByPrefix() throws IOException {
		List<Ref> refs = db.getRefDatabase().getRefsByPrefix("refs/heads/g");
		assertEquals(2, refs.size());
		checkContainsRef(refs, db.exactRef("refs/heads/g"));
		checkContainsRef(refs, db.exactRef("refs/heads/gitlink"));

		refs = db.getRefDatabase().getRefsByPrefix("refs/heads/prefix/");
		assertEquals(1, refs.size());
		checkContainsRef(refs, db.exactRef("refs/heads/prefix/a"));
	}
}

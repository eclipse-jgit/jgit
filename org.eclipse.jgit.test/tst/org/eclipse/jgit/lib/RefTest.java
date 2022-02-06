/*
 * Copyright (C) 2009-2010, Google Inc.
 * Copyright (C) 2009, Robin Rosenberg
 * Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@dewire.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static java.nio.charset.StandardCharsets.UTF_8;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

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
			os.write(ref.getObjectId().name().getBytes(UTF_8));
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

	private static void checkContainsRef(Collection<Ref> haystack, Ref needle) {
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
		testGetRefsByPrefix(
				db.getRefDatabase().getRefsByPrefix("refs/heads/g"));
	}

	@Test
	public void testGetRefsStreamByPrefix() throws IOException {
		testGetRefsByPrefix(
				db.getRefDatabase().getRefsStreamByPrefix("refs/heads/g")
						.collect(Collectors.toUnmodifiableList()));
	}

	private void testGetRefsByPrefix(List<Ref> refs)
			throws IOException {
		assertEquals(2, refs.size());
		checkContainsRef(refs, db.exactRef("refs/heads/g"));
		checkContainsRef(refs, db.exactRef("refs/heads/gitlink"));

		refs = db.getRefDatabase().getRefsByPrefix("refs/heads/prefix/");
		assertEquals(1, refs.size());
		checkContainsRef(refs, db.exactRef("refs/heads/prefix/a"));
	}

	@Test
	public void testGetRefsByPrefixes() throws IOException {
		List<Ref> refs = db.getRefDatabase().getRefsByPrefix();
		assertEquals(0, refs.size());

		refs = db.getRefDatabase().getRefsByPrefix("refs/heads/p",
				"refs/tags/A");
		assertEquals(3, refs.size());
		checkContainsRef(refs, db.exactRef("refs/heads/pa"));
		checkContainsRef(refs, db.exactRef("refs/heads/prefix/a"));
		checkContainsRef(refs, db.exactRef("refs/tags/A"));
	}

	@Test
	public void testGetRefsExcludingPrefix() throws IOException {
		Set<String> exclude = new HashSet<>();
		exclude.add("refs/tags");
		// HEAD + 12 refs/heads are present here.
		List<Ref> refs =
				db.getRefDatabase().getRefsByPrefixWithExclusions(RefDatabase.ALL, exclude);
		assertEquals(13, refs.size());
		checkContainsRef(refs, db.exactRef("HEAD"));
		checkContainsRef(refs, db.exactRef("refs/heads/a"));
		for (Ref notInResult : db.getRefDatabase().getRefsByPrefix("refs/tags")) {
			assertFalse(refs.contains(notInResult));
		}
	}

	@Test
	public void testGetRefsExcludingPrefixes() throws IOException {
		Set<String> exclude = new HashSet<>();
		exclude.add("refs/tags/");
		exclude.add("refs/heads/");
		List<Ref> refs = db.getRefDatabase().getRefsByPrefixWithExclusions(RefDatabase.ALL, exclude);
		assertEquals(1, refs.size());
		checkContainsRef(refs, db.exactRef("HEAD"));
	}

	@Test
	public void testGetRefsExcludingNonExistingPrefixes() throws IOException {
		Set<String> prefixes = new HashSet<>();
		prefixes.add("refs/tags/");
		prefixes.add("refs/heads/");
		prefixes.add("refs/nonexistent/");
		List<Ref> refs = db.getRefDatabase().getRefsByPrefixWithExclusions(RefDatabase.ALL, prefixes);
		assertEquals(1, refs.size());
		checkContainsRef(refs, db.exactRef("HEAD"));
	}

	@Test
	public void testGetRefsWithPrefixExcludingPrefixes() throws IOException {
		Set<String> exclude = new HashSet<>();
		exclude.add("refs/heads/pa");
		String include = "refs/heads/p";
		List<Ref> refs = db.getRefDatabase().getRefsByPrefixWithExclusions(include, exclude);
		assertEquals(1, refs.size());
		checkContainsRef(refs, db.exactRef("refs/heads/prefix/a"));
	}

	@Test
	public void testGetRefsWithPrefixExcludingOverlappingPrefixes() throws IOException {
		Set<String> exclude = new HashSet<>();
		exclude.add("refs/heads/pa");
		exclude.add("refs/heads/");
		exclude.add("refs/heads/p");
		exclude.add("refs/tags/");
		List<Ref> refs = db.getRefDatabase().getRefsByPrefixWithExclusions(RefDatabase.ALL, exclude);
		assertEquals(1, refs.size());
		checkContainsRef(refs, db.exactRef("HEAD"));
	}

	@Test
	public void testResolveTipSha1() throws IOException {
		ObjectId masterId = db.resolve("refs/heads/master");
		Set<Ref> resolved = db.getRefDatabase().getTipsWithSha1(masterId);

		assertEquals(2, resolved.size());
		checkContainsRef(resolved, db.exactRef("refs/heads/master"));
		checkContainsRef(resolved, db.exactRef("HEAD"));

		assertEquals(db.getRefDatabase()
				.getTipsWithSha1(ObjectId.zeroId()).size(), 0);
	}
}

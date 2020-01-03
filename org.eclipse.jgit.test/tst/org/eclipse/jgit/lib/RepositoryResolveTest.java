/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Jonas Fonseca <fonseca@diku.dk>
 * Copyright (C) 2007-2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.test.resources.SampleDataRepositoryTestCase;
import org.junit.Test;

public class RepositoryResolveTest extends SampleDataRepositoryTestCase {

	@Test
	public void testObjectId_existing() throws IOException {
		assertEquals("49322bb17d3acc9146f98c97d078513228bbf3c0",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0").name());
	}

	@Test
	public void testObjectId_nonexisting() throws IOException {
		assertEquals("49322bb17d3acc9146f98c97d078513228bbf3c1",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c1").name());
	}

	@Test
	public void testObjectId_objectid_implicit_firstparent() throws IOException {
		assertEquals("6e1475206e57110fcef4b92320436c1e9872a322",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^").name());
		assertEquals("1203b03dc816ccbb67773f28b3c19318654b0bc8",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^^").name());
		assertEquals("bab66b48f836ed950c99134ef666436fb07a09a0",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^^^").name());
	}

	@Test
	public void testObjectId_objectid_self() throws IOException {
		assertEquals("49322bb17d3acc9146f98c97d078513228bbf3c0",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^0").name());
		assertEquals("49322bb17d3acc9146f98c97d078513228bbf3c0",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^0^0").name());
		assertEquals("49322bb17d3acc9146f98c97d078513228bbf3c0",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^0^0^0").name());
	}

	@Test
	public void testObjectId_objectid_explicit_firstparent() throws IOException {
		assertEquals("6e1475206e57110fcef4b92320436c1e9872a322",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^1").name());
		assertEquals("1203b03dc816ccbb67773f28b3c19318654b0bc8",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^1^1").name());
		assertEquals("bab66b48f836ed950c99134ef666436fb07a09a0",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^1^1^1").name());
	}

	@Test
	public void testObjectId_objectid_explicit_otherparents() throws IOException {
		assertEquals("6e1475206e57110fcef4b92320436c1e9872a322",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^1").name());
		assertEquals("f73b95671f326616d66b2afb3bdfcdbbce110b44",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^2").name());
		assertEquals("d0114ab8ac326bab30e3a657a0397578c5a1af88",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^3").name());
		assertEquals("d0114ab8ac326bab30e3a657a0397578c5a1af88",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^03").name());
	}

	@Test
	public void testObjectId_objectid_invalid_explicit_parent() throws IOException {
		assertEquals("42e4e7c5e507e113ebbb7801b16b52cf867b7ce1",db.resolve("6462e7d8024396b14d7651e2ec11e2bbf07a05c4^1").name());
		assertNull(db.resolve("6462e7d8024396b14d7651e2ec11e2bbf07a05c4^2"));
		assertEquals("42e4e7c5e507e113ebbb7801b16b52cf867b7ce1",db.resolve("42e4e7c5e507e113ebbb7801b16b52cf867b7ce1^0").name());
		assertNull(db.resolve("42e4e7c5e507e113ebbb7801b16b52cf867b7ce1^1"));
		assertNull(db.resolve("42e4e7c5e507e113ebbb7801b16b52cf867b7ce1^2"));
	}

	@Test
	public void testRef_refname() throws IOException {
		assertEquals("49322bb17d3acc9146f98c97d078513228bbf3c0",db.resolve("master^0").name());
		assertEquals("6e1475206e57110fcef4b92320436c1e9872a322",db.resolve("master^").name());
		assertEquals("6e1475206e57110fcef4b92320436c1e9872a322",db.resolve("refs/heads/master^1").name());
	}

	@Test
	public void testDistance() throws IOException {
		assertEquals("49322bb17d3acc9146f98c97d078513228bbf3c0",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0~0").name());
		assertEquals("6e1475206e57110fcef4b92320436c1e9872a322",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0~1").name());
		assertEquals("1203b03dc816ccbb67773f28b3c19318654b0bc8",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0~2").name());
		assertEquals("bab66b48f836ed950c99134ef666436fb07a09a0",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0~3").name());
		assertEquals("bab66b48f836ed950c99134ef666436fb07a09a0",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0~03").name());
		assertEquals("6e1475206e57110fcef4b92320436c1e9872a322",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0~").name());
		assertEquals("1203b03dc816ccbb67773f28b3c19318654b0bc8",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0~~").name());
		assertEquals("bab66b48f836ed950c99134ef666436fb07a09a0",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0~~~").name());
		assertEquals("1203b03dc816ccbb67773f28b3c19318654b0bc8",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0~~1").name());
		assertEquals("1203b03dc816ccbb67773f28b3c19318654b0bc8",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0~~~0").name());
	}

	@Test
	public void testDistance_past_root() throws IOException {
		assertEquals("42e4e7c5e507e113ebbb7801b16b52cf867b7ce1",db.resolve("6462e7d8024396b14d7651e2ec11e2bbf07a05c4~1").name());
		assertNull(db.resolve("6462e7d8024396b14d7651e2ec11e2bbf07a05c4~~"));
		assertNull(db.resolve("6462e7d8024396b14d7651e2ec11e2bbf07a05c4^^"));
		assertNull(db.resolve("6462e7d8024396b14d7651e2ec11e2bbf07a05c4~2"));
		assertNull(db.resolve("6462e7d8024396b14d7651e2ec11e2bbf07a05c4~99"));
		assertNull(db.resolve("42e4e7c5e507e113ebbb7801b16b52cf867b7ce1~~"));
		assertNull(db.resolve("42e4e7c5e507e113ebbb7801b16b52cf867b7ce1^^"));
		assertNull(db.resolve("42e4e7c5e507e113ebbb7801b16b52cf867b7ce1~2"));
		assertNull(db.resolve("42e4e7c5e507e113ebbb7801b16b52cf867b7ce1~99"));
		assertEquals("42e4e7c5e507e113ebbb7801b16b52cf867b7ce1",db.resolve("master~6").name());
		assertNull(db.resolve("master~7"));
		assertNull(db.resolve("master~6~"));
	}

	@Test
	public void testTree() throws IOException {
		assertEquals("6020a3b8d5d636e549ccbd0c53e2764684bb3125",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^{tree}").name());
		assertEquals("02ba32d3649e510002c21651936b7077aa75ffa9",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^^{tree}").name());
	}

	@Test
	public void testHEAD() throws IOException {
		assertEquals("6020a3b8d5d636e549ccbd0c53e2764684bb3125",db.resolve("HEAD^{tree}").name());
	}

	@Test
	public void testDerefCommit() throws IOException {
		assertEquals("49322bb17d3acc9146f98c97d078513228bbf3c0",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^{}").name());
		assertEquals("49322bb17d3acc9146f98c97d078513228bbf3c0",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^{commit}").name());
		// double deref
		assertEquals("6020a3b8d5d636e549ccbd0c53e2764684bb3125",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^{commit}^{tree}").name());
	}

	@Test
	public void testDerefTag() throws IOException {
		assertEquals("17768080a2318cd89bba4c8b87834401e2095703",db.resolve("refs/tags/B").name());
		assertEquals("d86a2aada2f5e7ccf6f11880bfb9ab404e8a8864",db.resolve("refs/tags/B^{commit}").name());
		assertEquals("032c063ce34486359e3ee3d4f9e5c225b9e1a4c2",db.resolve("refs/tags/B10th").name());
		assertEquals("d86a2aada2f5e7ccf6f11880bfb9ab404e8a8864",db.resolve("refs/tags/B10th^{commit}").name());
		assertEquals("d86a2aada2f5e7ccf6f11880bfb9ab404e8a8864",db.resolve("refs/tags/B10th^{}").name());
		assertEquals("d86a2aada2f5e7ccf6f11880bfb9ab404e8a8864",db.resolve("refs/tags/B10th^0").name());
		assertEquals("d86a2aada2f5e7ccf6f11880bfb9ab404e8a8864",db.resolve("refs/tags/B10th~0").name());
		assertEquals("0966a434eb1a025db6b71485ab63a3bfbea520b6",db.resolve("refs/tags/B10th^").name());
		assertEquals("2c349335b7f797072cf729c4f3bb0914ecb6dec9",db.resolve("refs/tags/B10th^^").name());
		assertEquals("0966a434eb1a025db6b71485ab63a3bfbea520b6",db.resolve("refs/tags/B10th^1").name());
		assertEquals("0966a434eb1a025db6b71485ab63a3bfbea520b6",db.resolve("refs/tags/B10th~1").name());
		assertEquals("2c349335b7f797072cf729c4f3bb0914ecb6dec9",db.resolve("refs/tags/B10th~2").name());
		assertEquals("2c349335b7f797072cf729c4f3bb0914ecb6dec9",db.resolve("refs/tags/B10th^~1").name());
	}

	@Test
	public void testDerefBlob() throws IOException {
		assertEquals("fd608fbe625a2b456d9f15c2b1dc41f252057dd7",db.resolve("spearce-gpg-pub^{}").name());
		assertEquals("fd608fbe625a2b456d9f15c2b1dc41f252057dd7",db.resolve("spearce-gpg-pub^{blob}").name());
		assertEquals("fd608fbe625a2b456d9f15c2b1dc41f252057dd7",db.resolve("fd608fbe625a2b456d9f15c2b1dc41f252057dd7^{}").name());
		assertEquals("fd608fbe625a2b456d9f15c2b1dc41f252057dd7",db.resolve("fd608fbe625a2b456d9f15c2b1dc41f252057dd7^{blob}").name());
	}

	@Test
	public void testDerefTree() throws IOException {
		assertEquals("032c063ce34486359e3ee3d4f9e5c225b9e1a4c2",db.resolve("refs/tags/B10th").name());
		assertEquals("856ec208ae6cadac25a6d74f19b12bb27a24fe24",db.resolve("032c063ce34486359e3ee3d4f9e5c225b9e1a4c2^{tree}").name());
		assertEquals("856ec208ae6cadac25a6d74f19b12bb27a24fe24",db.resolve("refs/tags/B10th^{tree}").name());
	}

	@Test
	public void testParseGitDescribeOutput() throws IOException {
		ObjectId exp = db.resolve("b");
		assertEquals(exp, db.resolve("B-g7f82283")); // old style
		assertEquals(exp, db.resolve("B-6-g7f82283")); // new style

		assertEquals(exp, db.resolve("B-6-g7f82283^0"));
		assertEquals(exp, db.resolve("B-6-g7f82283^{commit}"));

		try {
			db.resolve("B-6-g7f82283^{blob}");
			fail("expected IncorrectObjectTypeException");
		} catch (IncorrectObjectTypeException badType) {
			// Expected
		}

		assertEquals(db.resolve("b^1"), db.resolve("B-6-g7f82283^1"));
		assertEquals(db.resolve("b~2"), db.resolve("B-6-g7f82283~2"));
	}

	@Test
	public void testParseNonGitDescribe() throws IOException {
		ObjectId id = id("49322bb17d3acc9146f98c97d078513228bbf3c0");
		RefUpdate ru = db.updateRef("refs/heads/foo-g032c");
		ru.setNewObjectId(id);
		assertSame(RefUpdate.Result.NEW, ru.update());

		assertEquals(id, db.resolve("refs/heads/foo-g032c"));
		assertEquals(id, db.resolve("foo-g032c"));
		assertNull(db.resolve("foo-g032"));
		assertNull(db.resolve("foo-g03"));
		assertNull(db.resolve("foo-g0"));
		assertNull(db.resolve("foo-g"));

		ru = db.updateRef("refs/heads/foo-g032c-dev");
		ru.setNewObjectId(id);
		assertSame(RefUpdate.Result.NEW, ru.update());

		assertEquals(id, db.resolve("refs/heads/foo-g032c-dev"));
		assertEquals(id, db.resolve("foo-g032c-dev"));
	}

	@Test
	public void testParseLookupPath() throws IOException {
		ObjectId b2_txt = id("10da5895682013006950e7da534b705252b03be6");
		ObjectId b3_b2_txt = id("e6bfff5c1d0f0ecd501552b43a1e13d8008abc31");
		ObjectId b_root = id("acd0220f06f7e4db50ea5ba242f0dfed297b27af");
		ObjectId master_txt = id("82b1d08466e9505f8666b778744f9a3471a70c81");

		assertEquals(b2_txt, db.resolve("b:b/b2.txt"));
		assertEquals(b_root, db.resolve("b:"));
		assertEquals(id("6020a3b8d5d636e549ccbd0c53e2764684bb3125"),
				db.resolve("master:"));
		assertEquals(id("10da5895682013006950e7da534b705252b03be6"),
				db.resolve("master:b/b2.txt"));
		assertEquals(master_txt, db.resolve(":master.txt"));
		assertEquals(b3_b2_txt, db.resolve("b~3:b/b2.txt"));

		assertNull("no FOO", db.resolve("b:FOO"));
		assertNull("no b/FOO", db.resolve("b:b/FOO"));
		assertNull("no b/FOO", db.resolve(":b/FOO"));
		assertNull("no not-a-branch:", db.resolve("not-a-branch:"));
	}

	@Test
	public void resolveExprSimple() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file.txt", "content");
			git.add().addFilepattern("file.txt").call();
			git.commit().setMessage("create file").call();
			assertEquals("master", db.simplify("master"));
			assertEquals("refs/heads/master", db.simplify("refs/heads/master"));
			assertEquals("HEAD", db.simplify("HEAD"));
		}
	}

	@Test
	public void resolveUpstream() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file.txt", "content");
			git.add().addFilepattern("file.txt").call();
			RevCommit c1 = git.commit().setMessage("create file").call();
			writeTrashFile("file2.txt", "content");
			RefUpdate updateRemoteRef = db.updateRef("refs/remotes/origin/main");
			updateRemoteRef.setNewObjectId(c1);
			updateRemoteRef.update();
			db.getConfig().setString("branch", "master", "remote", "origin");
			db.getConfig()
					.setString("branch", "master", "merge", "refs/heads/main");
			db.getConfig().setString("remote", "origin", "url",
					"git://example.com/here");
			db.getConfig().setString("remote", "origin", "fetch",
					"+refs/heads/*:refs/remotes/origin/*");
			git.add().addFilepattern("file2.txt").call();
			git.commit().setMessage("create file").call();
			assertEquals("refs/remotes/origin/main", db.simplify("@{upstream}"));
		}
	}

	@Test
	public void invalidNames() throws AmbiguousObjectException, IOException {
		assertTrue(Repository.isValidRefName("x/a"));
		assertTrue(Repository.isValidRefName("x/a.b"));
		assertTrue(Repository.isValidRefName("x/a@b"));
		assertTrue(Repository.isValidRefName("x/a@b{x}"));
		assertTrue(Repository.isValidRefName("x/a/b"));
		assertTrue(Repository.isValidRefName("x/a]b")); // odd, yes..
		assertTrue(Repository.isValidRefName("x/\u00a0")); // unicode is fine,
															// even hard space
		assertFalse(Repository.isValidRefName("x/.a"));
		assertFalse(Repository.isValidRefName("x/a."));
		assertFalse(Repository.isValidRefName("x/a..b"));
		assertFalse(Repository.isValidRefName("x//a"));
		assertFalse(Repository.isValidRefName("x/a/"));
		assertFalse(Repository.isValidRefName("x/a//b"));
		assertFalse(Repository.isValidRefName("x/a[b"));
		assertFalse(Repository.isValidRefName("x/a^b"));
		assertFalse(Repository.isValidRefName("x/a*b"));
		assertFalse(Repository.isValidRefName("x/a?b"));
		assertFalse(Repository.isValidRefName("x/a~1"));
		assertFalse(Repository.isValidRefName("x/a\\b"));
		assertFalse(Repository.isValidRefName("x/a\u0000"));

		db.resolve("x/a@");

		assertUnparseable(".");
		assertUnparseable("x@{3");
		assertUnparseable("x[b");
		assertUnparseable("x y");
		assertUnparseable("x.");
		assertUnparseable(".x");
		assertUnparseable("a..b");
		assertUnparseable("x\\b");
		assertUnparseable("a~b");
		assertUnparseable("a^b");
		assertUnparseable("a\u0000");
	}

	private void assertUnparseable(String s) throws AmbiguousObjectException,
			IOException {
		try {
			db.resolve(s);
			fail("'" + s + "' should be unparseable");
		} catch (RevisionSyntaxException e) {
			// good
		}
	}

	private static ObjectId id(String name) {
		return ObjectId.fromString(name);
	}
}

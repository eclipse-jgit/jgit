/*
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.test.resources.SampleDataRepositoryTestCase;
import org.junit.Test;

@SuppressWarnings("deprecation")
public class T0002_TreeTest extends SampleDataRepositoryTestCase {
	private static final ObjectId SOME_FAKE_ID = ObjectId.fromString(
			"0123456789abcdef0123456789abcdef01234567");

	private static int compareNamesUsingSpecialCompare(String a, String b)
			throws UnsupportedEncodingException {
		char lasta = '\0';
		byte[] abytes;
		if (a.length() > 0 && a.charAt(a.length()-1) == '/') {
			lasta = '/';
			a = a.substring(0, a.length() - 1);
		}
		abytes = a.getBytes("ISO-8859-1");
		char lastb = '\0';
		byte[] bbytes;
		if (b.length() > 0 && b.charAt(b.length()-1) == '/') {
			lastb = '/';
			b = b.substring(0, b.length() - 1);
		}
		bbytes = b.getBytes("ISO-8859-1");
		return Tree.compareNames(abytes, bbytes, lasta, lastb);
	}

	@Test
	public void test000_sort_01() throws UnsupportedEncodingException {
		assertEquals(0, compareNamesUsingSpecialCompare("a","a"));
	}

	@Test
	public void test000_sort_02() throws UnsupportedEncodingException {
		assertEquals(-1, compareNamesUsingSpecialCompare("a","b"));
		assertEquals(1, compareNamesUsingSpecialCompare("b","a"));
	}

	@Test
	public void test000_sort_03() throws UnsupportedEncodingException {
		assertEquals(1, compareNamesUsingSpecialCompare("a:","a"));
		assertEquals(1, compareNamesUsingSpecialCompare("a/","a"));
		assertEquals(-1, compareNamesUsingSpecialCompare("a","a/"));
		assertEquals(-1, compareNamesUsingSpecialCompare("a","a:"));
		assertEquals(1, compareNamesUsingSpecialCompare("a:","a/"));
		assertEquals(-1, compareNamesUsingSpecialCompare("a/","a:"));
	}

	@Test
	public void test000_sort_04() throws UnsupportedEncodingException {
		assertEquals(-1, compareNamesUsingSpecialCompare("a.a","a/a"));
		assertEquals(1, compareNamesUsingSpecialCompare("a/a","a.a"));
	}

	@Test
	public void test000_sort_05() throws UnsupportedEncodingException {
		assertEquals(-1, compareNamesUsingSpecialCompare("a.","a/"));
		assertEquals(1, compareNamesUsingSpecialCompare("a/","a."));

	}

	@Test
	public void test001_createEmpty() throws IOException {
		final Tree t = new Tree(db);
		assertTrue("isLoaded", t.isLoaded());
		assertTrue("isModified", t.isModified());
		assertTrue("no parent", t.getParent() == null);
		assertTrue("isRoot", t.isRoot());
		assertTrue("no name", t.getName() == null);
		assertTrue("no nameUTF8", t.getNameUTF8() == null);
		assertTrue("has entries array", t.members() != null);
		assertEquals("entries is empty", 0, t.members().length);
		assertEquals("full name is empty", "", t.getFullName());
		assertTrue("no id", t.getId() == null);
		assertTrue("database is r", t.getRepository() == db);
		assertTrue("no foo child", t.findTreeMember("foo") == null);
		assertTrue("no foo child", t.findBlobMember("foo") == null);
	}

	@Test
	public void test002_addFile() throws IOException {
		final Tree t = new Tree(db);
		t.setId(SOME_FAKE_ID);
		assertTrue("has id", t.getId() != null);
		assertFalse("not modified", t.isModified());

		final String n = "bob";
		final FileTreeEntry f = t.addFile(n);
		assertNotNull("have file", f);
		assertEquals("name matches", n, f.getName());
		assertEquals("name matches", f.getName(), new String(f.getNameUTF8(),
				"UTF-8"));
		assertEquals("full name matches", n, f.getFullName());
		assertTrue("no id", f.getId() == null);
		assertTrue("is modified", t.isModified());
		assertTrue("has no id", t.getId() == null);
		assertTrue("found bob", t.findBlobMember(f.getName()) == f);

		final TreeEntry[] i = t.members();
		assertNotNull("members array not null", i);
		assertTrue("iterator is not empty", i != null && i.length > 0);
		assertTrue("iterator returns file", i != null && i[0] == f);
		assertTrue("iterator is empty", i != null && i.length == 1);
	}

	@Test
	public void test004_addTree() throws IOException {
		final Tree t = new Tree(db);
		t.setId(SOME_FAKE_ID);
		assertTrue("has id", t.getId() != null);
		assertFalse("not modified", t.isModified());

		final String n = "bob";
		final Tree f = t.addTree(n);
		assertNotNull("have tree", f);
		assertEquals("name matches", n, f.getName());
		assertEquals("name matches", f.getName(), new String(f.getNameUTF8(),
				"UTF-8"));
		assertEquals("full name matches", n, f.getFullName());
		assertTrue("no id", f.getId() == null);
		assertTrue("parent matches", f.getParent() == t);
		assertTrue("repository matches", f.getRepository() == db);
		assertTrue("isLoaded", f.isLoaded());
		assertFalse("has items", f.members().length > 0);
		assertFalse("is root", f.isRoot());
		assertTrue("parent is modified", t.isModified());
		assertTrue("parent has no id", t.getId() == null);
		assertTrue("found bob child", t.findTreeMember(f.getName()) == f);

		final TreeEntry[] i = t.members();
		assertTrue("iterator is not empty", i.length > 0);
		assertTrue("iterator returns file", i[0] == f);
		assertEquals("iterator is empty", 1, i.length);
	}

	@Test
	public void test005_addRecursiveFile() throws IOException {
		final Tree t = new Tree(db);
		final FileTreeEntry f = t.addFile("a/b/c");
		assertNotNull("created f", f);
		assertEquals("c", f.getName());
		assertEquals("b", f.getParent().getName());
		assertEquals("a", f.getParent().getParent().getName());
		assertTrue("t is great-grandparent", t == f.getParent().getParent()
				.getParent());
	}

	@Test
	public void test005_addRecursiveTree() throws IOException {
		final Tree t = new Tree(db);
		final Tree f = t.addTree("a/b/c");
		assertNotNull("created f", f);
		assertEquals("c", f.getName());
		assertEquals("b", f.getParent().getName());
		assertEquals("a", f.getParent().getParent().getName());
		assertTrue("t is great-grandparent", t == f.getParent().getParent()
				.getParent());
	}

	@Test
	public void test006_addDeepTree() throws IOException {
		final Tree t = new Tree(db);

		final Tree e = t.addTree("e");
		assertNotNull("have e", e);
		assertTrue("e.parent == t", e.getParent() == t);
		final Tree f = t.addTree("f");
		assertNotNull("have f", f);
		assertTrue("f.parent == t", f.getParent() == t);
		final Tree g = f.addTree("g");
		assertNotNull("have g", g);
		assertTrue("g.parent == f", g.getParent() == f);
		final Tree h = g.addTree("h");
		assertNotNull("have h", h);
		assertTrue("h.parent = g", h.getParent() == g);

		h.setId(SOME_FAKE_ID);
		assertTrue("h not modified", !h.isModified());
		g.setId(SOME_FAKE_ID);
		assertTrue("g not modified", !g.isModified());
		f.setId(SOME_FAKE_ID);
		assertTrue("f not modified", !f.isModified());
		e.setId(SOME_FAKE_ID);
		assertTrue("e not modified", !e.isModified());
		t.setId(SOME_FAKE_ID);
		assertTrue("t not modified.", !t.isModified());

		assertEquals("full path of h ok", "f/g/h", h.getFullName());
		assertTrue("Can find h", t.findTreeMember(h.getFullName()) == h);
		assertTrue("Can't find f/z", t.findBlobMember("f/z") == null);
		assertTrue("Can't find y/z", t.findBlobMember("y/z") == null);

		final FileTreeEntry i = h.addFile("i");
		assertNotNull(i);
		assertEquals("full path of i ok", "f/g/h/i", i.getFullName());
		assertTrue("Can find i", t.findBlobMember(i.getFullName()) == i);
		assertTrue("h modified", h.isModified());
		assertTrue("g modified", g.isModified());
		assertTrue("f modified", f.isModified());
		assertTrue("e not modified", !e.isModified());
		assertTrue("t modified", t.isModified());

		assertTrue("h no id", h.getId() == null);
		assertTrue("g no id", g.getId() == null);
		assertTrue("f no id", f.getId() == null);
		assertTrue("e has id", e.getId() != null);
		assertTrue("t no id", t.getId() == null);
	}

	@Test
	public void test007_manyFileLookup() throws IOException {
		final Tree t = new Tree(db);
		final List<FileTreeEntry> files = new ArrayList<FileTreeEntry>(26 * 26);
		for (char level1 = 'a'; level1 <= 'z'; level1++) {
			for (char level2 = 'a'; level2 <= 'z'; level2++) {
				final String n = "." + level1 + level2 + "9";
				final FileTreeEntry f = t.addFile(n);
				assertNotNull("File " + n + " added.", f);
				assertEquals(n, f.getName());
				files.add(f);
			}
		}
		assertEquals(files.size(), t.memberCount());
		final TreeEntry[] ents = t.members();
		assertNotNull(ents);
		assertEquals(files.size(), ents.length);
		for (int k = 0; k < ents.length; k++) {
			assertTrue("File " + files.get(k).getName()
					+ " is at " + k + ".", files.get(k) == ents[k]);
		}
	}

	@Test
	public void test008_SubtreeInternalSorting() throws IOException {
		final Tree t = new Tree(db);
		final FileTreeEntry e0 = t.addFile("a-b");
		final FileTreeEntry e1 = t.addFile("a-");
		final FileTreeEntry e2 = t.addFile("a=b");
		final Tree e3 = t.addTree("a");
		final FileTreeEntry e4 = t.addFile("a=");

		final TreeEntry[] ents = t.members();
		assertSame(e1, ents[0]);
		assertSame(e0, ents[1]);
		assertSame(e3, ents[2]);
		assertSame(e4, ents[3]);
		assertSame(e2, ents[4]);
	}

	@Test
	public void test009_SymlinkAndGitlink() throws IOException {
		final Tree symlinkTree = mapTree("symlink");
		assertTrue("Symlink entry exists", symlinkTree.existsBlob("symlink.txt"));
		final Tree gitlinkTree = mapTree("gitlink");
		assertTrue("Gitlink entry exists", gitlinkTree.existsBlob("submodule"));
	}

	private Tree mapTree(String name) throws IOException {
		ObjectId id = db.resolve(name + "^{tree}");
		return new Tree(db, id, db.open(id).getCachedBytes());
	}
}

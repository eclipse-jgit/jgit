/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.notes;

import java.io.IOException;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.RawParseUtils;

public class NoteMapTest extends RepositoryTestCase {
	private TestRepository<Repository> tr;

	private ObjectReader reader;

	private ObjectInserter inserter;

	@Override
	protected void setUp() throws Exception {
		super.setUp();

		tr = new TestRepository<Repository>(db);
		reader = db.newObjectReader();
		inserter = db.newObjectInserter();
	}

	@Override
	protected void tearDown() throws Exception {
		reader.release();
		inserter.release();
		super.tearDown();
	}

	public void testReadFlatTwoNotes() throws Exception {
		RevBlob a = tr.blob("a");
		RevBlob b = tr.blob("b");
		RevBlob data1 = tr.blob("data1");
		RevBlob data2 = tr.blob("data2");

		RevCommit r = tr.commit() //
				.add(a.name(), data1) //
				.add(b.name(), data2) //
				.create();
		tr.parseBody(r);

		NoteMap map = NoteMap.read(reader, r);
		assertNotNull("have map", map);

		assertTrue("has note for a", map.contains(a));
		assertTrue("has note for b", map.contains(b));
		assertEquals(data1, map.get(a));
		assertEquals(data2, map.get(b));

		assertFalse("no note for data1", map.contains(data1));
		assertNull("no note for data1", map.get(data1));
	}

	public void testReadFanout2_38() throws Exception {
		RevBlob a = tr.blob("a");
		RevBlob b = tr.blob("b");
		RevBlob data1 = tr.blob("data1");
		RevBlob data2 = tr.blob("data2");

		RevCommit r = tr.commit() //
				.add(fanout(2, a.name()), data1) //
				.add(fanout(2, b.name()), data2) //
				.create();
		tr.parseBody(r);

		NoteMap map = NoteMap.read(reader, r);
		assertNotNull("have map", map);

		assertTrue("has note for a", map.contains(a));
		assertTrue("has note for b", map.contains(b));
		assertEquals(data1, map.get(a));
		assertEquals(data2, map.get(b));

		assertFalse("no note for data1", map.contains(data1));
		assertNull("no note for data1", map.get(data1));
	}

	public void testReadFanout2_2_36() throws Exception {
		RevBlob a = tr.blob("a");
		RevBlob b = tr.blob("b");
		RevBlob data1 = tr.blob("data1");
		RevBlob data2 = tr.blob("data2");

		RevCommit r = tr.commit() //
				.add(fanout(4, a.name()), data1) //
				.add(fanout(4, b.name()), data2) //
				.create();
		tr.parseBody(r);

		NoteMap map = NoteMap.read(reader, r);
		assertNotNull("have map", map);

		assertTrue("has note for a", map.contains(a));
		assertTrue("has note for b", map.contains(b));
		assertEquals(data1, map.get(a));
		assertEquals(data2, map.get(b));

		assertFalse("no note for data1", map.contains(data1));
		assertNull("no note for data1", map.get(data1));
	}

	public void testReadFullyFannedOut() throws Exception {
		RevBlob a = tr.blob("a");
		RevBlob b = tr.blob("b");
		RevBlob data1 = tr.blob("data1");
		RevBlob data2 = tr.blob("data2");

		RevCommit r = tr.commit() //
				.add(fanout(38, a.name()), data1) //
				.add(fanout(38, b.name()), data2) //
				.create();
		tr.parseBody(r);

		NoteMap map = NoteMap.read(reader, r);
		assertNotNull("have map", map);

		assertTrue("has note for a", map.contains(a));
		assertTrue("has note for b", map.contains(b));
		assertEquals(data1, map.get(a));
		assertEquals(data2, map.get(b));

		assertFalse("no note for data1", map.contains(data1));
		assertNull("no note for data1", map.get(data1));
	}

	public void testGetCachedBytes() throws Exception {
		final String exp = "this is test data";
		RevBlob a = tr.blob("a");
		RevBlob data = tr.blob(exp);

		RevCommit r = tr.commit() //
				.add(a.name(), data) //
				.create();
		tr.parseBody(r);

		NoteMap map = NoteMap.read(reader, r);
		byte[] act = map.getCachedBytes(a, exp.length() * 4);
		assertNotNull("has data for a", act);
		assertEquals(exp, RawParseUtils.decode(act));
	}

	public void testWriteUnchangedFlat() throws Exception {
		RevBlob a = tr.blob("a");
		RevBlob b = tr.blob("b");
		RevBlob data1 = tr.blob("data1");
		RevBlob data2 = tr.blob("data2");

		RevCommit r = tr.commit() //
				.add(a.name(), data1) //
				.add(b.name(), data2) //
				.add(".gitignore", "") //
				.add("zoo-animals.txt", "") //
				.create();
		tr.parseBody(r);

		NoteMap map = NoteMap.read(reader, r);
		assertTrue("has note for a", map.contains(a));
		assertTrue("has note for b", map.contains(b));

		RevCommit n = commitNoteMap(map);
		assertNotSame("is new commit", r, n);
		assertSame("same tree", r.getTree(), n.getTree());
	}

	public void testWriteUnchangedFanout2_38() throws Exception {
		RevBlob a = tr.blob("a");
		RevBlob b = tr.blob("b");
		RevBlob data1 = tr.blob("data1");
		RevBlob data2 = tr.blob("data2");

		RevCommit r = tr.commit() //
				.add(fanout(2, a.name()), data1) //
				.add(fanout(2, b.name()), data2) //
				.add(".gitignore", "") //
				.add("zoo-animals.txt", "") //
				.create();
		tr.parseBody(r);

		NoteMap map = NoteMap.read(reader, r);
		assertTrue("has note for a", map.contains(a));
		assertTrue("has note for b", map.contains(b));

		// This is a non-lazy map, so we'll be looking at the leaf buckets.
		RevCommit n = commitNoteMap(map);
		assertNotSame("is new commit", r, n);
		assertSame("same tree", r.getTree(), n.getTree());

		// Use a lazy-map for the next round of the same test.
		map = NoteMap.read(reader, r);
		n = commitNoteMap(map);
		assertNotSame("is new commit", r, n);
		assertSame("same tree", r.getTree(), n.getTree());
	}

	public void testCreateFromEmpty() throws Exception {
		RevBlob a = tr.blob("a");
		RevBlob b = tr.blob("b");
		RevBlob data1 = tr.blob("data1");
		RevBlob data2 = tr.blob("data2");

		NoteMap map = NoteMap.newEmptyMap();
		assertFalse("no a", map.contains(a));
		assertFalse("no b", map.contains(b));

		map.set(a, data1);
		map.set(b, data2);

		assertEquals(data1, map.get(a));
		assertEquals(data2, map.get(b));

		map.remove(a);
		map.remove(b);

		assertFalse("no a", map.contains(a));
		assertFalse("no b", map.contains(b));

		map.set(a, "data1", inserter);
		assertEquals(data1, map.get(a));

		map.set(a, null, inserter);
		assertFalse("no a", map.contains(a));
	}

	public void testEditFlat() throws Exception {
		RevBlob a = tr.blob("a");
		RevBlob b = tr.blob("b");
		RevBlob data1 = tr.blob("data1");
		RevBlob data2 = tr.blob("data2");

		RevCommit r = tr.commit() //
				.add(a.name(), data1) //
				.add(b.name(), data2) //
				.add(".gitignore", "") //
				.add("zoo-animals.txt", b) //
				.create();
		tr.parseBody(r);

		NoteMap map = NoteMap.read(reader, r);
		map.set(a, data2);
		map.set(b, null);
		map.set(data1, b);
		map.set(data2, null);

		assertEquals(data2, map.get(a));
		assertEquals(b, map.get(data1));
		assertFalse("no b", map.contains(b));
		assertFalse("no data2", map.contains(data2));

		MutableObjectId id = new MutableObjectId();
		for (int p = 42; p > 0; p--) {
			id.setByte(1, p);
			map.set(id, data1);
		}

		for (int p = 42; p > 0; p--) {
			id.setByte(1, p);
			assertTrue("contains " + id, map.contains(id));
		}

		RevCommit n = commitNoteMap(map);
		map = NoteMap.read(reader, n);
		assertEquals(data2, map.get(a));
		assertEquals(b, map.get(data1));
		assertFalse("no b", map.contains(b));
		assertFalse("no data2", map.contains(data2));
		assertEquals(b, TreeWalk
				.forPath(reader, "zoo-animals.txt", n.getTree()).getObjectId(0));
	}

	public void testEditFanout2_38() throws Exception {
		RevBlob a = tr.blob("a");
		RevBlob b = tr.blob("b");
		RevBlob data1 = tr.blob("data1");
		RevBlob data2 = tr.blob("data2");

		RevCommit r = tr.commit() //
				.add(fanout(2, a.name()), data1) //
				.add(fanout(2, b.name()), data2) //
				.add(".gitignore", "") //
				.add("zoo-animals.txt", b) //
				.create();
		tr.parseBody(r);

		NoteMap map = NoteMap.read(reader, r);
		map.set(a, data2);
		map.set(b, null);
		map.set(data1, b);
		map.set(data2, null);

		assertEquals(data2, map.get(a));
		assertEquals(b, map.get(data1));
		assertFalse("no b", map.contains(b));
		assertFalse("no data2", map.contains(data2));
		RevCommit n = commitNoteMap(map);

		map.set(a, null);
		map.set(data1, null);
		assertFalse("no a", map.contains(a));
		assertFalse("no data1", map.contains(data1));

		map = NoteMap.read(reader, n);
		assertEquals(data2, map.get(a));
		assertEquals(b, map.get(data1));
		assertFalse("no b", map.contains(b));
		assertFalse("no data2", map.contains(data2));
		assertEquals(b, TreeWalk
				.forPath(reader, "zoo-animals.txt", n.getTree()).getObjectId(0));
	}

	public void testLeafSplitsWhenFull() throws Exception {
		RevBlob data1 = tr.blob("data1");
		MutableObjectId idBuf = new MutableObjectId();

		RevCommit r = tr.commit() //
				.add(data1.name(), data1) //
				.create();
		tr.parseBody(r);

		NoteMap map = NoteMap.read(reader, r);
		for (int i = 0; i < 254; i++) {
			idBuf.setByte(Constants.OBJECT_ID_LENGTH - 1, i);
			map.set(idBuf, data1);
		}

		RevCommit n = commitNoteMap(map);
		TreeWalk tw = new TreeWalk(reader);
		tw.reset(n.getTree());
		while (tw.next())
			assertFalse("no fan-out subtree", tw.isSubtree());

		for (int i = 254; i < 256; i++) {
			idBuf.setByte(Constants.OBJECT_ID_LENGTH - 1, i);
			map.set(idBuf, data1);
		}
		idBuf.setByte(Constants.OBJECT_ID_LENGTH - 2, 1);
		map.set(idBuf, data1);
		n = commitNoteMap(map);

		// The 00 bucket is fully split.
		String path = fanout(38, idBuf.name());
		tw = TreeWalk.forPath(reader, path, n.getTree());
		assertNotNull("has " + path, tw);

		// The other bucket is not.
		path = fanout(2, data1.name());
		tw = TreeWalk.forPath(reader, path, n.getTree());
		assertNotNull("has " + path, tw);
	}

	public void testRemoveDeletesTreeFanout2_38() throws Exception {
		RevBlob a = tr.blob("a");
		RevBlob data1 = tr.blob("data1");
		RevTree empty = tr.tree();

		RevCommit r = tr.commit() //
				.add(fanout(2, a.name()), data1) //
				.create();
		tr.parseBody(r);

		NoteMap map = NoteMap.read(reader, r);
		map.set(a, null);

		RevCommit n = commitNoteMap(map);
		assertEquals("empty tree", empty, n.getTree());
	}

	private RevCommit commitNoteMap(NoteMap map) throws IOException {
		tr.tick(600);

		CommitBuilder builder = new CommitBuilder();
		builder.setTreeId(map.writeTree(inserter));
		tr.setAuthorAndCommitter(builder);
		return tr.getRevWalk().parseCommit(inserter.insert(builder));
	}

	private static String fanout(int prefix, String name) {
		StringBuilder r = new StringBuilder();
		int i = 0;
		for (; i < prefix && i < name.length(); i += 2) {
			if (i != 0)
				r.append('/');
			r.append(name.charAt(i + 0));
			r.append(name.charAt(i + 1));
		}
		if (i < name.length()) {
			if (i != 0)
				r.append('/');
			r.append(name.substring(i));
		}
		return r.toString();
	}
}

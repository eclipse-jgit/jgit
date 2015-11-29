/*
 * Copyright (C) 2015, Google Inc.
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

package org.eclipse.jgit.treewalk;

import static org.eclipse.jgit.lib.FileMode.GITLINK;
import static org.eclipse.jgit.lib.FileMode.REGULAR_FILE;
import static org.eclipse.jgit.lib.FileMode.SYMLINK;
import static org.eclipse.jgit.lib.FileMode.TREE;
import static org.eclipse.jgit.lib.ObjectId.fromString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.TreeFormatter;
import org.junit.Test;

public class RawTreeIteratorTest {
	private final ObjectId idA = fromString("ab9c715d21d5486e59083fb6071566aa6ecd4d42");
	private final ObjectId idB = fromString("b213e8e25bb2442326e86cbfb9ef56319f482869");
	private final ObjectId idC = fromString("c61814fe9c3fc0503d4654ef4aace6a804da5ae7");
	private final ObjectId idD = fromString("d5cc76524bc29d856340736a9de8d0889b17bc13");

	@Test
	public void testEmptyTreeHasNoNext() {
		TreeFormatter tree = new TreeFormatter();
		RawTreeIterator itr = new RawTreeIterator(tree.toByteArray());
		assertSame(itr, itr.iterator());
		assertFalse(itr.hasNext());
	}

	@Test
	public void testOneEntry() {
		TreeFormatter tree = new TreeFormatter();
		tree.append("a", REGULAR_FILE, idA);
		RawTreeIterator itr = new RawTreeIterator(tree.toByteArray());

		assertTrue(itr.hasNext());
		assertSame(itr, itr.next());
		assertEquals(REGULAR_FILE.getBits(), itr.getRawMode());
		assertEquals("a", itr.getName());
		assertEquals(idA, itr.getObjectId());
		assertFalse(itr.hasNext());
	}

	@Test
	public void testFindAttributesWhenFirst() {
		TreeFormatter tree = new TreeFormatter();
		tree.append(".gitattributes", REGULAR_FILE, idA);
		RawTreeIterator itr = new RawTreeIterator(tree.toByteArray());

		assertTrue(itr.findFile(".gitattributes"));
		assertEquals(REGULAR_FILE.getBits(), itr.getRawMode());
		assertEquals(".gitattributes", itr.getName());
		assertEquals(idA, itr.getObjectId());
	}

	@Test
	public void testFindAttributesWhenSecond() {
		TreeFormatter tree = new TreeFormatter();
		tree.append(".config", SYMLINK, idA);
		tree.append(".gitattributes", REGULAR_FILE, idB);
		RawTreeIterator itr = new RawTreeIterator(tree.toByteArray());

		assertTrue(itr.findFile(".gitattributes"));
		assertEquals(REGULAR_FILE.getBits(), itr.getRawMode());
		assertEquals(".gitattributes", itr.getName());
		assertEquals(idB, itr.getObjectId());
	}

	@Test
	public void testFindAttributesWhenMissing() {
		TreeFormatter tree = new TreeFormatter();
		tree.append("src", REGULAR_FILE, idA);
		tree.append("zoo", REGULAR_FILE, idA);
		RawTreeIterator itr = new RawTreeIterator(tree.toByteArray());

		assertFalse(itr.findFile(".gitattributes"));
		assertEquals(11, itr.idOffset()); // Did not walk the entire tree.
		assertEquals("src", itr.getName());
	}

	@SuppressWarnings("boxing")
	@Test
	public void testOldMode() throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		buf.write(Constants.encode(String.format("%o", 0100664)));
		buf.write(' ');
		buf.write(Constants.encode("a"));
		buf.write(0);
		idA.copyRawTo(buf);
		RawTreeIterator itr = new RawTreeIterator(buf.toByteArray());

		assertTrue(itr.hasNext());
		assertSame(itr, itr.next());
		assertEquals(0100664, itr.getRawMode());
		assertEquals(REGULAR_FILE, itr.getFileMode());
		assertEquals("a", itr.getName());
		assertEquals(idA, itr.getObjectId());
		assertFalse(itr.hasNext());
	}

	@Test
	public void testMultipleEntries() {
		TreeFormatter tree = new TreeFormatter();
		tree.append("a", REGULAR_FILE, idA);
		tree.append("foo", TREE, idB);
		tree.append("git", GITLINK, idC);
		tree.append("sym", SYMLINK, idD);
		RawTreeIterator itr = new RawTreeIterator(tree.toByteArray());

		assertTrue(itr.hasNext());
		assertSame(itr, itr.next());
		assertEquals(REGULAR_FILE.getBits(), itr.getRawMode());
		assertEquals("a", itr.getName());
		assertEquals(idA, itr.getObjectId());

		assertSame(itr, itr.next());
		assertEquals(TREE.getBits(), itr.getRawMode());
		assertEquals("foo", itr.getName());
		assertEquals(idB, itr.getObjectId());

		assertSame(itr, itr.next());
		assertEquals(GITLINK.getBits(), itr.getRawMode());
		assertEquals("git", itr.getName());
		assertEquals(idC, itr.getObjectId());

		assertSame(itr, itr.next());
		assertEquals(SYMLINK.getBits(), itr.getRawMode());
		assertEquals("sym", itr.getName());
		assertEquals(idD, itr.getObjectId());
		assertFalse(itr.hasNext());
	}

	@Test
	public void testLongEntryName() throws Exception {
		int n = AbstractTreeIterator.DEFAULT_PATH_SIZE * 4;
		StringBuilder b = new StringBuilder(n);
		for (int i = 0; i < n; i++) {
			b.append('q');
		}
		String name = b.toString();

		TreeFormatter tree = new TreeFormatter();
		tree.append(name, REGULAR_FILE, idA);
		RawTreeIterator itr = new RawTreeIterator(tree.toByteArray());

		assertTrue(itr.hasNext());
		assertSame(itr, itr.next());
		assertEquals(name, itr.getName());
	}
}

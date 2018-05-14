/*
 * Copyright (C) 2008-2009, Google Inc.
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

import static org.eclipse.jgit.lib.FileMode.REGULAR_FILE;
import static org.eclipse.jgit.lib.FileMode.SYMLINK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Before;
import org.junit.Test;

public class CanonicalTreeParserTest {
	private final CanonicalTreeParser ctp = new CanonicalTreeParser();

	private final FileMode m644 = FileMode.REGULAR_FILE;

	private final FileMode mt = FileMode.TREE;

	private final ObjectId hash_a = ObjectId
			.fromString("6b9c715d21d5486e59083fb6071566aa6ecd4d42");

	private final ObjectId hash_foo = ObjectId
			.fromString("a213e8e25bb2442326e86cbfb9ef56319f482869");

	private final ObjectId hash_sometree = ObjectId
			.fromString("daf4bdb0d7bb24319810fe0e73aa317663448c93");

	private byte[] tree1;

	private byte[] tree2;

	private byte[] tree3;

	@Before
	public void setUp() throws Exception {
		tree1 = mktree(entry(m644, "a", hash_a));
		tree2 = mktree(entry(m644, "a", hash_a), entry(m644, "foo", hash_foo));
		tree3 = mktree(entry(m644, "a", hash_a), entry(mt, "b_sometree",
				hash_sometree), entry(m644, "foo", hash_foo));
	}

	private static byte[] mktree(byte[]... data) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (final byte[] e : data)
			out.write(e);
		return out.toByteArray();
	}

	private static byte[] entry(final FileMode mode, final String name,
			final ObjectId id) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		mode.copyTo(out);
		out.write(' ');
		out.write(Constants.encode(name));
		out.write(0);
		id.copyRawTo(out);
		return out.toByteArray();
	}

	private String path() {
		return RawParseUtils.decode(Constants.CHARSET, ctp.path,
				ctp.pathOffset, ctp.pathLen);
	}

	@Test
	public void testEmptyTree_AtEOF() throws Exception {
		ctp.reset(new byte[0]);
		assertTrue(ctp.eof());
	}

	@Test
	public void testOneEntry_Forward() throws Exception {
		ctp.reset(tree1);

		assertTrue(ctp.first());
		assertFalse(ctp.eof());
		assertEquals(m644.getBits(), ctp.mode);
		assertEquals("a", path());
		assertEquals(hash_a, ctp.getEntryObjectId());

		ctp.next(1);
		assertFalse(ctp.first());
		assertTrue(ctp.eof());
	}

	@Test
	public void testTwoEntries_ForwardOneAtATime() throws Exception {
		ctp.reset(tree2);

		assertTrue(ctp.first());
		assertFalse(ctp.eof());
		assertEquals(m644.getBits(), ctp.mode);
		assertEquals("a", path());
		assertEquals(hash_a, ctp.getEntryObjectId());

		ctp.next(1);
		assertFalse(ctp.eof());
		assertEquals(m644.getBits(), ctp.mode);
		assertEquals("foo", path());
		assertEquals(hash_foo, ctp.getEntryObjectId());

		ctp.next(1);
		assertFalse(ctp.first());
		assertTrue(ctp.eof());
	}

	@Test
	public void testOneEntry_Seek1IsEOF() throws Exception {
		ctp.reset(tree1);
		ctp.next(1);
		assertTrue(ctp.eof());
	}

	@Test
	public void testTwoEntries_Seek2IsEOF() throws Exception {
		ctp.reset(tree2);
		ctp.next(2);
		assertTrue(ctp.eof());
	}

	@Test
	public void testThreeEntries_Seek3IsEOF() throws Exception {
		ctp.reset(tree3);
		ctp.next(3);
		assertTrue(ctp.eof());
	}

	@Test
	public void testThreeEntries_Seek2() throws Exception {
		ctp.reset(tree3);

		ctp.next(2);
		assertFalse(ctp.eof());
		assertFalse(ctp.eof());
		assertEquals(m644.getBits(), ctp.mode);
		assertEquals("foo", path());
		assertEquals(hash_foo, ctp.getEntryObjectId());

		ctp.next(1);
		assertTrue(ctp.eof());
	}

	@Test
	public void testOneEntry_Backwards() throws Exception {
		ctp.reset(tree1);
		ctp.next(1);
		assertFalse(ctp.first());
		assertTrue(ctp.eof());

		ctp.back(1);
		assertTrue(ctp.first());
		assertFalse(ctp.eof());
		assertEquals(m644.getBits(), ctp.mode);
		assertEquals("a", path());
		assertEquals(hash_a, ctp.getEntryObjectId());
	}

	@Test
	public void testTwoEntries_BackwardsOneAtATime() throws Exception {
		ctp.reset(tree2);
		ctp.next(2);
		assertTrue(ctp.eof());

		ctp.back(1);
		assertFalse(ctp.eof());
		assertEquals(m644.getBits(), ctp.mode);
		assertEquals("foo", path());
		assertEquals(hash_foo, ctp.getEntryObjectId());

		ctp.back(1);
		assertFalse(ctp.eof());
		assertEquals(m644.getBits(), ctp.mode);
		assertEquals("a", path());
		assertEquals(hash_a, ctp.getEntryObjectId());
	}

	@Test
	public void testTwoEntries_BackwardsTwo() throws Exception {
		ctp.reset(tree2);
		ctp.next(2);
		assertTrue(ctp.eof());

		ctp.back(2);
		assertFalse(ctp.eof());
		assertEquals(m644.getBits(), ctp.mode);
		assertEquals("a", path());
		assertEquals(hash_a, ctp.getEntryObjectId());

		ctp.next(1);
		assertFalse(ctp.eof());
		assertEquals(m644.getBits(), ctp.mode);
		assertEquals("foo", path());
		assertEquals(hash_foo, ctp.getEntryObjectId());

		ctp.next(1);
		assertTrue(ctp.eof());
	}

	@Test
	public void testThreeEntries_BackwardsTwo() throws Exception {
		ctp.reset(tree3);
		ctp.next(3);
		assertTrue(ctp.eof());

		ctp.back(2);
		assertFalse(ctp.eof());
		assertEquals(mt.getBits(), ctp.mode);
		assertEquals("b_sometree", path());
		assertEquals(hash_sometree, ctp.getEntryObjectId());

		ctp.next(1);
		assertFalse(ctp.eof());
		assertEquals(m644.getBits(), ctp.mode);
		assertEquals("foo", path());
		assertEquals(hash_foo, ctp.getEntryObjectId());

		ctp.next(1);
		assertTrue(ctp.eof());
	}

	@Test
	public void testBackwards_ConfusingPathName() throws Exception {
		final String aVeryConfusingName = "confusing 644 entry 755 and others";
		ctp.reset(mktree(entry(m644, "a", hash_a), entry(mt, aVeryConfusingName,
				hash_sometree), entry(m644, "foo", hash_foo)));
		ctp.next(3);
		assertTrue(ctp.eof());

		ctp.back(2);
		assertFalse(ctp.eof());
		assertEquals(mt.getBits(), ctp.mode);
		assertEquals(aVeryConfusingName, path());
		assertEquals(hash_sometree, ctp.getEntryObjectId());

		ctp.back(1);
		assertFalse(ctp.eof());
		assertEquals(m644.getBits(), ctp.mode);
		assertEquals("a", path());
		assertEquals(hash_a, ctp.getEntryObjectId());
	}

	@Test
	public void testBackwords_Prebuilts1() throws Exception {
		// What is interesting about this test is the ObjectId for the
		// "darwin-x86" path entry ends in an octal digit (37 == '7').
		// Thus when scanning backwards we could over scan and consume
		// part of the SHA-1, and miss the path terminator.
		//
		final ObjectId common = ObjectId
				.fromString("af7bf97cb9bce3f60f1d651a0ef862e9447dd8bc");
		final ObjectId darwinx86 = ObjectId
				.fromString("e927f7398240f78face99e1a738dac54ef738e37");
		final ObjectId linuxx86 = ObjectId
				.fromString("ac08dd97120c7cb7d06e98cd5b152011183baf21");
		final ObjectId windows = ObjectId
				.fromString("6c4c64c221a022bb973165192cca4812033479df");

		ctp.reset(mktree(entry(mt, "common", common), entry(mt, "darwin-x86",
				darwinx86), entry(mt, "linux-x86", linuxx86), entry(mt,
				"windows", windows)));
		ctp.next(3);
		assertEquals("windows", ctp.getEntryPathString());
		assertSame(mt, ctp.getEntryFileMode());
		assertEquals(windows, ctp.getEntryObjectId());

		ctp.back(1);
		assertEquals("linux-x86", ctp.getEntryPathString());
		assertSame(mt, ctp.getEntryFileMode());
		assertEquals(linuxx86, ctp.getEntryObjectId());

		ctp.next(1);
		assertEquals("windows", ctp.getEntryPathString());
		assertSame(mt, ctp.getEntryFileMode());
		assertEquals(windows, ctp.getEntryObjectId());
	}

	@Test
	public void testBackwords_Prebuilts2() throws Exception {
		// What is interesting about this test is the ObjectId for the
		// "darwin-x86" path entry ends in an octal digit (37 == '7').
		// Thus when scanning backwards we could over scan and consume
		// part of the SHA-1, and miss the path terminator.
		//
		final ObjectId common = ObjectId
				.fromString("af7bf97cb9bce3f60f1d651a0ef862e9447dd8bc");
		final ObjectId darwinx86 = ObjectId
				.fromString("0000000000000000000000000000000000000037");
		final ObjectId linuxx86 = ObjectId
				.fromString("ac08dd97120c7cb7d06e98cd5b152011183baf21");
		final ObjectId windows = ObjectId
				.fromString("6c4c64c221a022bb973165192cca4812033479df");

		ctp.reset(mktree(entry(mt, "common", common), entry(mt, "darwin-x86",
				darwinx86), entry(mt, "linux-x86", linuxx86), entry(mt,
				"windows", windows)));
		ctp.next(3);
		assertEquals("windows", ctp.getEntryPathString());
		assertSame(mt, ctp.getEntryFileMode());
		assertEquals(windows, ctp.getEntryObjectId());

		ctp.back(1);
		assertEquals("linux-x86", ctp.getEntryPathString());
		assertSame(mt, ctp.getEntryFileMode());
		assertEquals(linuxx86, ctp.getEntryObjectId());

		ctp.next(1);
		assertEquals("windows", ctp.getEntryPathString());
		assertSame(mt, ctp.getEntryFileMode());
		assertEquals(windows, ctp.getEntryObjectId());
	}

	@Test
	public void testFreakingHugePathName() throws Exception {
		final int n = AbstractTreeIterator.DEFAULT_PATH_SIZE * 4;
		final StringBuilder b = new StringBuilder(n);
		for (int i = 0; i < n; i++)
			b.append('q');
		final String name = b.toString();
		ctp.reset(entry(m644, name, hash_a));
		assertFalse(ctp.eof());
		assertEquals(name, RawParseUtils.decode(Constants.CHARSET, ctp.path,
				ctp.pathOffset, ctp.pathLen));
	}

	@Test
	public void testFindAttributesWhenFirst() throws CorruptObjectException {
		TreeFormatter tree = new TreeFormatter();
		tree.append(".gitattributes", REGULAR_FILE, hash_a);
		ctp.reset(tree.toByteArray());

		assertTrue(ctp.findFile(".gitattributes"));
		assertEquals(REGULAR_FILE.getBits(), ctp.getEntryRawMode());
		assertEquals(".gitattributes", ctp.getEntryPathString());
		assertEquals(hash_a, ctp.getEntryObjectId());
	}

	@Test
	public void testFindAttributesWhenSecond() throws CorruptObjectException {
		TreeFormatter tree = new TreeFormatter();
		tree.append(".config", SYMLINK, hash_a);
		tree.append(".gitattributes", REGULAR_FILE, hash_foo);
		ctp.reset(tree.toByteArray());

		assertTrue(ctp.findFile(".gitattributes"));
		assertEquals(REGULAR_FILE.getBits(), ctp.getEntryRawMode());
		assertEquals(".gitattributes", ctp.getEntryPathString());
		assertEquals(hash_foo, ctp.getEntryObjectId());
	}

	@Test
	public void testFindAttributesWhenMissing() throws CorruptObjectException {
		TreeFormatter tree = new TreeFormatter();
		tree.append("src", REGULAR_FILE, hash_a);
		tree.append("zoo", REGULAR_FILE, hash_foo);
		ctp.reset(tree.toByteArray());

		assertFalse(ctp.findFile(".gitattributes"));
		assertEquals(11, ctp.idOffset()); // Did not walk the entire tree.
		assertEquals("src", ctp.getEntryPathString());
	}
}

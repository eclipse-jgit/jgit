/*
 * Copyright (C) 2008, Google Inc.
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AbbreviatedObjectIdTest {
	@Test
	public void testEmpty_FromByteArray() {
		final AbbreviatedObjectId i;
		i = AbbreviatedObjectId.fromString(new byte[] {}, 0, 0);
		assertNotNull(i);
		assertEquals(0, i.length());
		assertFalse(i.isComplete());
		assertEquals("", i.name());
	}

	@Test
	public void testEmpty_FromString() {
		final AbbreviatedObjectId i = AbbreviatedObjectId.fromString("");
		assertNotNull(i);
		assertEquals(0, i.length());
		assertFalse(i.isComplete());
		assertEquals("", i.name());
	}

	@Test
	public void testFull_FromByteArray() {
		final String s = "7b6e8067ec96acef9a4184b43210d583b6d2f99a";
		final byte[] b = Constants.encodeASCII(s);
		final AbbreviatedObjectId i = AbbreviatedObjectId.fromString(b, 0,
				b.length);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertTrue(i.isComplete());
		assertEquals(s, i.name());

		final ObjectId f = i.toObjectId();
		assertNotNull(f);
		assertEquals(ObjectId.fromString(s), f);
		assertEquals(f.hashCode(), i.hashCode());
	}

	@Test
	public void testFull_FromString() {
		final String s = "7b6e8067ec96acef9a4184b43210d583b6d2f99a";
		final AbbreviatedObjectId i = AbbreviatedObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertTrue(i.isComplete());
		assertEquals(s, i.name());

		final ObjectId f = i.toObjectId();
		assertNotNull(f);
		assertEquals(ObjectId.fromString(s), f);
		assertEquals(f.hashCode(), i.hashCode());
	}

	@Test
	public void test1_FromString() {
		final String s = "7";
		final AbbreviatedObjectId i = AbbreviatedObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toObjectId());
	}

	@Test
	public void test2_FromString() {
		final String s = "7b";
		final AbbreviatedObjectId i = AbbreviatedObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toObjectId());
	}

	@Test
	public void test3_FromString() {
		final String s = "7b6";
		final AbbreviatedObjectId i = AbbreviatedObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toObjectId());
	}

	@Test
	public void test4_FromString() {
		final String s = "7b6e";
		final AbbreviatedObjectId i = AbbreviatedObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toObjectId());
	}

	@Test
	public void test5_FromString() {
		final String s = "7b6e8";
		final AbbreviatedObjectId i = AbbreviatedObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toObjectId());
	}

	@Test
	public void test6_FromString() {
		final String s = "7b6e80";
		final AbbreviatedObjectId i = AbbreviatedObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toObjectId());
	}

	@Test
	public void test7_FromString() {
		final String s = "7b6e806";
		final AbbreviatedObjectId i = AbbreviatedObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toObjectId());
	}

	@Test
	public void test8_FromString() {
		final String s = "7b6e8067";
		final AbbreviatedObjectId i = AbbreviatedObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toObjectId());
	}

	@Test
	public void test9_FromString() {
		final String s = "7b6e8067e";
		final AbbreviatedObjectId i = AbbreviatedObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toObjectId());
	}

	@Test
	public void test17_FromString() {
		final String s = "7b6e8067ec96acef9";
		final AbbreviatedObjectId i = AbbreviatedObjectId.fromString(s);
		assertNotNull(i);
		assertEquals(s.length(), i.length());
		assertFalse(i.isComplete());
		assertEquals(s, i.name());
		assertNull(i.toObjectId());
	}

	@Test
	public void testEquals_Short() {
		final String s = "7b6e8067";
		final AbbreviatedObjectId a = AbbreviatedObjectId.fromString(s);
		final AbbreviatedObjectId b = AbbreviatedObjectId.fromString(s);
		assertNotSame(a, b);
		assertTrue(a.hashCode() == b.hashCode());
		assertEquals(b, a);
		assertEquals(a, b);
	}

	@Test
	public void testEquals_Full() {
		final String s = "7b6e8067ec96acef9a4184b43210d583b6d2f99a";
		final AbbreviatedObjectId a = AbbreviatedObjectId.fromString(s);
		final AbbreviatedObjectId b = AbbreviatedObjectId.fromString(s);
		assertNotSame(a, b);
		assertTrue(a.hashCode() == b.hashCode());
		assertEquals(b, a);
		assertEquals(a, b);
	}

	@Test
	public void testNotEquals_SameLength() {
		final String sa = "7b6e8067";
		final String sb = "7b6e806e";
		final AbbreviatedObjectId a = AbbreviatedObjectId.fromString(sa);
		final AbbreviatedObjectId b = AbbreviatedObjectId.fromString(sb);
		assertFalse(a.equals(b));
		assertFalse(b.equals(a));
	}

	@Test
	public void testNotEquals_DiffLength() {
		final String sa = "7b6e8067abcd";
		final String sb = "7b6e8067";
		final AbbreviatedObjectId a = AbbreviatedObjectId.fromString(sa);
		final AbbreviatedObjectId b = AbbreviatedObjectId.fromString(sb);
		assertFalse(a.equals(b));
		assertFalse(b.equals(a));
	}

	@Test
	public void testPrefixCompare_Full() {
		final String s1 = "7b6e8067ec96acef9a4184b43210d583b6d2f99a";
		final AbbreviatedObjectId a = AbbreviatedObjectId.fromString(s1);
		final ObjectId i1 = ObjectId.fromString(s1);
		assertEquals(0, a.prefixCompare(i1));
		assertTrue(i1.startsWith(a));

		final String s2 = "7b6e8067ec96acef9a4184b43210d583b6d2f99b";
		final ObjectId i2 = ObjectId.fromString(s2);
		assertTrue(a.prefixCompare(i2) < 0);
		assertFalse(i2.startsWith(a));

		final String s3 = "7b6e8067ec96acef9a4184b43210d583b6d2f999";
		final ObjectId i3 = ObjectId.fromString(s3);
		assertTrue(a.prefixCompare(i3) > 0);
		assertFalse(i3.startsWith(a));
	}

	@Test
	public void testPrefixCompare_1() {
		final String sa = "7";
		final AbbreviatedObjectId a = AbbreviatedObjectId.fromString(sa);

		final String s1 = "7b6e8067ec96acef9a4184b43210d583b6d2f99a";
		final ObjectId i1 = ObjectId.fromString(s1);
		assertEquals(0, a.prefixCompare(i1));
		assertTrue(i1.startsWith(a));

		final String s2 = "8b6e8067ec96acef9a4184b43210d583b6d2f99a";
		final ObjectId i2 = ObjectId.fromString(s2);
		assertTrue(a.prefixCompare(i2) < 0);
		assertFalse(i2.startsWith(a));

		final String s3 = "6b6e8067ec96acef9a4184b43210d583b6d2f99a";
		final ObjectId i3 = ObjectId.fromString(s3);
		assertTrue(a.prefixCompare(i3) > 0);
		assertFalse(i3.startsWith(a));
	}

	@Test
	public void testPrefixCompare_7() {
		final String sa = "7b6e806";
		final AbbreviatedObjectId a = AbbreviatedObjectId.fromString(sa);

		final String s1 = "7b6e8067ec96acef9a4184b43210d583b6d2f99a";
		final ObjectId i1 = ObjectId.fromString(s1);
		assertEquals(0, a.prefixCompare(i1));
		assertTrue(i1.startsWith(a));

		final String s2 = "7b6e8167ec86acef9a4184b43210d583b6d2f99a";
		final ObjectId i2 = ObjectId.fromString(s2);
		assertTrue(a.prefixCompare(i2) < 0);
		assertFalse(i2.startsWith(a));

		final String s3 = "7b6e8057eca6acef9a4184b43210d583b6d2f99a";
		final ObjectId i3 = ObjectId.fromString(s3);
		assertTrue(a.prefixCompare(i3) > 0);
		assertFalse(i3.startsWith(a));
	}

	@Test
	public void testPrefixCompare_8() {
		final String sa = "7b6e8067";
		final AbbreviatedObjectId a = AbbreviatedObjectId.fromString(sa);

		final String s1 = "7b6e8067ec96acef9a4184b43210d583b6d2f99a";
		final ObjectId i1 = ObjectId.fromString(s1);
		assertEquals(0, a.prefixCompare(i1));
		assertTrue(i1.startsWith(a));

		final String s2 = "7b6e8167ec86acef9a4184b43210d583b6d2f99a";
		final ObjectId i2 = ObjectId.fromString(s2);
		assertTrue(a.prefixCompare(i2) < 0);
		assertFalse(i2.startsWith(a));

		final String s3 = "7b6e8057eca6acef9a4184b43210d583b6d2f99a";
		final ObjectId i3 = ObjectId.fromString(s3);
		assertTrue(a.prefixCompare(i3) > 0);
		assertFalse(i3.startsWith(a));
	}

	@Test
	public void testPrefixCompare_9() {
		final String sa = "7b6e8067e";
		final AbbreviatedObjectId a = AbbreviatedObjectId.fromString(sa);

		final String s1 = "7b6e8067ec96acef9a4184b43210d583b6d2f99a";
		final ObjectId i1 = ObjectId.fromString(s1);
		assertEquals(0, a.prefixCompare(i1));
		assertTrue(i1.startsWith(a));

		final String s2 = "7b6e8167ec86acef9a4184b43210d583b6d2f99a";
		final ObjectId i2 = ObjectId.fromString(s2);
		assertTrue(a.prefixCompare(i2) < 0);
		assertFalse(i2.startsWith(a));

		final String s3 = "7b6e8057eca6acef9a4184b43210d583b6d2f99a";
		final ObjectId i3 = ObjectId.fromString(s3);
		assertTrue(a.prefixCompare(i3) > 0);
		assertFalse(i3.startsWith(a));
	}

	@Test
	public void testPrefixCompare_17() {
		final String sa = "7b6e8067ec96acef9";
		final AbbreviatedObjectId a = AbbreviatedObjectId.fromString(sa);

		final String s1 = "7b6e8067ec96acef9a4184b43210d583b6d2f99a";
		final ObjectId i1 = ObjectId.fromString(s1);
		assertEquals(0, a.prefixCompare(i1));
		assertTrue(i1.startsWith(a));

		final String s2 = "7b6e8067eca6acef9a4184b43210d583b6d2f99a";
		final ObjectId i2 = ObjectId.fromString(s2);
		assertTrue(a.prefixCompare(i2) < 0);
		assertFalse(i2.startsWith(a));

		final String s3 = "7b6e8067ec86acef9a4184b43210d583b6d2f99a";
		final ObjectId i3 = ObjectId.fromString(s3);
		assertTrue(a.prefixCompare(i3) > 0);
		assertFalse(i3.startsWith(a));
	}

	@Test
	public void testIsId() {
		// These are all too short.
		assertFalse(AbbreviatedObjectId.isId(""));
		assertFalse(AbbreviatedObjectId.isId("a"));

		// These are too long.
		assertFalse(AbbreviatedObjectId.isId(ObjectId.fromString(
				"7b6e8067ec86acef9a4184b43210d583b6d2f99a").name()
				+ "0"));
		assertFalse(AbbreviatedObjectId.isId(ObjectId.fromString(
				"7b6e8067ec86acef9a4184b43210d583b6d2f99a").name()
				+ "c0ffee"));

		// These contain non-hex characters.
		assertFalse(AbbreviatedObjectId.isId("01notahexstring"));

		// These should all work.
		assertTrue(AbbreviatedObjectId.isId("ab"));
		assertTrue(AbbreviatedObjectId.isId("abc"));
		assertTrue(AbbreviatedObjectId.isId("abcd"));
		assertTrue(AbbreviatedObjectId.isId("abcd0"));
		assertTrue(AbbreviatedObjectId.isId("abcd09"));
		assertTrue(AbbreviatedObjectId.isId(ObjectId.fromString(
				"7b6e8067ec86acef9a4184b43210d583b6d2f99a").name()));
	}
}

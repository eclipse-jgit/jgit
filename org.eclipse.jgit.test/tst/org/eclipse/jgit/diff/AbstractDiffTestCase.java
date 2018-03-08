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

package org.eclipse.jgit.diff;

import static org.eclipse.jgit.lib.Constants.CHARSET;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public abstract class AbstractDiffTestCase {
	@Test
	public void testEmptyInputs() {
		EditList r = diff(t(""), t(""));
		assertTrue("is empty", r.isEmpty());
	}

	@Test
	public void testCreateFile() {
		EditList r = diff(t(""), t("AB"));
		assertEquals(1, r.size());
		assertEquals(new Edit(0, 0, 0, 2), r.get(0));
	}

	@Test
	public void testDeleteFile() {
		EditList r = diff(t("AB"), t(""));
		assertEquals(1, r.size());
		assertEquals(new Edit(0, 2, 0, 0), r.get(0));
	}

	@Test
	public void testDegenerate_InsertMiddle() {
		EditList r = diff(t("ac"), t("aBc"));
		assertEquals(1, r.size());
		assertEquals(new Edit(1, 1, 1, 2), r.get(0));
	}

	@Test
	public void testDegenerate_DeleteMiddle() {
		EditList r = diff(t("aBc"), t("ac"));
		assertEquals(1, r.size());
		assertEquals(new Edit(1, 2, 1, 1), r.get(0));
	}

	@Test
	public void testDegenerate_ReplaceMiddle() {
		EditList r = diff(t("bCd"), t("bEd"));
		assertEquals(1, r.size());
		assertEquals(new Edit(1, 2, 1, 2), r.get(0));
	}

	@Test
	public void testDegenerate_InsertsIntoMidPosition() {
		EditList r = diff(t("aaaa"), t("aaXaa"));
		assertEquals(1, r.size());
		assertEquals(new Edit(2, 2, 2, 3), r.get(0));
	}

	@Test
	public void testDegenerate_InsertStart() {
		EditList r = diff(t("bc"), t("Abc"));
		assertEquals(1, r.size());
		assertEquals(new Edit(0, 0, 0, 1), r.get(0));
	}

	@Test
	public void testDegenerate_DeleteStart() {
		EditList r = diff(t("Abc"), t("bc"));
		assertEquals(1, r.size());
		assertEquals(new Edit(0, 1, 0, 0), r.get(0));
	}

	@Test
	public void testDegenerate_InsertEnd() {
		EditList r = diff(t("bc"), t("bcD"));
		assertEquals(1, r.size());
		assertEquals(new Edit(2, 2, 2, 3), r.get(0));
	}

	@Test
	public void testDegenerate_DeleteEnd() {
		EditList r = diff(t("bcD"), t("bc"));
		assertEquals(1, r.size());
		assertEquals(new Edit(2, 3, 2, 2), r.get(0));
	}

	@Test
	public void testEdit_ReplaceCommonDelete() {
		EditList r = diff(t("RbC"), t("Sb"));
		assertEquals(2, r.size());
		assertEquals(new Edit(0, 1, 0, 1), r.get(0));
		assertEquals(new Edit(2, 3, 2, 2), r.get(1));
	}

	@Test
	public void testEdit_CommonReplaceCommonDeleteCommon() {
		EditList r = diff(t("aRbCd"), t("aSbd"));
		assertEquals(2, r.size());
		assertEquals(new Edit(1, 2, 1, 2), r.get(0));
		assertEquals(new Edit(3, 4, 3, 3), r.get(1));
	}

	@Test
	public void testEdit_MoveBlock() {
		EditList r = diff(t("aYYbcdz"), t("abcdYYz"));
		assertEquals(2, r.size());
		assertEquals(new Edit(1, 3, 1, 1), r.get(0));
		assertEquals(new Edit(6, 6, 4, 6), r.get(1));
	}

	@Test
	public void testEdit_InvertBlocks() {
		EditList r = diff(t("aYYbcdXXz"), t("aXXbcdYYz"));
		assertEquals(2, r.size());
		assertEquals(new Edit(1, 3, 1, 3), r.get(0));
		assertEquals(new Edit(6, 8, 6, 8), r.get(1));
	}

	@Test
	public void testEdit_UniqueCommonLargerThanMatchPoint() {
		// We are testing 3 unique common matches, but two of
		// them are consumed as part of the 1st's LCS region.
		EditList r = diff(t("AbdeZ"), t("PbdeQR"));
		assertEquals(2, r.size());
		assertEquals(new Edit(0, 1, 0, 1), r.get(0));
		assertEquals(new Edit(4, 5, 4, 6), r.get(1));
	}

	@Test
	public void testEdit_CommonGrowsPrefixAndSuffix() {
		// Here there is only one common unique point, but we can grow it
		// in both directions to find the LCS in the middle.
		EditList r = diff(t("AaabccZ"), t("PaabccR"));
		assertEquals(2, r.size());
		assertEquals(new Edit(0, 1, 0, 1), r.get(0));
		assertEquals(new Edit(6, 7, 6, 7), r.get(1));
	}

	@Test
	public void testEdit_DuplicateAButCommonUniqueInB() {
		EditList r = diff(t("AbbcR"), t("CbcS"));
		assertEquals(2, r.size());
		assertEquals(new Edit(0, 2, 0, 1), r.get(0));
		assertEquals(new Edit(4, 5, 3, 4), r.get(1));
	}

	@Test
	public void testEdit_InsertNearCommonTail() {
		EditList r = diff(t("aq}nb"), t("aCq}nD}nb"));
		assertEquals(new Edit(1, 1, 1, 2), r.get(0));
		assertEquals(new Edit(4, 4, 5, 8), r.get(1));
		assertEquals(2, r.size());
	}

	@Test
	public void testEdit_DeleteNearCommonTail() {
		EditList r = diff(t("aCq}nD}nb"), t("aq}nb"));
		assertEquals(new Edit(1, 2, 1, 1), r.get(0));
		assertEquals(new Edit(5, 8, 4, 4), r.get(1));
		assertEquals(2, r.size());
	}

	@Test
	public void testEdit_DeleteNearCommonCenter() {
		EditList r = diff(t("abcd123123uvwxpq"), t("aBcd123uvwxPq"));
		assertEquals(new Edit(1, 2, 1, 2), r.get(0));
		assertEquals(new Edit(7, 10, 7, 7), r.get(1));
		assertEquals(new Edit(14, 15, 11, 12), r.get(2));
		assertEquals(3, r.size());
	}

	@Test
	public void testEdit_InsertNearCommonCenter() {
		EditList r = diff(t("aBcd123uvwxPq"), t("abcd123123uvwxpq"));
		assertEquals(new Edit(1, 2, 1, 2), r.get(0));
		assertEquals(new Edit(7, 7, 7, 10), r.get(1));
		assertEquals(new Edit(11, 12, 14, 15), r.get(2));
		assertEquals(3, r.size());
	}

	@Test
	public void testEdit_LinuxBug() {
		EditList r = diff(t("a{bcdE}z"), t("a{0bcdEE}z"));
		assertEquals(new Edit(2, 2, 2, 3), r.get(0));
		assertEquals(new Edit(6, 6, 7, 8), r.get(1));
		assertEquals(2, r.size());
	}

	public EditList diff(RawText a, RawText b) {
		return algorithm().diff(RawTextComparator.DEFAULT, a, b);
	}

	protected abstract DiffAlgorithm algorithm();

	public static RawText t(String text) {
		StringBuilder r = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			r.append(text.charAt(i));
			r.append('\n');
		}
		return new RawText(r.toString().getBytes(CHARSET));
	}
}

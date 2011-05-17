/*
 * Copyright (C) 2009, Christian Halstrick <christian.halstrick@sap.com>
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

package org.eclipse.jgit.merge;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Constants;
import org.junit.Test;

public class MergeAlgorithmTest {
	MergeFormatter fmt=new MergeFormatter();

	/**
	 * Check for a conflict where the second text was changed similar to the
	 * first one, but the second texts modification covers one more line.
	 *
	 * @throws IOException
	 */
	@Test
	public void testTwoConflictingModifications() throws IOException {
		assertEquals(t("a<b=Z>Zdefghij"),
				merge("abcdefghij", "abZdefghij", "aZZdefghij"));
	}

	/**
	 * Test a case where we have three consecutive chunks. The first text
	 * modifies all three chunks. The second text modifies the first and the
	 * last chunk. This should be reported as one conflicting region.
	 *
	 * @throws IOException
	 */
	@Test
	public void testOneAgainstTwoConflictingModifications() throws IOException {
		assertEquals(t("aZ<Z=c>Zefghij"),
				merge("abcdefghij", "aZZZefghij", "aZcZefghij"));
	}

	/**
	 * Test a merge where only the second text contains modifications. Expect as
	 * merge result the second text.
	 *
	 * @throws IOException
	 */
	@Test
	public void testNoAgainstOneModification() throws IOException {
		assertEquals(t("aZcZefghij"),
				merge("abcdefghij", "abcdefghij", "aZcZefghij"));
	}

	/**
	 * Both texts contain modifications but not on the same chunks. Expect a
	 * non-conflict merge result.
	 *
	 * @throws IOException
	 */
	@Test
	public void testTwoNonConflictingModifications() throws IOException {
		assertEquals(t("YbZdefghij"),
				merge("abcdefghij", "abZdefghij", "Ybcdefghij"));
	}

	/**
	 * Merge two complicated modifications. The merge algorithm has to extend
	 * and combine conflicting regions to get to the expected merge result.
	 *
	 * @throws IOException
	 */
	@Test
	public void testTwoComplicatedModifications() throws IOException {
		assertEquals(t("a<ZZZZfZhZj=bYdYYYYiY>"),
				merge("abcdefghij", "aZZZZfZhZj", "abYdYYYYiY"));
	}

	/**
	 * Test a conflicting region at the very start of the text.
	 *
	 * @throws IOException
	 */
	@Test
	public void testConflictAtStart() throws IOException {
		assertEquals(t("<Z=Y>bcdefghij"),
				merge("abcdefghij", "Zbcdefghij", "Ybcdefghij"));
	}

	/**
	 * Test a conflicting region at the very end of the text.
	 *
	 * @throws IOException
	 */
	@Test
	public void testConflictAtEnd() throws IOException {
		assertEquals(t("abcdefghi<Z=Y>"),
				merge("abcdefghij", "abcdefghiZ", "abcdefghiY"));
	}

	/**
	 * Check for a conflict where the second text was changed similar to the
	 * first one, but the second texts modification covers one more line.
	 *
	 * @throws IOException
	 */
	@Test
	public void testSameModification() throws IOException {
		assertEquals(t("abZdefghij"),
				merge("abcdefghij", "abZdefghij", "abZdefghij"));
	}

	/**
	 * Check that a deleted vs. a modified line shows up as conflict (see Bug
	 * 328551)
	 *
	 * @throws IOException
	 */
	@Test
	public void testDeleteVsModify() throws IOException {
		assertEquals(t("ab<=Z>defghij"),
				merge("abcdefghij", "abdefghij", "abZdefghij"));
	}

	@Test
	public void testInsertVsModify() throws IOException {
		assertEquals(t("a<bZ=XY>"), merge("ab", "abZ", "aXY"));
	}

	@Test
	public void testAdjacentModifications() throws IOException {
		assertEquals(t("a<Zc=bY>d"), merge("abcd", "aZcd", "abYd"));
	}

	@Test
	public void testSeperateModifications() throws IOException {
		assertEquals(t("aZcYe"), merge("abcde", "aZcde", "abcYe"));
	}

	/**
	 * Test merging two contents which do one similar modification and one
	 * insertion is only done by one side. Between modification and insertion is
	 * a block which is common between the two contents and the common base
	 *
	 * @throws IOException
	 */
	@Test
	public void testTwoSimilarModsAndOneInsert() throws IOException {
		assertEquals(t("IAAJ"), merge("iA", "IA", "IAAJ"));
		assertEquals(t("aBcDde"), merge("abcde", "aBcde", "aBcDde"));
		assertEquals(t("IAJ"), merge("iA", "IA", "IAJ"));
		assertEquals(t("IAAAJ"), merge("iA", "IA", "IAAAJ"));
		assertEquals(t("IAAAJCAB"), merge("iACAB", "IACAB", "IAAAJCAB"));
		assertEquals(t("HIAAAJCAB"), merge("HiACAB", "HIACAB", "HIAAAJCAB"));
		assertEquals(t("AGADEFHIAAAJCAB"),
				merge("AGADEFHiACAB", "AGADEFHIACAB", "AGADEFHIAAAJCAB"));

	}

	/**
	 * Test situations where (at least) one input value is the empty text
	 *
	 * @throws IOException
	 */
	@Test
	public void testEmptyTexts() throws IOException {
		// test modification against deletion
		assertEquals(t("<AB=>"), merge("A", "AB", ""));
		assertEquals(t("<=AB>"), merge("A", "", "AB"));

		// test unmodified against deletion
		assertEquals(t(""), merge("AB", "AB", ""));
		assertEquals(t(""), merge("AB", "", "AB"));

		// test deletion against deletion
		assertEquals(t(""), merge("AB", "", ""));
	}

	private String merge(String commonBase, String ours, String theirs) throws IOException {
		MergeResult r = new MergeAlgorithm().merge(RawTextComparator.DEFAULT,
				T(commonBase), T(ours), T(theirs));
		ByteArrayOutputStream bo=new ByteArrayOutputStream(50);
		fmt.formatMerge(bo, r, "B", "O", "T", Constants.CHARACTER_ENCODING);
		return new String(bo.toByteArray(), Constants.CHARACTER_ENCODING);
	}

	public static String t(String text) {
		StringBuilder r = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (c) {
			case '<':
				r.append("<<<<<<< O\n");
				break;
			case '=':
				r.append("=======\n");
				break;
			case '>':
				r.append(">>>>>>> T\n");
				break;
			default:
				r.append(c);
				r.append('\n');
			}
		}
		return r.toString();
	}

	public static RawText T(String text) {
		return new RawText(Constants.encode(t(text)));
	}
}

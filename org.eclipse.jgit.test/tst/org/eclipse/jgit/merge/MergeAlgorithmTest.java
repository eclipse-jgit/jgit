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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Constants;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.runner.RunWith;

@RunWith(Theories.class)
public class MergeAlgorithmTest {
	MergeFormatter fmt=new MergeFormatter();

	private final boolean newlineAtEnd;

	@DataPoints
	public static boolean[] newlineAtEndDataPoints = { false, true };

	public MergeAlgorithmTest(boolean newlineAtEnd) {
		this.newlineAtEnd = newlineAtEnd;
	}

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
	 * Merge two modifications with a shared delete at the end. The underlying
	 * diff algorithm has to provide consistent edit results to get the expected
	 * merge result.
	 *
	 * @throws IOException
	 */
	@Test
	public void testTwoModificationsWithSharedDelete() throws IOException {
		assertEquals(t("Cb}n}"),
				merge("ab}n}n}", "ab}n}", "Cb}n}"));
	}

	/**
	 * Merge modifications with a shared insert in the middle. The
	 * underlying diff algorithm has to provide consistent edit
	 * results to get the expected merge result.
	 *
	 * @throws IOException
	 */
	@Test
	public void testModificationsWithMiddleInsert() throws IOException {
		assertEquals(t("aBcd123123uvwxPq"),
				merge("abcd123uvwxpq", "aBcd123123uvwxPq", "abcd123123uvwxpq"));
	}

	/**
	 * Merge modifications with a shared delete in the middle. The
	 * underlying diff algorithm has to provide consistent edit
	 * results to get the expected merge result.
	 *
	 * @throws IOException
	 */
	@Test
	public void testModificationsWithMiddleDelete() throws IOException {
		assertEquals(t("Abz}z123Q"),
				merge("abz}z}z123q", "Abz}z123Q", "abz}z123q"));
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
	public void testSeparateModifications() throws IOException {
		assertEquals(t("aZcYe"), merge("abcde", "aZcde", "abcYe"));
	}

	@Test
	public void testBlankLines() throws IOException {
		assertEquals(t("aZc\nYe"), merge("abc\nde", "aZc\nde", "abc\nYe"));
	}

	/**
	 * Test merging two contents which do one similar modification and one
	 * insertion is only done by one side, in the middle. Between modification
	 * and insertion is a block which is common between the two contents and the
	 * common base
	 *
	 * @throws IOException
	 */
	@Test
	public void testTwoSimilarModsAndOneInsert() throws IOException {
		assertEquals(t("aBcDde"), merge("abcde", "aBcde", "aBcDde"));
		assertEquals(t("IAAAJCAB"), merge("iACAB", "IACAB", "IAAAJCAB"));
		assertEquals(t("HIAAAJCAB"), merge("HiACAB", "HIACAB", "HIAAAJCAB"));
		assertEquals(t("AGADEFHIAAAJCAB"),
				merge("AGADEFHiACAB", "AGADEFHIACAB", "AGADEFHIAAAJCAB"));
	}

	/**
	 * Test merging two contents which do one similar modification and one
	 * insertion is only done by one side, at the end. Between modification and
	 * insertion is a block which is common between the two contents and the
	 * common base
	 *
	 * @throws IOException
	 */
	@Test
	public void testTwoSimilarModsAndOneInsertAtEnd() throws IOException {
		Assume.assumeTrue(newlineAtEnd);
		assertEquals(t("IAAJ"), merge("iA", "IA", "IAAJ"));
		assertEquals(t("IAJ"), merge("iA", "IA", "IAJ"));
		assertEquals(t("IAAAJ"), merge("iA", "IA", "IAAAJ"));
	}

	@Test
	public void testTwoSimilarModsAndOneInsertAtEndNoNewlineAtEnd()
			throws IOException {
		Assume.assumeFalse(newlineAtEnd);
		assertEquals(t("I<A=AAJ>"), merge("iA", "IA", "IAAJ"));
		assertEquals(t("I<A=AJ>"), merge("iA", "IA", "IAJ"));
		assertEquals(t("I<A=AAAJ>"), merge("iA", "IA", "IAAAJ"));
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
		return new String(bo.toByteArray(), UTF_8);
	}

	public String t(String text) {
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
				if (newlineAtEnd || i < text.length() - 1)
					r.append('\n');
			}
		}
		return r.toString();
	}

	public RawText T(String text) {
		return new RawText(Constants.encode(t(text)));
	}
}

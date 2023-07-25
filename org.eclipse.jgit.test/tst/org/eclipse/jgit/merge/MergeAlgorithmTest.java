/*
 * Copyright (C) 2009, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
				merge("abcdefghij", "abZdefghij", "aZZdefghij", false));

		assertEquals(t("a<b|b=Z>Zdefghij"),
				merge("abcdefghij", "abZdefghij", "aZZdefghij", true));
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
				merge("abcdefghij", "aZZZefghij", "aZcZefghij", false));

		assertEquals(t("aZ<Z|c=c>Zefghij"),
				merge("abcdefghij", "aZZZefghij", "aZcZefghij", true));
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
				merge("abcdefghij", "abcdefghij", "aZcZefghij", false));

		assertEquals(t("aZcZefghij"),
				merge("abcdefghij", "abcdefghij", "aZcZefghij", true));
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
				merge("abcdefghij", "abZdefghij", "Ybcdefghij", false));

		assertEquals(t("YbZdefghij"),
				merge("abcdefghij", "abZdefghij", "Ybcdefghij", true));
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
				merge("abcdefghij", "aZZZZfZhZj", "abYdYYYYiY", false));

		assertEquals(t("a<ZZZZfZhZj|bcdefghij=bYdYYYYiY>"),
				merge("abcdefghij", "aZZZZfZhZj", "abYdYYYYiY", true));
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
				merge("ab}n}n}", "ab}n}", "Cb}n}", false));

		assertEquals(t("Cb}n}"), merge("ab}n}n}", "ab}n}", "Cb}n}", true));
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
				merge("abcd123uvwxpq", "aBcd123123uvwxPq", "abcd123123uvwxpq",
						false));

		assertEquals(t("aBcd123123uvwxPq"), merge("abcd123uvwxpq",
				"aBcd123123uvwxPq", "abcd123123uvwxpq", true));
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
				merge("abz}z}z123q", "Abz}z123Q", "abz}z123q", false));

		assertEquals(t("Abz}z123Q"),
				merge("abz}z}z123q", "Abz}z123Q", "abz}z123q", true));
	}

	/**
	 * Test a conflicting region at the very start of the text.
	 *
	 * @throws IOException
	 */
	@Test
	public void testConflictAtStart() throws IOException {
		assertEquals(t("<Z=Y>bcdefghij"),
				merge("abcdefghij", "Zbcdefghij", "Ybcdefghij", false));

		assertEquals(t("<Z|a=Y>bcdefghij"),
				merge("abcdefghij", "Zbcdefghij", "Ybcdefghij", true));
	}

	/**
	 * Test a conflicting region at the very end of the text.
	 *
	 * @throws IOException
	 */
	@Test
	public void testConflictAtEnd() throws IOException {
		assertEquals(t("abcdefghi<Z=Y>"),
				merge("abcdefghij", "abcdefghiZ", "abcdefghiY", false));

		assertEquals(t("abcdefghi<Z|j=Y>"),
				merge("abcdefghij", "abcdefghiZ", "abcdefghiY", true));
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
				merge("abcdefghij", "abZdefghij", "abZdefghij", false));

		assertEquals(t("abZdefghij"),
				merge("abcdefghij", "abZdefghij", "abZdefghij", true));
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
				merge("abcdefghij", "abdefghij", "abZdefghij", false));

		assertEquals(t("ab<|c=Z>defghij"),
				merge("abcdefghij", "abdefghij", "abZdefghij", true));
	}

	@Test
	public void testInsertVsModify() throws IOException {
		assertEquals(t("a<bZ=XY>"), merge("ab", "abZ", "aXY", false));
		assertEquals(t("a<bZ|b=XY>"), merge("ab", "abZ", "aXY", true));
	}

	@Test
	public void testAdjacentModifications() throws IOException {
		assertEquals(t("a<Zc=bY>d"), merge("abcd", "aZcd", "abYd", false));
		assertEquals(t("a<Zc|bc=bY>d"), merge("abcd", "aZcd", "abYd", true));
	}

	@Test
	public void testSeparateModifications() throws IOException {
		assertEquals(t("aZcYe"), merge("abcde", "aZcde", "abcYe", false));
		assertEquals(t("aZcYe"), merge("abcde", "aZcde", "abcYe", true));
	}

	@Test
	public void testBlankLines() throws IOException {
		assertEquals(t("aZc\nYe"),
				merge("abc\nde", "aZc\nde", "abc\nYe", false));
		assertEquals(t("aZc\nYe"),
				merge("abc\nde", "aZc\nde", "abc\nYe", true));
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
		assertEquals(t("aBcDde"), merge("abcde", "aBcde", "aBcDde", false));
		assertEquals(t("aBcDde"), merge("abcde", "aBcde", "aBcDde", true));

		assertEquals(t("IAAAJCAB"), merge("iACAB", "IACAB", "IAAAJCAB", false));
		assertEquals(t("IAAAJCAB"), merge("iACAB", "IACAB", "IAAAJCAB", true));

		assertEquals(t("HIAAAJCAB"),
				merge("HiACAB", "HIACAB", "HIAAAJCAB", false));
		assertEquals(t("HIAAAJCAB"),
				merge("HiACAB", "HIACAB", "HIAAAJCAB", true));

		assertEquals(t("AGADEFHIAAAJCAB"),
				merge("AGADEFHiACAB", "AGADEFHIACAB", "AGADEFHIAAAJCAB",
						false));
		assertEquals(t("AGADEFHIAAAJCAB"),
				merge("AGADEFHiACAB", "AGADEFHIACAB", "AGADEFHIAAAJCAB", true));
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
		assertEquals(t("IAAJ"), merge("iA", "IA", "IAAJ", false));
		assertEquals(t("IAAJ"), merge("iA", "IA", "IAAJ", true));

		assertEquals(t("IAJ"), merge("iA", "IA", "IAJ", false));
		assertEquals(t("IAJ"), merge("iA", "IA", "IAJ", true));

		assertEquals(t("IAAAJ"), merge("iA", "IA", "IAAAJ", false));
		assertEquals(t("IAAAJ"), merge("iA", "IA", "IAAAJ", true));
	}

	@Test
	public void testTwoSimilarModsAndOneInsertAtEndNoNewlineAtEnd()
			throws IOException {
		Assume.assumeFalse(newlineAtEnd);
		assertEquals(t("I<A=AAJ>"), merge("iA", "IA", "IAAJ", false));
		assertEquals(t("I<A|A=AAJ>"), merge("iA", "IA", "IAAJ", true));

		assertEquals(t("I<A=AJ>"), merge("iA", "IA", "IAJ", false));
		assertEquals(t("I<A|A=AJ>"), merge("iA", "IA", "IAJ", true));

		assertEquals(t("I<A=AAAJ>"), merge("iA", "IA", "IAAAJ", false));
		assertEquals(t("I<A|A=AAAJ>"), merge("iA", "IA", "IAAAJ", true));
	}

	/**
	 * Test situations where (at least) one input value is the empty text
	 *
	 * @throws IOException
	 */
	@Test
	public void testEmptyTexts() throws IOException {
		// test modification against deletion
		assertEquals(t("<AB=>"), merge("A", "AB", "", false));
		assertEquals(t("<AB|A=>"), merge("A", "AB", "", true));

		assertEquals(t("<=AB>"), merge("A", "", "AB", false));
		assertEquals(t("<|A=AB>"), merge("A", "", "AB", true));

		// test unmodified against deletion
		assertEquals(t(""), merge("AB", "AB", "", false));
		assertEquals(t(""), merge("AB", "AB", "", true));

		assertEquals(t(""), merge("AB", "", "AB", false));
		assertEquals(t(""), merge("AB", "", "AB", true));

		// test deletion against deletion
		assertEquals(t(""), merge("AB", "", "", false));
		assertEquals(t(""), merge("AB", "", "", true));
	}

	private String merge(String commonBase, String ours, String theirs,
			boolean diff3) throws IOException {
		MergeResult r = new MergeAlgorithm().merge(RawTextComparator.DEFAULT,
				T(commonBase), T(ours), T(theirs));
		ByteArrayOutputStream bo=new ByteArrayOutputStream(50);
		if (diff3) {
			fmt.formatMergeWriteBaseInConflicts(bo, r, "B", "O", "T", UTF_8);
		} else {
			fmt.formatMerge(bo, r, "B", "O", "T", UTF_8);
		}
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
			case '|':
				r.append("||||||| B\n");
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

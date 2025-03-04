/*
 * Copyright (C) 2024 Qualcomm Innovation Center, Inc.
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
public class MergeAlgorithmUnionTest {
	MergeFormatter fmt = new MergeFormatter();

	private final boolean newlineAtEnd;

	@DataPoints
	public static boolean[] newlineAtEndDataPoints = { false, true };

	public MergeAlgorithmUnionTest(boolean newlineAtEnd) {
		this.newlineAtEnd = newlineAtEnd;
	}

	/**
	 * Check for a conflict where the second text was changed similar to the
	 * first one, but the second texts modification covers one more line.
	 *
	 * @throws java.io.IOException
	 */
	@Test
	public void testTwoConflictingModifications() throws IOException {
		assertEquals(t("abZZdefghij"),
				merge("abcdefghij", "abZdefghij", "aZZdefghij"));
	}

	/**
	 * Test a case where we have three consecutive chunks. The first text
	 * modifies all three chunks. The second text modifies the first and the
	 * last chunk. This should be reported as one conflicting region.
	 *
	 * @throws java.io.IOException
	 */
	@Test
	public void testOneAgainstTwoConflictingModifications() throws IOException {
		assertEquals(t("aZZcZefghij"),
				merge("abcdefghij", "aZZZefghij", "aZcZefghij"));
	}

	/**
	 * Test a merge where only the second text contains modifications. Expect as
	 * merge result the second text.
	 *
	 * @throws java.io.IOException
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
	 * @throws java.io.IOException
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
	 * @throws java.io.IOException
	 */
	@Test
	public void testTwoComplicatedModifications() throws IOException {
		assertEquals(t("aZZZZfZhZjbYdYYYYiY"),
				merge("abcdefghij", "aZZZZfZhZj", "abYdYYYYiY"));
	}

	/**
	 * Merge two modifications with a shared delete at the end. The underlying
	 * diff algorithm has to provide consistent edit results to get the expected
	 * merge result.
	 *
	 * @throws java.io.IOException
	 */
	@Test
	public void testTwoModificationsWithSharedDelete() throws IOException {
		assertEquals(t("Cb}n}"), merge("ab}n}n}", "ab}n}", "Cb}n}"));
	}

	/**
	 * Merge modifications with a shared insert in the middle. The underlying
	 * diff algorithm has to provide consistent edit results to get the expected
	 * merge result.
	 *
	 * @throws java.io.IOException
	 */
	@Test
	public void testModificationsWithMiddleInsert() throws IOException {
		assertEquals(t("aBcd123123uvwxPq"),
				merge("abcd123uvwxpq", "aBcd123123uvwxPq", "abcd123123uvwxpq"));
	}

	/**
	 * Merge modifications with a shared delete in the middle. The underlying
	 * diff algorithm has to provide consistent edit results to get the expected
	 * merge result.
	 *
	 * @throws java.io.IOException
	 */
	@Test
	public void testModificationsWithMiddleDelete() throws IOException {
		assertEquals(t("Abz}z123Q"),
				merge("abz}z}z123q", "Abz}z123Q", "abz}z123q"));
	}

	@Test
	public void testInsertionAfterDeletion() throws IOException {
		assertEquals(t("abcd"), merge("abd", "ad", "abcd"));
	}

	@Test
	public void testInsertionBeforeDeletion() throws IOException {
		assertEquals(t("acbd"), merge("abd", "ad", "acbd"));
	}

	/**
	 * Test a conflicting region at the very start of the text.
	 *
	 * @throws java.io.IOException
	 */
	@Test
	public void testConflictAtStart() throws IOException {
		assertEquals(t("ZYbcdefghij"),
				merge("abcdefghij", "Zbcdefghij", "Ybcdefghij"));
	}

	/**
	 * Test a conflicting region at the very end of the text.
	 *
	 * @throws java.io.IOException
	 */
	@Test
	public void testConflictAtEnd() throws IOException {
		assertEquals(t("abcdefghiZY"),
				merge("abcdefghij", "abcdefghiZ", "abcdefghiY"));
	}

	/**
	 * Check for a conflict where the second text was changed similar to the
	 * first one, but the second texts modification covers one more line.
	 *
	 * @throws java.io.IOException
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
	 * @throws java.io.IOException
	 */
	@Test
	public void testDeleteVsModify() throws IOException {
		assertEquals(t("abZdefghij"),
				merge("abcdefghij", "abdefghij", "abZdefghij"));
	}

	@Test
	public void testInsertVsModify() throws IOException {
		assertEquals(t("abZXY"), merge("ab", "abZ", "aXY"));
	}

	@Test
	public void testAdjacentModifications() throws IOException {
		assertEquals(t("aZcbYd"), merge("abcd", "aZcd", "abYd"));
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
	 * @throws java.io.IOException
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
	 * @throws java.io.IOException
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
		assertEquals(t("IAAAJ"), merge("iA", "IA", "IAAJ"));

		assertEquals(t("IAAJ"), merge("iA", "IA", "IAJ"));

		assertEquals(t("IAAAAJ"), merge("iA", "IA", "IAAAJ"));
	}

	// Test situations where (at least) one input value is the empty text

	@Test
	public void testEmptyTextModifiedAgainstDeletion() throws IOException {
		// NOTE: git.git merge-file appends a '\n' to the end of the file even
		// when the input files do not have a newline at the end. That appears
		// to be a bug in git.git.
		assertEquals(t("AB"), merge("A", "AB", ""));
		assertEquals(t("AB"), merge("A", "", "AB"));
	}

	@Test
	public void testEmptyTextUnmodifiedAgainstDeletion() throws IOException {
		assertEquals(t(""), merge("AB", "AB", ""));

		assertEquals(t(""), merge("AB", "", "AB"));
	}

	@Test
	public void testEmptyTextDeletionAgainstDeletion() throws IOException {
		assertEquals(t(""), merge("AB", "", ""));
	}

	private String merge(String commonBase, String ours, String theirs)
			throws IOException {
		MergeAlgorithm ma = new MergeAlgorithm();
		ma.setContentMergeStrategy(ContentMergeStrategy.UNION);
		MergeResult<RawText> r = ma.merge(RawTextComparator.DEFAULT,
				T(commonBase), T(ours), T(theirs));
		ByteArrayOutputStream bo = new ByteArrayOutputStream(50);
		fmt.formatMerge(bo, r, "B", "O", "T", UTF_8);
		return bo.toString(UTF_8);
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

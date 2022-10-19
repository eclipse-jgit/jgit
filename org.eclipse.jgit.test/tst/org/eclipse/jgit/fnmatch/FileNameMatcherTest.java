/*
 * Copyright (C) 2008, Florian Köberle <florianskarten@web.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.fnmatch;

import static org.eclipse.jgit.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.eclipse.jgit.errors.InvalidPatternException;
import org.junit.jupiter.api.Test;

public class FileNameMatcherTest {

	private static void assertMatch(final String pattern, final String input,
			final boolean matchExpected, final boolean appendCanMatchExpected)
			throws InvalidPatternException {
		final FileNameMatcher matcher = new FileNameMatcher(pattern, null);
		matcher.append(input);
		assertEquals(matchExpected, matcher.isMatch());
		assertEquals(appendCanMatchExpected, matcher.canAppendMatch());
	}

	private static void assertFileNameMatch(final String pattern,
			final String input,
			final char excludedCharacter, final boolean matchExpected,
			final boolean appendCanMatchExpected)
			throws InvalidPatternException {
		final FileNameMatcher matcher = new FileNameMatcher(pattern,
				Character.valueOf(excludedCharacter));
		matcher.append(input);
		assertEquals(matchExpected, matcher.isMatch());
		assertEquals(appendCanMatchExpected, matcher.canAppendMatch());
	}

	@Test
	void testVerySimplePatternCase0() throws Exception {
		assertMatch("", "", true, false);
	}

	@Test
	void testVerySimplePatternCase1() throws Exception {
		assertMatch("ab", "a", false, true);
	}

	@Test
	void testVerySimplePatternCase2() throws Exception {
		assertMatch("ab", "ab", true, false);
	}

	@Test
	void testVerySimplePatternCase3() throws Exception {
		assertMatch("ab", "ac", false, false);
	}

	@Test
	void testVerySimplePatternCase4() throws Exception {
		assertMatch("ab", "abc", false, false);
	}

	@Test
	void testVerySimpleWirdcardCase0() throws Exception {
		assertMatch("?", "a", true, false);
	}

	@Test
	void testVerySimpleWildCardCase1() throws Exception {
		assertMatch("??", "a", false, true);
	}

	@Test
	void testVerySimpleWildCardCase2() throws Exception {
		assertMatch("??", "ab", true, false);
	}

	@Test
	void testVerySimpleWildCardCase3() throws Exception {
		assertMatch("??", "abc", false, false);
	}

	@Test
	void testVerySimpleStarCase0() throws Exception {
		assertMatch("*", "", true, true);
	}

	@Test
	void testVerySimpleStarCase1() throws Exception {
		assertMatch("*", "a", true, true);
	}

	@Test
	void testVerySimpleStarCase2() throws Exception {
		assertMatch("*", "ab", true, true);
	}

	@Test
	void testSimpleStarCase0() throws Exception {
		assertMatch("a*b", "a", false, true);
	}

	@Test
	void testSimpleStarCase1() throws Exception {
		assertMatch("a*c", "ac", true, true);
	}

	@Test
	void testSimpleStarCase2() throws Exception {
		assertMatch("a*c", "ab", false, true);
	}

	@Test
	void testSimpleStarCase3() throws Exception {
		assertMatch("a*c", "abc", true, true);
	}

	@Test
	void testManySolutionsCase0() throws Exception {
		assertMatch("a*a*a", "aaa", true, true);
	}

	@Test
	void testManySolutionsCase1() throws Exception {
		assertMatch("a*a*a", "aaaa", true, true);
	}

	@Test
	void testManySolutionsCase2() throws Exception {
		assertMatch("a*a*a", "ababa", true, true);
	}

	@Test
	void testManySolutionsCase3() throws Exception {
		assertMatch("a*a*a", "aaaaaaaa", true, true);
	}

	@Test
	void testManySolutionsCase4() throws Exception {
		assertMatch("a*a*a", "aaaaaaab", false, true);
	}

	@Test
	void testVerySimpleGroupCase0() throws Exception {
		assertMatch("[ab]", "a", true, false);
	}

	@Test
	void testVerySimpleGroupCase1() throws Exception {
		assertMatch("[ab]", "b", true, false);
	}

	@Test
	void testVerySimpleGroupCase2() throws Exception {
		assertMatch("[ab]", "ab", false, false);
	}

	@Test
	void testVerySimpleGroupRangeCase0() throws Exception {
		assertMatch("[b-d]", "a", false, false);
	}

	@Test
	void testVerySimpleGroupRangeCase1() throws Exception {
		assertMatch("[b-d]", "b", true, false);
	}

	@Test
	void testVerySimpleGroupRangeCase2() throws Exception {
		assertMatch("[b-d]", "c", true, false);
	}

	@Test
	void testVerySimpleGroupRangeCase3() throws Exception {
		assertMatch("[b-d]", "d", true, false);
	}

	@Test
	void testVerySimpleGroupRangeCase4() throws Exception {
		assertMatch("[b-d]", "e", false, false);
	}

	@Test
	void testVerySimpleGroupRangeCase5() throws Exception {
		assertMatch("[b-d]", "-", false, false);
	}

	@Test
	void testTwoGroupsCase0() throws Exception {
		assertMatch("[b-d][ab]", "bb", true, false);
	}

	@Test
	void testTwoGroupsCase1() throws Exception {
		assertMatch("[b-d][ab]", "ca", true, false);
	}

	@Test
	void testTwoGroupsCase2() throws Exception {
		assertMatch("[b-d][ab]", "fa", false, false);
	}

	@Test
	void testTwoGroupsCase3() throws Exception {
		assertMatch("[b-d][ab]", "bc", false, false);
	}

	@Test
	void testTwoRangesInOneGroupCase0() throws Exception {
		assertMatch("[b-ce-e]", "a", false, false);
	}

	@Test
	void testTwoRangesInOneGroupCase1() throws Exception {
		assertMatch("[b-ce-e]", "b", true, false);
	}

	@Test
	void testTwoRangesInOneGroupCase2() throws Exception {
		assertMatch("[b-ce-e]", "c", true, false);
	}

	@Test
	void testTwoRangesInOneGroupCase3() throws Exception {
		assertMatch("[b-ce-e]", "d", false, false);
	}

	@Test
	void testTwoRangesInOneGroupCase4() throws Exception {
		assertMatch("[b-ce-e]", "e", true, false);
	}

	@Test
	void testTwoRangesInOneGroupCase5() throws Exception {
		assertMatch("[b-ce-e]", "f", false, false);
	}

	@Test
	void testIncompleteRangesInOneGroupCase0() throws Exception {
		assertMatch("a[b-]", "ab", true, false);
	}

	@Test
	void testIncompleteRangesInOneGroupCase1() throws Exception {
		assertMatch("a[b-]", "ac", false, false);
	}

	@Test
	void testIncompleteRangesInOneGroupCase2() throws Exception {
		assertMatch("a[b-]", "a-", true, false);
	}

	@Test
	void testCombinedRangesInOneGroupCase0() throws Exception {
		assertMatch("[a-c-e]", "b", true, false);
	}

	/**
	 * The c belongs to the range a-c. "-e" is no valid range so d should not
	 * match.
	 *
	 * @throws Exception
	 *             for some reasons
	 */
	@Test
	void testCombinedRangesInOneGroupCase1() throws Exception {
		assertMatch("[a-c-e]", "d", false, false);
	}

	@Test
	void testCombinedRangesInOneGroupCase2() throws Exception {
		assertMatch("[a-c-e]", "e", true, false);
	}

	@Test
	void testInversedGroupCase0() throws Exception {
		assertMatch("[!b-c]", "a", true, false);
	}

	@Test
	void testInversedGroupCase1() throws Exception {
		assertMatch("[!b-c]", "b", false, false);
	}

	@Test
	void testInversedGroupCase2() throws Exception {
		assertMatch("[!b-c]", "c", false, false);
	}

	@Test
	void testInversedGroupCase3() throws Exception {
		assertMatch("[!b-c]", "d", true, false);
	}

	@Test
	void testAlphaGroupCase0() throws Exception {
		assertMatch("[[:alpha:]]", "d", true, false);
	}

	@Test
	void testAlphaGroupCase1() throws Exception {
		assertMatch("[[:alpha:]]", ":", false, false);
	}

	@Test
	void testAlphaGroupCase2() throws Exception {
		// \u00f6 = 'o' with dots on it
		assertMatch("[[:alpha:]]", "\u00f6", true, false);
	}

	@Test
	void test2AlphaGroupsCase0() throws Exception {
		// \u00f6 = 'o' with dots on it
		assertMatch("[[:alpha:]][[:alpha:]]", "a\u00f6", true, false);
		assertMatch("[[:alpha:]][[:alpha:]]", "a1", false, false);
	}

	@Test
	void testAlnumGroupCase0() throws Exception {
		assertMatch("[[:alnum:]]", "a", true, false);
	}

	@Test
	void testAlnumGroupCase1() throws Exception {
		assertMatch("[[:alnum:]]", "1", true, false);
	}

	@Test
	void testAlnumGroupCase2() throws Exception {
		assertMatch("[[:alnum:]]", ":", false, false);
	}

	@Test
	void testBlankGroupCase0() throws Exception {
		assertMatch("[[:blank:]]", " ", true, false);
	}

	@Test
	void testBlankGroupCase1() throws Exception {
		assertMatch("[[:blank:]]", "\t", true, false);
	}

	@Test
	void testBlankGroupCase2() throws Exception {
		assertMatch("[[:blank:]]", "\r", false, false);
	}

	@Test
	void testBlankGroupCase3() throws Exception {
		assertMatch("[[:blank:]]", "\n", false, false);
	}

	@Test
	void testBlankGroupCase4() throws Exception {
		assertMatch("[[:blank:]]", "a", false, false);
	}

	@Test
	void testCntrlGroupCase0() throws Exception {
		assertMatch("[[:cntrl:]]", "a", false, false);
	}

	@Test
	void testCntrlGroupCase1() throws Exception {
		assertMatch("[[:cntrl:]]", String.valueOf((char) 7), true, false);
	}

	@Test
	void testDigitGroupCase0() throws Exception {
		assertMatch("[[:digit:]]", "0", true, false);
	}

	@Test
	void testDigitGroupCase1() throws Exception {
		assertMatch("[[:digit:]]", "5", true, false);
	}

	@Test
	void testDigitGroupCase2() throws Exception {
		assertMatch("[[:digit:]]", "9", true, false);
	}

	@Test
	void testDigitGroupCase3() throws Exception {
		// \u06f9 = EXTENDED ARABIC-INDIC DIGIT NINE
		assertMatch("[[:digit:]]", "\u06f9", true, false);
	}

	@Test
	void testDigitGroupCase4() throws Exception {
		assertMatch("[[:digit:]]", "a", false, false);
	}

	@Test
	void testDigitGroupCase5() throws Exception {
		assertMatch("[[:digit:]]", "]", false, false);
	}

	@Test
	void testGraphGroupCase0() throws Exception {
		assertMatch("[[:graph:]]", "]", true, false);
	}

	@Test
	void testGraphGroupCase1() throws Exception {
		assertMatch("[[:graph:]]", "a", true, false);
	}

	@Test
	void testGraphGroupCase2() throws Exception {
		assertMatch("[[:graph:]]", ".", true, false);
	}

	@Test
	void testGraphGroupCase3() throws Exception {
		assertMatch("[[:graph:]]", "0", true, false);
	}

	@Test
	void testGraphGroupCase4() throws Exception {
		assertMatch("[[:graph:]]", " ", false, false);
	}

	@Test
	void testGraphGroupCase5() throws Exception {
		// \u00f6 = 'o' with dots on it
		assertMatch("[[:graph:]]", "\u00f6", true, false);
	}

	@Test
	void testLowerGroupCase0() throws Exception {
		assertMatch("[[:lower:]]", "a", true, false);
	}

	@Test
	void testLowerGroupCase1() throws Exception {
		assertMatch("[[:lower:]]", "h", true, false);
	}

	@Test
	void testLowerGroupCase2() throws Exception {
		assertMatch("[[:lower:]]", "A", false, false);
	}

	@Test
	void testLowerGroupCase3() throws Exception {
		assertMatch("[[:lower:]]", "H", false, false);
	}

	@Test
	void testLowerGroupCase4() throws Exception {
		// \u00e4 = small 'a' with dots on it
		assertMatch("[[:lower:]]", "\u00e4", true, false);
	}

	@Test
	void testLowerGroupCase5() throws Exception {
		assertMatch("[[:lower:]]", ".", false, false);
	}

	@Test
	void testPrintGroupCase0() throws Exception {
		assertMatch("[[:print:]]", "]", true, false);
	}

	@Test
	void testPrintGroupCase1() throws Exception {
		assertMatch("[[:print:]]", "a", true, false);
	}

	@Test
	void testPrintGroupCase2() throws Exception {
		assertMatch("[[:print:]]", ".", true, false);
	}

	@Test
	void testPrintGroupCase3() throws Exception {
		assertMatch("[[:print:]]", "0", true, false);
	}

	@Test
	void testPrintGroupCase4() throws Exception {
		assertMatch("[[:print:]]", " ", true, false);
	}

	@Test
	void testPrintGroupCase5() throws Exception {
		// \u00f6 = 'o' with dots on it
		assertMatch("[[:print:]]", "\u00f6", true, false);
	}

	@Test
	void testPunctGroupCase0() throws Exception {
		assertMatch("[[:punct:]]", ".", true, false);
	}

	@Test
	void testPunctGroupCase1() throws Exception {
		assertMatch("[[:punct:]]", "@", true, false);
	}

	@Test
	void testPunctGroupCase2() throws Exception {
		assertMatch("[[:punct:]]", " ", false, false);
	}

	@Test
	void testPunctGroupCase3() throws Exception {
		assertMatch("[[:punct:]]", "a", false, false);
	}

	@Test
	void testSpaceGroupCase0() throws Exception {
		assertMatch("[[:space:]]", " ", true, false);
	}

	@Test
	void testSpaceGroupCase1() throws Exception {
		assertMatch("[[:space:]]", "\t", true, false);
	}

	@Test
	void testSpaceGroupCase2() throws Exception {
		assertMatch("[[:space:]]", "\r", true, false);
	}

	@Test
	void testSpaceGroupCase3() throws Exception {
		assertMatch("[[:space:]]", "\n", true, false);
	}

	@Test
	void testSpaceGroupCase4() throws Exception {
		assertMatch("[[:space:]]", "a", false, false);
	}

	@Test
	void testUpperGroupCase0() throws Exception {
		assertMatch("[[:upper:]]", "a", false, false);
	}

	@Test
	void testUpperGroupCase1() throws Exception {
		assertMatch("[[:upper:]]", "h", false, false);
	}

	@Test
	void testUpperGroupCase2() throws Exception {
		assertMatch("[[:upper:]]", "A", true, false);
	}

	@Test
	void testUpperGroupCase3() throws Exception {
		assertMatch("[[:upper:]]", "H", true, false);
	}

	@Test
	void testUpperGroupCase4() throws Exception {
		// \u00c4 = 'A' with dots on it
		assertMatch("[[:upper:]]", "\u00c4", true, false);
	}

	@Test
	void testUpperGroupCase5() throws Exception {
		assertMatch("[[:upper:]]", ".", false, false);
	}

	@Test
	void testXDigitGroupCase0() throws Exception {
		assertMatch("[[:xdigit:]]", "a", true, false);
	}

	@Test
	void testXDigitGroupCase1() throws Exception {
		assertMatch("[[:xdigit:]]", "d", true, false);
	}

	@Test
	void testXDigitGroupCase2() throws Exception {
		assertMatch("[[:xdigit:]]", "f", true, false);
	}

	@Test
	void testXDigitGroupCase3() throws Exception {
		assertMatch("[[:xdigit:]]", "0", true, false);
	}

	@Test
	void testXDigitGroupCase4() throws Exception {
		assertMatch("[[:xdigit:]]", "5", true, false);
	}

	@Test
	void testXDigitGroupCase5() throws Exception {
		assertMatch("[[:xdigit:]]", "9", true, false);
	}

	@Test
	void testXDigitGroupCase6() throws Exception {
		assertMatch("[[:xdigit:]]", "۹", false, false);
	}

	@Test
	void testXDigitGroupCase7() throws Exception {
		assertMatch("[[:xdigit:]]", ".", false, false);
	}

	@Test
	void testWordroupCase0() throws Exception {
		assertMatch("[[:word:]]", "g", true, false);
	}

	@Test
	void testWordroupCase1() throws Exception {
		// \u00f6 = 'o' with dots on it
		assertMatch("[[:word:]]", "\u00f6", true, false);
	}

	@Test
	void testWordroupCase2() throws Exception {
		assertMatch("[[:word:]]", "5", true, false);
	}

	@Test
	void testWordroupCase3() throws Exception {
		assertMatch("[[:word:]]", "_", true, false);
	}

	@Test
	void testWordroupCase4() throws Exception {
		assertMatch("[[:word:]]", " ", false, false);
	}

	@Test
	void testWordroupCase5() throws Exception {
		assertMatch("[[:word:]]", ".", false, false);
	}

	@Test
	void testMixedGroupCase0() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "A", true, false);
	}

	@Test
	void testMixedGroupCase1() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "C", true, false);
	}

	@Test
	void testMixedGroupCase2() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "e", true, false);
	}

	@Test
	void testMixedGroupCase3() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "3", true, false);
	}

	@Test
	void testMixedGroupCase4() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "4", true, false);
	}

	@Test
	void testMixedGroupCase5() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "5", true, false);
	}

	@Test
	void testMixedGroupCase6() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "B", false, false);
	}

	@Test
	void testMixedGroupCase7() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "2", false, false);
	}

	@Test
	void testMixedGroupCase8() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "6", false, false);
	}

	@Test
	void testMixedGroupCase9() throws Exception {
		assertMatch("[A[:lower:]C3-5]", ".", false, false);
	}

	@Test
	void testSpecialGroupCase0() throws Exception {
		assertMatch("[[]", "[", true, false);
	}

	@Test
	void testSpecialGroupCase1() throws Exception {
		assertMatch("[]]", "]", true, false);
	}

	@Test
	void testSpecialGroupCase2() throws Exception {
		assertMatch("[]a]", "]", true, false);
	}

	@Test
	void testSpecialGroupCase3() throws Exception {
		assertMatch("[a[]", "[", true, false);
	}

	@Test
	void testSpecialGroupCase4() throws Exception {
		assertMatch("[a[]", "a", true, false);
	}

	@Test
	void testSpecialGroupCase5() throws Exception {
		assertMatch("[!]]", "]", false, false);
	}

	@Test
	void testSpecialGroupCase6() throws Exception {
		assertMatch("[!]]", "x", true, false);
	}

	@Test
	void testSpecialGroupCase7() throws Exception {
		assertMatch("[:]]", ":]", true, false);
	}

	@Test
	void testSpecialGroupCase8() throws Exception {
		assertMatch("[:]]", ":", false, true);
	}

	@Test
	void testSpecialGroupCase9() throws Exception {
		try {
			assertMatch("[[:]", ":", true, true);
			fail("InvalidPatternException expected");
		} catch (InvalidPatternException e) {
			// expected
		}
	}

	@Test
	void testUnsupportedGroupCase0() throws Exception {
		try {
			assertMatch("[[=a=]]", "b", false, false);
			fail("InvalidPatternException expected");
		} catch (InvalidPatternException e) {
			assertTrue(e.getMessage().contains("[=a=]"));
		}
	}

	@Test
	void testUnsupportedGroupCase1() throws Exception {
		try {
			assertMatch("[[.a.]]", "b", false, false);
			fail("InvalidPatternException expected");
		} catch (InvalidPatternException e) {
			assertTrue(e.getMessage().contains("[.a.]"));
		}
	}

	@Test
	void testEscapedBracket1() throws Exception {
		assertMatch("\\[", "[", true, false);
	}

	@Test
	void testEscapedBracket2() throws Exception {
		assertMatch("\\[[a]", "[", false, true);
	}

	@Test
	void testEscapedBracket3() throws Exception {
		assertMatch("\\[[a]", "a", false, false);
	}

	@Test
	void testEscapedBracket4() throws Exception {
		assertMatch("\\[[a]", "[a", true, false);
	}

	@Test
	void testEscapedBracket5() throws Exception {
		assertMatch("[a\\]]", "]", true, false);
	}

	@Test
	void testEscapedBracket6() throws Exception {
		assertMatch("[a\\]]", "a", true, false);
	}

	@Test
	void testEscapedBackslash() throws Exception {
		assertMatch("a\\\\b", "a\\b", true, false);
	}

	@Test
	void testMultipleEscapedCharacters1() throws Exception {
		assertMatch("\\]a?c\\*\\[d\\?\\]", "]abc*[d?]", true, false);
	}

	@Test
	void testFilePathSimpleCase() throws Exception {
		assertFileNameMatch("a/b", "a/b", '/', true, false);
	}

	@Test
	void testFilePathCase0() throws Exception {
		assertFileNameMatch("a*b", "a/b", '/', false, false);
	}

	@Test
	void testFilePathCase1() throws Exception {
		assertFileNameMatch("a?b", "a/b", '/', false, false);
	}

	@Test
	void testFilePathCase2() throws Exception {
		assertFileNameMatch("a*b", "a\\b", '\\', false, false);
	}

	@Test
	void testFilePathCase3() throws Exception {
		assertFileNameMatch("a?b", "a\\b", '\\', false, false);
	}

	@Test
	void testReset() throws Exception {
		final String pattern = "helloworld";
		final FileNameMatcher matcher = new FileNameMatcher(pattern, null);
		matcher.append("helloworld");
		assertTrue(matcher.isMatch());
		assertFalse(matcher.canAppendMatch());
		matcher.reset();
		matcher.append("hello");
		assertFalse(matcher.isMatch());
		assertTrue(matcher.canAppendMatch());
		matcher.append("world");
		assertTrue(matcher.isMatch());
		assertFalse(matcher.canAppendMatch());
		matcher.append("to much");
		assertFalse(matcher.isMatch());
		assertFalse(matcher.canAppendMatch());
		matcher.reset();
		matcher.append("helloworld");
		assertTrue(matcher.isMatch());
		assertFalse(matcher.canAppendMatch());
	}

	@Test
	void testCreateMatcherForSuffix() throws Exception {
		final String pattern = "helloworld";
		final FileNameMatcher matcher = new FileNameMatcher(pattern, null);
		matcher.append("hello");
		final FileNameMatcher childMatcher = matcher.createMatcherForSuffix();
		assertFalse(matcher.isMatch());
		assertTrue(matcher.canAppendMatch());
		assertFalse(childMatcher.isMatch());
		assertTrue(childMatcher.canAppendMatch());
		matcher.append("world");
		assertTrue(matcher.isMatch());
		assertFalse(matcher.canAppendMatch());
		assertFalse(childMatcher.isMatch());
		assertTrue(childMatcher.canAppendMatch());
		childMatcher.append("world");
		assertTrue(matcher.isMatch());
		assertFalse(matcher.canAppendMatch());
		assertTrue(childMatcher.isMatch());
		assertFalse(childMatcher.canAppendMatch());
		childMatcher.reset();
		assertTrue(matcher.isMatch());
		assertFalse(matcher.canAppendMatch());
		assertFalse(childMatcher.isMatch());
		assertTrue(childMatcher.canAppendMatch());
		childMatcher.append("world");
		assertTrue(matcher.isMatch());
		assertFalse(matcher.canAppendMatch());
		assertTrue(childMatcher.isMatch());
		assertFalse(childMatcher.canAppendMatch());
	}

	@Test
	void testCopyConstructor() throws Exception {
		final String pattern = "helloworld";
		final FileNameMatcher matcher = new FileNameMatcher(pattern, null);
		matcher.append("hello");
		final FileNameMatcher copy = new FileNameMatcher(matcher);
		assertFalse(matcher.isMatch());
		assertTrue(matcher.canAppendMatch());
		assertFalse(copy.isMatch());
		assertTrue(copy.canAppendMatch());
		matcher.append("world");
		assertTrue(matcher.isMatch());
		assertFalse(matcher.canAppendMatch());
		assertFalse(copy.isMatch());
		assertTrue(copy.canAppendMatch());
		copy.append("world");
		assertTrue(matcher.isMatch());
		assertFalse(matcher.canAppendMatch());
		assertTrue(copy.isMatch());
		assertFalse(copy.canAppendMatch());
		copy.reset();
		assertTrue(matcher.isMatch());
		assertFalse(matcher.canAppendMatch());
		assertFalse(copy.isMatch());
		assertTrue(copy.canAppendMatch());
		copy.append("helloworld");
		assertTrue(matcher.isMatch());
		assertFalse(matcher.canAppendMatch());
		assertTrue(copy.isMatch());
		assertFalse(copy.canAppendMatch());
	}
}

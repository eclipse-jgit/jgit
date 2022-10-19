/*
 * Copyright (C) 2008, Florian Köberle <florianskarten@web.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.ignore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

@SuppressWarnings({ "boxing" })
public class IgnoreRuleSpecialCasesTest {

	private void assertMatch(final String pattern, final String input,
			final boolean matchExpected, Boolean... assume) {
		boolean assumeDir = input.endsWith("/");
		FastIgnoreRule matcher = new FastIgnoreRule(pattern);
		if (assume.length == 0 || !assume[0].booleanValue()) {
			assertEquals(matchExpected, matcher.isMatch(input, assumeDir));
		} else {
			assumeTrue(matchExpected == matcher.isMatch(input, assumeDir));
		}
	}

	private void assertFileNameMatch(final String pattern, final String input,
			final boolean matchExpected) {
		boolean assumeDir = input.endsWith("/");
		FastIgnoreRule matcher = new FastIgnoreRule(pattern);
		assertEquals(matchExpected, matcher.isMatch(input, assumeDir));
	}

	@Test
	void testVerySimplePatternCase0() throws Exception {
		assertMatch("", "", false);
	}

	@Test
	void testVerySimplePatternCase1() throws Exception {
		assertMatch("ab", "a", false);
	}

	@Test
	void testVerySimplePatternCase2() throws Exception {
		assertMatch("ab", "ab", true);
	}

	@Test
	void testVerySimplePatternCase3() throws Exception {
		assertMatch("ab", "ac", false);
	}

	@Test
	void testVerySimplePatternCase4() throws Exception {
		assertMatch("ab", "abc", false);
	}

	@Test
	void testVerySimpleWildcardCase0() throws Exception {
		assertMatch("?", "a", true);
	}

	@Test
	void testVerySimpleWildCardCase1() throws Exception {
		assertMatch("??", "a", false);
	}

	@Test
	void testVerySimpleWildCardCase2() throws Exception {
		assertMatch("??", "ab", true);
	}

	@Test
	void testVerySimpleWildCardCase3() throws Exception {
		assertMatch("??", "abc", false);
	}

	@Test
	void testVerySimpleStarCase0() throws Exception {
		// can't happen, but blank lines should never match
		assertMatch("*", "", false);
	}

	@Test
	void testVerySimpleStarCase1() throws Exception {
		assertMatch("*", "a", true);
	}

	@Test
	void testVerySimpleStarCase2() throws Exception {
		assertMatch("*", "ab", true);
	}

	@Test
	void testSimpleStarCase0() throws Exception {
		assertMatch("a*b", "a", false);
	}

	@Test
	void testSimpleStarCase1() throws Exception {
		assertMatch("a*c", "ac", true);
	}

	@Test
	void testSimpleStarCase2() throws Exception {
		assertMatch("a*c", "ab", false);
	}

	@Test
	void testSimpleStarCase3() throws Exception {
		assertMatch("a*c", "abc", true);
	}

	@Test
	void testManySolutionsCase0() throws Exception {
		assertMatch("a*a*a", "aaa", true);
	}

	@Test
	void testManySolutionsCase1() throws Exception {
		assertMatch("a*a*a", "aaaa", true);
	}

	@Test
	void testManySolutionsCase2() throws Exception {
		assertMatch("a*a*a", "ababa", true);
	}

	@Test
	void testManySolutionsCase3() throws Exception {
		assertMatch("a*a*a", "aaaaaaaa", true);
	}

	@Test
	void testManySolutionsCase4() throws Exception {
		assertMatch("a*a*a", "aaaaaaab", false);
	}

	@Test
	void testVerySimpleGroupCase0() throws Exception {
		assertMatch("[ab]", "a", true);
	}

	@Test
	void testVerySimpleGroupCase1() throws Exception {
		assertMatch("[ab]", "b", true);
	}

	@Test
	void testVerySimpleGroupCase2() throws Exception {
		assertMatch("[ab]", "ab", false);
	}

	@Test
	void testVerySimpleGroupRangeCase0() throws Exception {
		assertMatch("[b-d]", "a", false);
	}

	@Test
	void testVerySimpleGroupRangeCase1() throws Exception {
		assertMatch("[b-d]", "b", true);
	}

	@Test
	void testVerySimpleGroupRangeCase2() throws Exception {
		assertMatch("[b-d]", "c", true);
	}

	@Test
	void testVerySimpleGroupRangeCase3() throws Exception {
		assertMatch("[b-d]", "d", true);
	}

	@Test
	void testVerySimpleGroupRangeCase4() throws Exception {
		assertMatch("[b-d]", "e", false);
	}

	@Test
	void testVerySimpleGroupRangeCase5() throws Exception {
		assertMatch("[b-d]", "-", false);
	}

	@Test
	void testTwoGroupsCase0() throws Exception {
		assertMatch("[b-d][ab]", "bb", true);
	}

	@Test
	void testTwoGroupsCase1() throws Exception {
		assertMatch("[b-d][ab]", "ca", true);
	}

	@Test
	void testTwoGroupsCase2() throws Exception {
		assertMatch("[b-d][ab]", "fa", false);
	}

	@Test
	void testTwoGroupsCase3() throws Exception {
		assertMatch("[b-d][ab]", "bc", false);
	}

	@Test
	void testTwoRangesInOneGroupCase0() throws Exception {
		assertMatch("[b-ce-e]", "a", false);
	}

	@Test
	void testTwoRangesInOneGroupCase1() throws Exception {
		assertMatch("[b-ce-e]", "b", true);
	}

	@Test
	void testTwoRangesInOneGroupCase2() throws Exception {
		assertMatch("[b-ce-e]", "c", true);
	}

	@Test
	void testTwoRangesInOneGroupCase3() throws Exception {
		assertMatch("[b-ce-e]", "d", false);
	}

	@Test
	void testTwoRangesInOneGroupCase4() throws Exception {
		assertMatch("[b-ce-e]", "e", true);
	}

	@Test
	void testTwoRangesInOneGroupCase5() throws Exception {
		assertMatch("[b-ce-e]", "f", false);
	}

	@Test
	void testIncompleteRangesInOneGroupCase0() throws Exception {
		assertMatch("a[b-]", "ab", true);
	}

	@Test
	void testIncompleteRangesInOneGroupCase1() throws Exception {
		assertMatch("a[b-]", "ac", false);
	}

	@Test
	void testIncompleteRangesInOneGroupCase2() throws Exception {
		assertMatch("a[b-]", "a-", true);
	}

	@Test
	void testCombinedRangesInOneGroupCase0() throws Exception {
		assertMatch("[a-c-e]", "b", true);
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
		assertMatch("[a-c-e]", "d", false);
	}

	@Test
	void testCombinedRangesInOneGroupCase2() throws Exception {
		assertMatch("[a-c-e]", "e", true);
	}

	@Test
	void testInversedGroupCase0() throws Exception {
		assertMatch("[!b-c]", "a", true);
	}

	@Test
	void testInversedGroupCase1() throws Exception {
		assertMatch("[!b-c]", "b", false);
	}

	@Test
	void testInversedGroupCase2() throws Exception {
		assertMatch("[!b-c]", "c", false);
	}

	@Test
	void testInversedGroupCase3() throws Exception {
		assertMatch("[!b-c]", "d", true);
	}

	@Test
	void testAlphaGroupCase0() throws Exception {
		assertMatch("[[:alpha:]]", "d", true);
	}

	@Test
	void testAlphaGroupCase1() throws Exception {
		assertMatch("[[:alpha:]]", ":", false);
	}

	@Test
	void testAlphaGroupCase2() throws Exception {
		// \u00f6 = 'o' with dots on it
		assertMatch("[[:alpha:]]", "\u00f6", true);
	}

	@Test
	void test2AlphaGroupsCase0() throws Exception {
		// \u00f6 = 'o' with dots on it
		assertMatch("[[:alpha:]][[:alpha:]]", "a\u00f6", true);
		assertMatch("[[:alpha:]][[:alpha:]]", "a1", false);
	}

	@Test
	void testAlnumGroupCase0() throws Exception {
		assertMatch("[[:alnum:]]", "a", true);
	}

	@Test
	void testAlnumGroupCase1() throws Exception {
		assertMatch("[[:alnum:]]", "1", true);
	}

	@Test
	void testAlnumGroupCase2() throws Exception {
		assertMatch("[[:alnum:]]", ":", false);
	}

	@Test
	void testBlankGroupCase0() throws Exception {
		assertMatch("[[:blank:]]", " ", true);
	}

	@Test
	void testBlankGroupCase1() throws Exception {
		assertMatch("[[:blank:]]", "\t", true);
	}

	@Test
	void testBlankGroupCase2() throws Exception {
		assertMatch("[[:blank:]]", "\r", false);
	}

	@Test
	void testBlankGroupCase3() throws Exception {
		assertMatch("[[:blank:]]", "\n", false);
	}

	@Test
	void testBlankGroupCase4() throws Exception {
		assertMatch("[[:blank:]]", "a", false);
	}

	@Test
	void testCntrlGroupCase0() throws Exception {
		assertMatch("[[:cntrl:]]", "a", false);
	}

	@Test
	void testCntrlGroupCase1() throws Exception {
		assertMatch("[[:cntrl:]]", String.valueOf((char) 7), true);
	}

	@Test
	void testDigitGroupCase0() throws Exception {
		assertMatch("[[:digit:]]", "0", true);
	}

	@Test
	void testDigitGroupCase1() throws Exception {
		assertMatch("[[:digit:]]", "5", true);
	}

	@Test
	void testDigitGroupCase2() throws Exception {
		assertMatch("[[:digit:]]", "9", true);
	}

	@Test
	void testDigitGroupCase3() throws Exception {
		// \u06f9 = EXTENDED ARABIC-INDIC DIGIT NINE
		assertMatch("[[:digit:]]", "\u06f9", true);
	}

	@Test
	void testDigitGroupCase4() throws Exception {
		assertMatch("[[:digit:]]", "a", false);
	}

	@Test
	void testDigitGroupCase5() throws Exception {
		assertMatch("[[:digit:]]", "]", false);
	}

	@Test
	void testGraphGroupCase0() throws Exception {
		assertMatch("[[:graph:]]", "]", true);
	}

	@Test
	void testGraphGroupCase1() throws Exception {
		assertMatch("[[:graph:]]", "a", true);
	}

	@Test
	void testGraphGroupCase2() throws Exception {
		assertMatch("[[:graph:]]", ".", true);
	}

	@Test
	void testGraphGroupCase3() throws Exception {
		assertMatch("[[:graph:]]", "0", true);
	}

	@Test
	void testGraphGroupCase4() throws Exception {
		assertMatch("[[:graph:]]", " ", false);
	}

	@Test
	void testGraphGroupCase5() throws Exception {
		// \u00f6 = 'o' with dots on it
		assertMatch("[[:graph:]]", "\u00f6", true);
	}

	@Test
	void testLowerGroupCase0() throws Exception {
		assertMatch("[[:lower:]]", "a", true);
	}

	@Test
	void testLowerGroupCase1() throws Exception {
		assertMatch("[[:lower:]]", "h", true);
	}

	@Test
	void testLowerGroupCase2() throws Exception {
		assertMatch("[[:lower:]]", "A", false);
	}

	@Test
	void testLowerGroupCase3() throws Exception {
		assertMatch("[[:lower:]]", "H", false);
	}

	@Test
	void testLowerGroupCase4() throws Exception {
		// \u00e4 = small 'a' with dots on it
		assertMatch("[[:lower:]]", "\u00e4", true);
	}

	@Test
	void testLowerGroupCase5() throws Exception {
		assertMatch("[[:lower:]]", ".", false);
	}

	@Test
	void testPrintGroupCase0() throws Exception {
		assertMatch("[[:print:]]", "]", true);
	}

	@Test
	void testPrintGroupCase1() throws Exception {
		assertMatch("[[:print:]]", "a", true);
	}

	@Test
	void testPrintGroupCase2() throws Exception {
		assertMatch("[[:print:]]", ".", true);
	}

	@Test
	void testPrintGroupCase3() throws Exception {
		assertMatch("[[:print:]]", "0", true);
	}

	@Test
	void testPrintGroupCase4() throws Exception {
		assertMatch("[[:print:]]", " ", true);
	}

	@Test
	void testPrintGroupCase5() throws Exception {
		// \u00f6 = 'o' with dots on it
		assertMatch("[[:print:]]", "\u00f6", true);
	}

	@Test
	void testPunctGroupCase0() throws Exception {
		assertMatch("[[:punct:]]", ".", true);
	}

	@Test
	void testPunctGroupCase1() throws Exception {
		assertMatch("[[:punct:]]", "@", true);
	}

	@Test
	void testPunctGroupCase2() throws Exception {
		assertMatch("[[:punct:]]", " ", false);
	}

	@Test
	void testPunctGroupCase3() throws Exception {
		assertMatch("[[:punct:]]", "a", false);
	}

	@Test
	void testSpaceGroupCase0() throws Exception {
		assertMatch("[[:space:]]", " ", true);
	}

	@Test
	void testSpaceGroupCase1() throws Exception {
		assertMatch("[[:space:]]", "\t", true);
	}

	@Test
	void testSpaceGroupCase2() throws Exception {
		assertMatch("[[:space:]]", "\r", true);
	}

	@Test
	void testSpaceGroupCase3() throws Exception {
		assertMatch("[[:space:]]", "\n", true);
	}

	@Test
	void testSpaceGroupCase4() throws Exception {
		assertMatch("[[:space:]]", "a", false);
	}

	@Test
	void testUpperGroupCase0() throws Exception {
		assertMatch("[[:upper:]]", "a", false);
	}

	@Test
	void testUpperGroupCase1() throws Exception {
		assertMatch("[[:upper:]]", "h", false);
	}

	@Test
	void testUpperGroupCase2() throws Exception {
		assertMatch("[[:upper:]]", "A", true);
	}

	@Test
	void testUpperGroupCase3() throws Exception {
		assertMatch("[[:upper:]]", "H", true);
	}

	@Test
	void testUpperGroupCase4() throws Exception {
		// \u00c4 = 'A' with dots on it
		assertMatch("[[:upper:]]", "\u00c4", true);
	}

	@Test
	void testUpperGroupCase5() throws Exception {
		assertMatch("[[:upper:]]", ".", false);
	}

	@Test
	void testXDigitGroupCase0() throws Exception {
		assertMatch("[[:xdigit:]]", "a", true);
	}

	@Test
	void testXDigitGroupCase1() throws Exception {
		assertMatch("[[:xdigit:]]", "d", true);
	}

	@Test
	void testXDigitGroupCase2() throws Exception {
		assertMatch("[[:xdigit:]]", "f", true);
	}

	@Test
	void testXDigitGroupCase3() throws Exception {
		assertMatch("[[:xdigit:]]", "0", true);
	}

	@Test
	void testXDigitGroupCase4() throws Exception {
		assertMatch("[[:xdigit:]]", "5", true);
	}

	@Test
	void testXDigitGroupCase5() throws Exception {
		assertMatch("[[:xdigit:]]", "9", true);
	}

	@Test
	void testXDigitGroupCase6() throws Exception {
		assertMatch("[[:xdigit:]]", "۹", false);
	}

	@Test
	void testXDigitGroupCase7() throws Exception {
		assertMatch("[[:xdigit:]]", ".", false);
	}

	@Test
	void testWordGroupCase0() throws Exception {
		assertMatch("[[:word:]]", "g", true);
	}

	@Test
	void testWordGroupCase1() throws Exception {
		// \u00f6 = 'o' with dots on it
		assertMatch("[[:word:]]", "\u00f6", true);
	}

	@Test
	void testWordGroupCase2() throws Exception {
		assertMatch("[[:word:]]", "5", true);
	}

	@Test
	void testWordGroupCase3() throws Exception {
		assertMatch("[[:word:]]", "_", true);
	}

	@Test
	void testWordGroupCase4() throws Exception {
		assertMatch("[[:word:]]", " ", false);
	}

	@Test
	void testWordGroupCase5() throws Exception {
		assertMatch("[[:word:]]", ".", false);
	}

	@Test
	void testMixedGroupCase0() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "A", true);
	}

	@Test
	void testMixedGroupCase1() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "C", true);
	}

	@Test
	void testMixedGroupCase2() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "e", true);
	}

	@Test
	void testMixedGroupCase3() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "3", true);
	}

	@Test
	void testMixedGroupCase4() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "4", true);
	}

	@Test
	void testMixedGroupCase5() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "5", true);
	}

	@Test
	void testMixedGroupCase6() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "B", false);
	}

	@Test
	void testMixedGroupCase7() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "2", false);
	}

	@Test
	void testMixedGroupCase8() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "6", false);
	}

	@Test
	void testMixedGroupCase9() throws Exception {
		assertMatch("[A[:lower:]C3-5]", ".", false);
	}

	@Test
	void testSpecialGroupCase0() throws Exception {
		assertMatch("[[]", "[", true);
	}

	@Test
	void testSpecialGroupCase1() throws Exception {
		assertMatch("[]]", "]", true);
	}

	@Test
	void testSpecialGroupCase2() throws Exception {
		assertMatch("[]a]", "]", true);
	}

	@Test
	void testSpecialGroupCase3() throws Exception {
		assertMatch("[a[]", "[", true);
	}

	@Test
	void testSpecialGroupCase4() throws Exception {
		assertMatch("[a[]", "a", true);
	}

	@Test
	void testSpecialGroupCase5() throws Exception {
		assertMatch("[!]]", "]", false);
	}

	@Test
	void testSpecialGroupCase6() throws Exception {
		assertMatch("[!]]", "x", true);
	}

	@Test
	void testSpecialGroupCase7() throws Exception {
		assertMatch("[:]]", ":]", true);
	}

	@Test
	void testSpecialGroupCase8() throws Exception {
		assertMatch("[:]]", ":", false);
	}

	@Test
	void testSpecialGroupCase9() throws Exception {
		assertMatch("][", "][", false);
	}

	@Test
	void testSpecialGroupCase10() throws Exception {
		// Second bracket is threated literally, so both [ and : should match
		assertMatch("[[:]", ":", true);
		assertMatch("[[:]", "[", true);
	}

	@Test
	void testUnsupportedGroupCase0() throws Exception {
		assertMatch("[[=a=]]", "a", false);
		assertMatch("[[=a=]]", "=", false);
		assertMatch("[=a=]", "a", true);
		assertMatch("[=a=]", "=", true);
	}

	@Test
	void testUnsupportedGroupCase01() throws Exception {
		assertMatch("[.a.]*[.a.]", "aha", true);
	}

	@Test
	void testUnsupportedGroupCase1() throws Exception {
		assertMatch("[[.a.]]", "a", false);
		assertMatch("[[.a.]]", ".", false);
		assertMatch("[.a.]", "a", true);
		assertMatch("[.a.]", ".", true);
	}

	@Test
	void testEscapedBracket1() throws Exception {
		assertMatch("\\[", "[", true);
	}

	@Test
	void testEscapedBracket2() throws Exception {
		assertMatch("\\[[a]", "[", false);
	}

	@Test
	void testEscapedBracket3() throws Exception {
		assertMatch("\\[[a]", "a", false);
	}

	@Test
	void testEscapedBracket4() throws Exception {
		assertMatch("\\[[a]", "[a", true);
	}

	@Test
	void testEscapedBracket5() throws Exception {
		assertMatch("[a\\]]", "]", true);
	}

	@Test
	void testEscapedBracket6() throws Exception {
		assertMatch("[a\\]]", "a", true);
	}

	@Test
	void testIgnoredBackslash() throws Exception {
		// In Git CLI a\b\c is equal to abc
		assertMatch("a\\b\\c", "abc", true);
	}

	@Test
	void testEscapedBackslash() throws Exception {
		// In Git CLI a\\b matches a\b file
		assertMatch("a\\\\b", "a\\b", true);
		assertMatch("a\\\\b\\c", "a\\bc", true);

	}

	@Test
	void testEscapedExclamationMark() throws Exception {
		assertMatch("\\!b!.txt", "!b!.txt", true);
		assertMatch("a\\!b!.txt", "a!b!.txt", true);
	}

	@Test
	void testEscapedHash() throws Exception {
		assertMatch("\\#b", "#b", true);
		assertMatch("a\\#", "a#", true);
	}

	@Test
	void testEscapedTrailingSpaces() throws Exception {
		assertMatch("\\ ", " ", true);
		assertMatch("a\\ ", "a ", true);
	}

	@Test
	void testNotEscapingBackslash() throws Exception {
		assertMatch("\\out", "out", true);
		assertMatch("\\out", "a/out", true);
		assertMatch("c:\\/", "c:/", true);
		assertMatch("c:\\/", "a/c:/", true);
		assertMatch("c:\\tmp", "c:tmp", true);
		assertMatch("c:\\tmp", "a/c:tmp", true);
	}

	@Test
	void testMultipleEscapedCharacters1() throws Exception {
		assertMatch("\\]a?c\\*\\[d\\?\\]", "]abc*[d?]", true);
	}

	@Test
	void testBackslash() throws Exception {
		assertMatch("a\\", "a", true);
		assertMatch("\\a", "a", true);
		assertMatch("a/\\", "a/", true);
		assertMatch("a/b\\", "a/b", true);
		assertMatch("\\a/b", "a/b", true);
		assertMatch("/\\a", "/a", true);
		assertMatch("\\a\\b\\c\\", "abc", true);
		assertMatch("/\\a/\\b/\\c\\", "a/b/c", true);

		// empty path segment doesn't match
		assertMatch("\\/a", "/a", false);
		assertMatch("\\/a", "a", false);
	}

	@Test
	void testDollar() throws Exception {
		assertMatch("$", "$", true);
		assertMatch("$x", "$x", true);
		assertMatch("$x", "x$", false);
		assertMatch("$x", "$", false);

		assertMatch("$x.*", "$x.a", true);
		assertMatch("*$", "x$", true);
		assertMatch("*.$", "x.$", true);

		assertMatch("$*x", "$ax", true);
		assertMatch("x*$", "xa$", true);
		assertMatch("x*$", "xa", false);
		assertMatch("[a$b]", "$", true);
	}

	@Test
	void testCaret() throws Exception {
		assertMatch("^", "^", true);
		assertMatch("^x", "^x", true);
		assertMatch("^x", "x^", false);
		assertMatch("^x", "^", false);

		assertMatch("^x.*", "^x.a", true);
		assertMatch("*^", "x^", true);
		assertMatch("*.^", "x.^", true);

		assertMatch("x*^", "xa^", true);
		assertMatch("^*x", "^ax", true);
		assertMatch("^*x", "ax", false);
		assertMatch("[a^b]", "^", true);
	}

	@Test
	void testPlus() throws Exception {
		assertMatch("+", "+", true);
		assertMatch("+x", "+x", true);
		assertMatch("+x", "x+", false);
		assertMatch("+x", "+", false);
		assertMatch("x+", "xx", false);

		assertMatch("+x.*", "+x.a", true);
		assertMatch("*+", "x+", true);
		assertMatch("*.+", "x.+", true);

		assertMatch("x*+", "xa+", true);
		assertMatch("+*x", "+ax", true);
		assertMatch("+*x", "ax", false);
		assertMatch("[a+b]", "+", true);
	}

	@Test
	void testPipe() throws Exception {
		assertMatch("|", "|", true);
		assertMatch("|x", "|x", true);
		assertMatch("|x", "x|", false);
		assertMatch("|x", "|", false);
		assertMatch("x|x", "xx", false);

		assertMatch("x|x.*", "x|x.a", true);
		assertMatch("*|", "x|", true);
		assertMatch("*.|", "x.|", true);

		assertMatch("x*|a", "xb|a", true);
		assertMatch("b|*x", "b|ax", true);
		assertMatch("b|*x", "ax", false);
		assertMatch("[a|b]", "|", true);
	}

	@Test
	void testBrackets() throws Exception {
		assertMatch("{}*()", "{}x()", true);
		assertMatch("[a{}()b][a{}()b]?[a{}()b][a{}()b]", "{}x()", true);
		assertMatch("x*{x}3", "xa{x}3", true);
		assertMatch("a*{x}3", "axxx", false);

		assertMatch("?", "[", true);
		assertMatch("*", "[", true);

		// Escaped bracket matches, but see weird things below...
		assertMatch("\\[", "[", true);
	}

	/**
	 * The ignore rules here <b>do not match</b> any paths because single '['
	 * begins character group and the entire rule cannot be parsed due the
	 * invalid glob pattern. See
	 * http://article.gmane.org/gmane.comp.version-control.git/278699.
	 *
	 * @throws Exception
	 */
	@Test
	void testBracketsUnmatched1() throws Exception {
		assertMatch("[", "[", false);
		assertMatch("[*", "[", false);
		assertMatch("*[", "[", false);
		assertMatch("*[", "a[", false);
		assertMatch("[a][", "a[", false);
		assertMatch("*[", "a", false);
		assertMatch("[a", "a", false);
		assertMatch("[*", "a", false);
		assertMatch("[*a", "a", false);
	}

	/**
	 * Single ']' is treated here literally, not as an and of a character group
	 *
	 * @throws Exception
	 */
	@Test
	void testBracketsUnmatched2() throws Exception {
		assertMatch("*]", "a", false);
		assertMatch("]a", "a", false);
		assertMatch("]*", "a", false);
		assertMatch("]*a", "a", false);

		assertMatch("]", "]", true);
		assertMatch("]*", "]", true);
		assertMatch("]*", "]a", true);
		assertMatch("*]", "]", true);
		assertMatch("*]", "a]", true);
	}

	@Test
	void testBracketsRandom() throws Exception {
		assertMatch("[\\]", "[$0+//r4a\\d]", false);
		assertMatch("[:]]sZX]", "[:]]sZX]", false);
		assertMatch("[:]]:]]]", "[:]]:]]]", false);
	}

	@Test
	void testFilePathSimpleCase() throws Exception {
		assertFileNameMatch("a/b", "a/b", true);
	}

	@Test
	void testFilePathCase0() throws Exception {
		assertFileNameMatch("a*b", "a/b", false);
	}

	@Test
	void testFilePathCase1() throws Exception {
		assertFileNameMatch("a?b", "a/b", false);
	}

	@Test
	void testFilePathCase2() throws Exception {
		assertFileNameMatch("a*b", "a\\b", true);
	}

	@Test
	void testFilePathCase3() throws Exception {
		assertFileNameMatch("a?b", "a\\b", true);
	}

}

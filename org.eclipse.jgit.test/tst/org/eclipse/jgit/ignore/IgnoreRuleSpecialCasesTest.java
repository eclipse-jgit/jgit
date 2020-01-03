/*
 * Copyright (C) 2008, Florian Koeberle <florianskarten@web.de>
 * Copyright (C) 2008, Florian Köberle <florianskarten@web.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.ignore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;

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
	public void testVerySimplePatternCase0() throws Exception {
		assertMatch("", "", false);
	}

	@Test
	public void testVerySimplePatternCase1() throws Exception {
		assertMatch("ab", "a", false);
	}

	@Test
	public void testVerySimplePatternCase2() throws Exception {
		assertMatch("ab", "ab", true);
	}

	@Test
	public void testVerySimplePatternCase3() throws Exception {
		assertMatch("ab", "ac", false);
	}

	@Test
	public void testVerySimplePatternCase4() throws Exception {
		assertMatch("ab", "abc", false);
	}

	@Test
	public void testVerySimpleWildcardCase0() throws Exception {
		assertMatch("?", "a", true);
	}

	@Test
	public void testVerySimpleWildCardCase1() throws Exception {
		assertMatch("??", "a", false);
	}

	@Test
	public void testVerySimpleWildCardCase2() throws Exception {
		assertMatch("??", "ab", true);
	}

	@Test
	public void testVerySimpleWildCardCase3() throws Exception {
		assertMatch("??", "abc", false);
	}

	@Test
	public void testVerySimpleStarCase0() throws Exception {
		// can't happen, but blank lines should never match
		assertMatch("*", "", false);
	}

	@Test
	public void testVerySimpleStarCase1() throws Exception {
		assertMatch("*", "a", true);
	}

	@Test
	public void testVerySimpleStarCase2() throws Exception {
		assertMatch("*", "ab", true);
	}

	@Test
	public void testSimpleStarCase0() throws Exception {
		assertMatch("a*b", "a", false);
	}

	@Test
	public void testSimpleStarCase1() throws Exception {
		assertMatch("a*c", "ac", true);
	}

	@Test
	public void testSimpleStarCase2() throws Exception {
		assertMatch("a*c", "ab", false);
	}

	@Test
	public void testSimpleStarCase3() throws Exception {
		assertMatch("a*c", "abc", true);
	}

	@Test
	public void testManySolutionsCase0() throws Exception {
		assertMatch("a*a*a", "aaa", true);
	}

	@Test
	public void testManySolutionsCase1() throws Exception {
		assertMatch("a*a*a", "aaaa", true);
	}

	@Test
	public void testManySolutionsCase2() throws Exception {
		assertMatch("a*a*a", "ababa", true);
	}

	@Test
	public void testManySolutionsCase3() throws Exception {
		assertMatch("a*a*a", "aaaaaaaa", true);
	}

	@Test
	public void testManySolutionsCase4() throws Exception {
		assertMatch("a*a*a", "aaaaaaab", false);
	}

	@Test
	public void testVerySimpleGroupCase0() throws Exception {
		assertMatch("[ab]", "a", true);
	}

	@Test
	public void testVerySimpleGroupCase1() throws Exception {
		assertMatch("[ab]", "b", true);
	}

	@Test
	public void testVerySimpleGroupCase2() throws Exception {
		assertMatch("[ab]", "ab", false);
	}

	@Test
	public void testVerySimpleGroupRangeCase0() throws Exception {
		assertMatch("[b-d]", "a", false);
	}

	@Test
	public void testVerySimpleGroupRangeCase1() throws Exception {
		assertMatch("[b-d]", "b", true);
	}

	@Test
	public void testVerySimpleGroupRangeCase2() throws Exception {
		assertMatch("[b-d]", "c", true);
	}

	@Test
	public void testVerySimpleGroupRangeCase3() throws Exception {
		assertMatch("[b-d]", "d", true);
	}

	@Test
	public void testVerySimpleGroupRangeCase4() throws Exception {
		assertMatch("[b-d]", "e", false);
	}

	@Test
	public void testVerySimpleGroupRangeCase5() throws Exception {
		assertMatch("[b-d]", "-", false);
	}

	@Test
	public void testTwoGroupsCase0() throws Exception {
		assertMatch("[b-d][ab]", "bb", true);
	}

	@Test
	public void testTwoGroupsCase1() throws Exception {
		assertMatch("[b-d][ab]", "ca", true);
	}

	@Test
	public void testTwoGroupsCase2() throws Exception {
		assertMatch("[b-d][ab]", "fa", false);
	}

	@Test
	public void testTwoGroupsCase3() throws Exception {
		assertMatch("[b-d][ab]", "bc", false);
	}

	@Test
	public void testTwoRangesInOneGroupCase0() throws Exception {
		assertMatch("[b-ce-e]", "a", false);
	}

	@Test
	public void testTwoRangesInOneGroupCase1() throws Exception {
		assertMatch("[b-ce-e]", "b", true);
	}

	@Test
	public void testTwoRangesInOneGroupCase2() throws Exception {
		assertMatch("[b-ce-e]", "c", true);
	}

	@Test
	public void testTwoRangesInOneGroupCase3() throws Exception {
		assertMatch("[b-ce-e]", "d", false);
	}

	@Test
	public void testTwoRangesInOneGroupCase4() throws Exception {
		assertMatch("[b-ce-e]", "e", true);
	}

	@Test
	public void testTwoRangesInOneGroupCase5() throws Exception {
		assertMatch("[b-ce-e]", "f", false);
	}

	@Test
	public void testIncompleteRangesInOneGroupCase0() throws Exception {
		assertMatch("a[b-]", "ab", true);
	}

	@Test
	public void testIncompleteRangesInOneGroupCase1() throws Exception {
		assertMatch("a[b-]", "ac", false);
	}

	@Test
	public void testIncompleteRangesInOneGroupCase2() throws Exception {
		assertMatch("a[b-]", "a-", true);
	}

	@Test
	public void testCombinedRangesInOneGroupCase0() throws Exception {
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
	public void testCombinedRangesInOneGroupCase1() throws Exception {
		assertMatch("[a-c-e]", "d", false);
	}

	@Test
	public void testCombinedRangesInOneGroupCase2() throws Exception {
		assertMatch("[a-c-e]", "e", true);
	}

	@Test
	public void testInversedGroupCase0() throws Exception {
		assertMatch("[!b-c]", "a", true);
	}

	@Test
	public void testInversedGroupCase1() throws Exception {
		assertMatch("[!b-c]", "b", false);
	}

	@Test
	public void testInversedGroupCase2() throws Exception {
		assertMatch("[!b-c]", "c", false);
	}

	@Test
	public void testInversedGroupCase3() throws Exception {
		assertMatch("[!b-c]", "d", true);
	}

	@Test
	public void testAlphaGroupCase0() throws Exception {
		assertMatch("[[:alpha:]]", "d", true);
	}

	@Test
	public void testAlphaGroupCase1() throws Exception {
		assertMatch("[[:alpha:]]", ":", false);
	}

	@Test
	public void testAlphaGroupCase2() throws Exception {
		// \u00f6 = 'o' with dots on it
		assertMatch("[[:alpha:]]", "\u00f6", true);
	}

	@Test
	public void test2AlphaGroupsCase0() throws Exception {
		// \u00f6 = 'o' with dots on it
		assertMatch("[[:alpha:]][[:alpha:]]", "a\u00f6", true);
		assertMatch("[[:alpha:]][[:alpha:]]", "a1", false);
	}

	@Test
	public void testAlnumGroupCase0() throws Exception {
		assertMatch("[[:alnum:]]", "a", true);
	}

	@Test
	public void testAlnumGroupCase1() throws Exception {
		assertMatch("[[:alnum:]]", "1", true);
	}

	@Test
	public void testAlnumGroupCase2() throws Exception {
		assertMatch("[[:alnum:]]", ":", false);
	}

	@Test
	public void testBlankGroupCase0() throws Exception {
		assertMatch("[[:blank:]]", " ", true);
	}

	@Test
	public void testBlankGroupCase1() throws Exception {
		assertMatch("[[:blank:]]", "\t", true);
	}

	@Test
	public void testBlankGroupCase2() throws Exception {
		assertMatch("[[:blank:]]", "\r", false);
	}

	@Test
	public void testBlankGroupCase3() throws Exception {
		assertMatch("[[:blank:]]", "\n", false);
	}

	@Test
	public void testBlankGroupCase4() throws Exception {
		assertMatch("[[:blank:]]", "a", false);
	}

	@Test
	public void testCntrlGroupCase0() throws Exception {
		assertMatch("[[:cntrl:]]", "a", false);
	}

	@Test
	public void testCntrlGroupCase1() throws Exception {
		assertMatch("[[:cntrl:]]", String.valueOf((char) 7), true);
	}

	@Test
	public void testDigitGroupCase0() throws Exception {
		assertMatch("[[:digit:]]", "0", true);
	}

	@Test
	public void testDigitGroupCase1() throws Exception {
		assertMatch("[[:digit:]]", "5", true);
	}

	@Test
	public void testDigitGroupCase2() throws Exception {
		assertMatch("[[:digit:]]", "9", true);
	}

	@Test
	public void testDigitGroupCase3() throws Exception {
		// \u06f9 = EXTENDED ARABIC-INDIC DIGIT NINE
		assertMatch("[[:digit:]]", "\u06f9", true);
	}

	@Test
	public void testDigitGroupCase4() throws Exception {
		assertMatch("[[:digit:]]", "a", false);
	}

	@Test
	public void testDigitGroupCase5() throws Exception {
		assertMatch("[[:digit:]]", "]", false);
	}

	@Test
	public void testGraphGroupCase0() throws Exception {
		assertMatch("[[:graph:]]", "]", true);
	}

	@Test
	public void testGraphGroupCase1() throws Exception {
		assertMatch("[[:graph:]]", "a", true);
	}

	@Test
	public void testGraphGroupCase2() throws Exception {
		assertMatch("[[:graph:]]", ".", true);
	}

	@Test
	public void testGraphGroupCase3() throws Exception {
		assertMatch("[[:graph:]]", "0", true);
	}

	@Test
	public void testGraphGroupCase4() throws Exception {
		assertMatch("[[:graph:]]", " ", false);
	}

	@Test
	public void testGraphGroupCase5() throws Exception {
		// \u00f6 = 'o' with dots on it
		assertMatch("[[:graph:]]", "\u00f6", true);
	}

	@Test
	public void testLowerGroupCase0() throws Exception {
		assertMatch("[[:lower:]]", "a", true);
	}

	@Test
	public void testLowerGroupCase1() throws Exception {
		assertMatch("[[:lower:]]", "h", true);
	}

	@Test
	public void testLowerGroupCase2() throws Exception {
		assertMatch("[[:lower:]]", "A", false);
	}

	@Test
	public void testLowerGroupCase3() throws Exception {
		assertMatch("[[:lower:]]", "H", false);
	}

	@Test
	public void testLowerGroupCase4() throws Exception {
		// \u00e4 = small 'a' with dots on it
		assertMatch("[[:lower:]]", "\u00e4", true);
	}

	@Test
	public void testLowerGroupCase5() throws Exception {
		assertMatch("[[:lower:]]", ".", false);
	}

	@Test
	public void testPrintGroupCase0() throws Exception {
		assertMatch("[[:print:]]", "]", true);
	}

	@Test
	public void testPrintGroupCase1() throws Exception {
		assertMatch("[[:print:]]", "a", true);
	}

	@Test
	public void testPrintGroupCase2() throws Exception {
		assertMatch("[[:print:]]", ".", true);
	}

	@Test
	public void testPrintGroupCase3() throws Exception {
		assertMatch("[[:print:]]", "0", true);
	}

	@Test
	public void testPrintGroupCase4() throws Exception {
		assertMatch("[[:print:]]", " ", true);
	}

	@Test
	public void testPrintGroupCase5() throws Exception {
		// \u00f6 = 'o' with dots on it
		assertMatch("[[:print:]]", "\u00f6", true);
	}

	@Test
	public void testPunctGroupCase0() throws Exception {
		assertMatch("[[:punct:]]", ".", true);
	}

	@Test
	public void testPunctGroupCase1() throws Exception {
		assertMatch("[[:punct:]]", "@", true);
	}

	@Test
	public void testPunctGroupCase2() throws Exception {
		assertMatch("[[:punct:]]", " ", false);
	}

	@Test
	public void testPunctGroupCase3() throws Exception {
		assertMatch("[[:punct:]]", "a", false);
	}

	@Test
	public void testSpaceGroupCase0() throws Exception {
		assertMatch("[[:space:]]", " ", true);
	}

	@Test
	public void testSpaceGroupCase1() throws Exception {
		assertMatch("[[:space:]]", "\t", true);
	}

	@Test
	public void testSpaceGroupCase2() throws Exception {
		assertMatch("[[:space:]]", "\r", true);
	}

	@Test
	public void testSpaceGroupCase3() throws Exception {
		assertMatch("[[:space:]]", "\n", true);
	}

	@Test
	public void testSpaceGroupCase4() throws Exception {
		assertMatch("[[:space:]]", "a", false);
	}

	@Test
	public void testUpperGroupCase0() throws Exception {
		assertMatch("[[:upper:]]", "a", false);
	}

	@Test
	public void testUpperGroupCase1() throws Exception {
		assertMatch("[[:upper:]]", "h", false);
	}

	@Test
	public void testUpperGroupCase2() throws Exception {
		assertMatch("[[:upper:]]", "A", true);
	}

	@Test
	public void testUpperGroupCase3() throws Exception {
		assertMatch("[[:upper:]]", "H", true);
	}

	@Test
	public void testUpperGroupCase4() throws Exception {
		// \u00c4 = 'A' with dots on it
		assertMatch("[[:upper:]]", "\u00c4", true);
	}

	@Test
	public void testUpperGroupCase5() throws Exception {
		assertMatch("[[:upper:]]", ".", false);
	}

	@Test
	public void testXDigitGroupCase0() throws Exception {
		assertMatch("[[:xdigit:]]", "a", true);
	}

	@Test
	public void testXDigitGroupCase1() throws Exception {
		assertMatch("[[:xdigit:]]", "d", true);
	}

	@Test
	public void testXDigitGroupCase2() throws Exception {
		assertMatch("[[:xdigit:]]", "f", true);
	}

	@Test
	public void testXDigitGroupCase3() throws Exception {
		assertMatch("[[:xdigit:]]", "0", true);
	}

	@Test
	public void testXDigitGroupCase4() throws Exception {
		assertMatch("[[:xdigit:]]", "5", true);
	}

	@Test
	public void testXDigitGroupCase5() throws Exception {
		assertMatch("[[:xdigit:]]", "9", true);
	}

	@Test
	public void testXDigitGroupCase6() throws Exception {
		assertMatch("[[:xdigit:]]", "۹", false);
	}

	@Test
	public void testXDigitGroupCase7() throws Exception {
		assertMatch("[[:xdigit:]]", ".", false);
	}

	@Test
	public void testWordGroupCase0() throws Exception {
		assertMatch("[[:word:]]", "g", true);
	}

	@Test
	public void testWordGroupCase1() throws Exception {
		// \u00f6 = 'o' with dots on it
		assertMatch("[[:word:]]", "\u00f6", true);
	}

	@Test
	public void testWordGroupCase2() throws Exception {
		assertMatch("[[:word:]]", "5", true);
	}

	@Test
	public void testWordGroupCase3() throws Exception {
		assertMatch("[[:word:]]", "_", true);
	}

	@Test
	public void testWordGroupCase4() throws Exception {
		assertMatch("[[:word:]]", " ", false);
	}

	@Test
	public void testWordGroupCase5() throws Exception {
		assertMatch("[[:word:]]", ".", false);
	}

	@Test
	public void testMixedGroupCase0() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "A", true);
	}

	@Test
	public void testMixedGroupCase1() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "C", true);
	}

	@Test
	public void testMixedGroupCase2() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "e", true);
	}

	@Test
	public void testMixedGroupCase3() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "3", true);
	}

	@Test
	public void testMixedGroupCase4() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "4", true);
	}

	@Test
	public void testMixedGroupCase5() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "5", true);
	}

	@Test
	public void testMixedGroupCase6() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "B", false);
	}

	@Test
	public void testMixedGroupCase7() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "2", false);
	}

	@Test
	public void testMixedGroupCase8() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "6", false);
	}

	@Test
	public void testMixedGroupCase9() throws Exception {
		assertMatch("[A[:lower:]C3-5]", ".", false);
	}

	@Test
	public void testSpecialGroupCase0() throws Exception {
		assertMatch("[[]", "[", true);
	}

	@Test
	public void testSpecialGroupCase1() throws Exception {
		assertMatch("[]]", "]", true);
	}

	@Test
	public void testSpecialGroupCase2() throws Exception {
		assertMatch("[]a]", "]", true);
	}

	@Test
	public void testSpecialGroupCase3() throws Exception {
		assertMatch("[a[]", "[", true);
	}

	@Test
	public void testSpecialGroupCase4() throws Exception {
		assertMatch("[a[]", "a", true);
	}

	@Test
	public void testSpecialGroupCase5() throws Exception {
		assertMatch("[!]]", "]", false);
	}

	@Test
	public void testSpecialGroupCase6() throws Exception {
		assertMatch("[!]]", "x", true);
	}

	@Test
	public void testSpecialGroupCase7() throws Exception {
		assertMatch("[:]]", ":]", true);
	}

	@Test
	public void testSpecialGroupCase8() throws Exception {
		assertMatch("[:]]", ":", false);
	}

	@Test
	public void testSpecialGroupCase9() throws Exception {
		assertMatch("][", "][", false);
	}

	@Test
	public void testSpecialGroupCase10() throws Exception {
		// Second bracket is threated literally, so both [ and : should match
		assertMatch("[[:]", ":", true);
		assertMatch("[[:]", "[", true);
	}

	@Test
	public void testUnsupportedGroupCase0() throws Exception {
		assertMatch("[[=a=]]", "a", false);
		assertMatch("[[=a=]]", "=", false);
		assertMatch("[=a=]", "a", true);
		assertMatch("[=a=]", "=", true);
	}

	@Test
	public void testUnsupportedGroupCase01() throws Exception {
		assertMatch("[.a.]*[.a.]", "aha", true);
	}

	@Test
	public void testUnsupportedGroupCase1() throws Exception {
		assertMatch("[[.a.]]", "a", false);
		assertMatch("[[.a.]]", ".", false);
		assertMatch("[.a.]", "a", true);
		assertMatch("[.a.]", ".", true);
	}

	@Test
	public void testEscapedBracket1() throws Exception {
		assertMatch("\\[", "[", true);
	}

	@Test
	public void testEscapedBracket2() throws Exception {
		assertMatch("\\[[a]", "[", false);
	}

	@Test
	public void testEscapedBracket3() throws Exception {
		assertMatch("\\[[a]", "a", false);
	}

	@Test
	public void testEscapedBracket4() throws Exception {
		assertMatch("\\[[a]", "[a", true);
	}

	@Test
	public void testEscapedBracket5() throws Exception {
		assertMatch("[a\\]]", "]", true);
	}

	@Test
	public void testEscapedBracket6() throws Exception {
		assertMatch("[a\\]]", "a", true);
	}

	@Test
	public void testIgnoredBackslash() throws Exception {
		// In Git CLI a\b\c is equal to abc
		assertMatch("a\\b\\c", "abc", true);
	}

	@Test
	public void testEscapedBackslash() throws Exception {
		// In Git CLI a\\b matches a\b file
		assertMatch("a\\\\b", "a\\b", true);
		assertMatch("a\\\\b\\c", "a\\bc", true);

	}

	@Test
	public void testEscapedExclamationMark() throws Exception {
		assertMatch("\\!b!.txt", "!b!.txt", true);
		assertMatch("a\\!b!.txt", "a!b!.txt", true);
	}

	@Test
	public void testEscapedHash() throws Exception {
		assertMatch("\\#b", "#b", true);
		assertMatch("a\\#", "a#", true);
	}

	@Test
	public void testEscapedTrailingSpaces() throws Exception {
		assertMatch("\\ ", " ", true);
		assertMatch("a\\ ", "a ", true);
	}

	@Test
	public void testNotEscapingBackslash() throws Exception {
		assertMatch("\\out", "out", true);
		assertMatch("\\out", "a/out", true);
		assertMatch("c:\\/", "c:/", true);
		assertMatch("c:\\/", "a/c:/", true);
		assertMatch("c:\\tmp", "c:tmp", true);
		assertMatch("c:\\tmp", "a/c:tmp", true);
	}

	@Test
	public void testMultipleEscapedCharacters1() throws Exception {
		assertMatch("\\]a?c\\*\\[d\\?\\]", "]abc*[d?]", true);
	}

	@Test
	public void testBackslash() throws Exception {
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
	public void testDollar() throws Exception {
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
	public void testCaret() throws Exception {
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
	public void testPlus() throws Exception {
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
	public void testPipe() throws Exception {
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
	public void testBrackets() throws Exception {
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
	public void testBracketsUnmatched1() throws Exception {
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
	public void testBracketsUnmatched2() throws Exception {
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
	public void testBracketsRandom() throws Exception {
		assertMatch("[\\]", "[$0+//r4a\\d]", false);
		assertMatch("[:]]sZX]", "[:]]sZX]", false);
		assertMatch("[:]]:]]]", "[:]]:]]]", false);
	}

	@Test
	public void testFilePathSimpleCase() throws Exception {
		assertFileNameMatch("a/b", "a/b", true);
	}

	@Test
	public void testFilePathCase0() throws Exception {
		assertFileNameMatch("a*b", "a/b", false);
	}

	@Test
	public void testFilePathCase1() throws Exception {
		assertFileNameMatch("a?b", "a/b", false);
	}

	@Test
	public void testFilePathCase2() throws Exception {
		assertFileNameMatch("a*b", "a\\b", true);
	}

	@Test
	public void testFilePathCase3() throws Exception {
		assertFileNameMatch("a?b", "a\\b", true);
	}

}

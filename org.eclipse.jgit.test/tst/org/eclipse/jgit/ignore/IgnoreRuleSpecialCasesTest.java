/*
 * Copyright (C) 2008, Florian Koeberle <florianskarten@web.de>
 * Copyright (C) 2008, Florian Köberle <florianskarten@web.de>
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
package org.eclipse.jgit.ignore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
@SuppressWarnings({ "deprecation", "boxing" })
public class IgnoreRuleSpecialCasesTest {

	@Parameters(name = "OldRule? {0}")
	public static Iterable<Boolean[]> data() {
		return Arrays.asList(new Boolean[][] { { Boolean.FALSE },
				{ Boolean.TRUE } });
	}

	@Parameter
	public Boolean useOldRule;

	private void assertMatch(final String pattern, final String input,
			final boolean matchExpected, Boolean... assume) {
		boolean assumeDir = input.endsWith("/");
		if (useOldRule.booleanValue()) {
			final IgnoreRule matcher = new IgnoreRule(pattern);
			if (assume.length == 0 || !assume[0].booleanValue())
				assertEquals(matchExpected, matcher.isMatch(input, assumeDir));
			else
				assumeTrue(matchExpected == matcher.isMatch(input, assumeDir));
		} else {
			FastIgnoreRule matcher = new FastIgnoreRule(pattern);
			if (assume.length == 0 || !assume[0].booleanValue())
				assertEquals(matchExpected, matcher.isMatch(input, assumeDir));
			else
				assumeTrue(matchExpected == matcher.isMatch(input, assumeDir));
		}
	}

	private void assertFileNameMatch(final String pattern, final String input,
			final boolean matchExpected) {
		boolean assumeDir = input.endsWith("/");
		if (useOldRule.booleanValue()) {
			final IgnoreRule matcher = new IgnoreRule(pattern);
			assertEquals(matchExpected, matcher.isMatch(input, assumeDir));
		} else {
			FastIgnoreRule matcher = new FastIgnoreRule(pattern);
			assertEquals(matchExpected, matcher.isMatch(input, assumeDir));
		}
	}

	@Test
	public void testVerySimplePatternCase0() throws Exception {
		if (useOldRule)
			System.err
					.println("IgnoreRule can't understand blank lines, skipping");
		Boolean assume = useOldRule;
		assertMatch("", "", false, assume);
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
		assertMatch("][", "][", true);
	}

	@Test
	public void testSpecialGroupCase10() throws Exception {
		if (useOldRule)
			System.err.println("IgnoreRule can't understand [[:], skipping");
		Boolean assume = useOldRule;
		// Second bracket is threated literally, so both [ and : should match
		assertMatch("[[:]", ":", true, assume);
		assertMatch("[[:]", "[", true, assume);
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
	public void testEscapedBackslash() throws Exception {
		if (useOldRule)
			System.err
					.println("IgnoreRule can't understand escaped backslashes, skipping");
		Boolean assume = useOldRule;
		// In Git CLI a\\b matches a\b file
		assertMatch("a\\\\b", "a\\b", true, assume);
	}

	@Test
	public void testMultipleEscapedCharacters1() throws Exception {
		assertMatch("\\]a?c\\*\\[d\\?\\]", "]abc*[d?]", true);
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

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

package org.eclipse.jgit.fnmatch;

import static org.eclipse.jgit.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.eclipse.jgit.errors.InvalidPatternException;
import org.junit.Test;

public class FileNameMatcherTest {

	private void assertMatch(final String pattern, final String input,
			final boolean matchExpected, final boolean appendCanMatchExpected)
			throws InvalidPatternException {
		final FileNameMatcher matcher = new FileNameMatcher(pattern, null);
		matcher.append(input);
		assertEquals(matchExpected, matcher.isMatch());
		assertEquals(appendCanMatchExpected, matcher.canAppendMatch());
	}

	private void assertFileNameMatch(final String pattern, final String input,
			final char excludedCharacter, final boolean matchExpected,
			final boolean appendCanMatchExpected)
			throws InvalidPatternException {
		final FileNameMatcher matcher = new FileNameMatcher(pattern,
				new Character(excludedCharacter));
		matcher.append(input);
		assertEquals(matchExpected, matcher.isMatch());
		assertEquals(appendCanMatchExpected, matcher.canAppendMatch());
	}

	@Test
	public void testVerySimplePatternCase0() throws Exception {
		assertMatch("", "", true, false);
	}

	@Test
	public void testVerySimplePatternCase1() throws Exception {
		assertMatch("ab", "a", false, true);
	}

	@Test
	public void testVerySimplePatternCase2() throws Exception {
		assertMatch("ab", "ab", true, false);
	}

	@Test
	public void testVerySimplePatternCase3() throws Exception {
		assertMatch("ab", "ac", false, false);
	}

	@Test
	public void testVerySimplePatternCase4() throws Exception {
		assertMatch("ab", "abc", false, false);
	}

	@Test
	public void testVerySimpleWirdcardCase0() throws Exception {
		assertMatch("?", "a", true, false);
	}

	@Test
	public void testVerySimpleWildCardCase1() throws Exception {
		assertMatch("??", "a", false, true);
	}

	@Test
	public void testVerySimpleWildCardCase2() throws Exception {
		assertMatch("??", "ab", true, false);
	}

	@Test
	public void testVerySimpleWildCardCase3() throws Exception {
		assertMatch("??", "abc", false, false);
	}

	@Test
	public void testVerySimpleStarCase0() throws Exception {
		assertMatch("*", "", true, true);
	}

	@Test
	public void testVerySimpleStarCase1() throws Exception {
		assertMatch("*", "a", true, true);
	}

	@Test
	public void testVerySimpleStarCase2() throws Exception {
		assertMatch("*", "ab", true, true);
	}

	@Test
	public void testSimpleStarCase0() throws Exception {
		assertMatch("a*b", "a", false, true);
	}

	@Test
	public void testSimpleStarCase1() throws Exception {
		assertMatch("a*c", "ac", true, true);
	}

	@Test
	public void testSimpleStarCase2() throws Exception {
		assertMatch("a*c", "ab", false, true);
	}

	@Test
	public void testSimpleStarCase3() throws Exception {
		assertMatch("a*c", "abc", true, true);
	}

	@Test
	public void testManySolutionsCase0() throws Exception {
		assertMatch("a*a*a", "aaa", true, true);
	}

	@Test
	public void testManySolutionsCase1() throws Exception {
		assertMatch("a*a*a", "aaaa", true, true);
	}

	@Test
	public void testManySolutionsCase2() throws Exception {
		assertMatch("a*a*a", "ababa", true, true);
	}

	@Test
	public void testManySolutionsCase3() throws Exception {
		assertMatch("a*a*a", "aaaaaaaa", true, true);
	}

	@Test
	public void testManySolutionsCase4() throws Exception {
		assertMatch("a*a*a", "aaaaaaab", false, true);
	}

	@Test
	public void testVerySimpleGroupCase0() throws Exception {
		assertMatch("[ab]", "a", true, false);
	}

	@Test
	public void testVerySimpleGroupCase1() throws Exception {
		assertMatch("[ab]", "b", true, false);
	}

	@Test
	public void testVerySimpleGroupCase2() throws Exception {
		assertMatch("[ab]", "ab", false, false);
	}

	@Test
	public void testVerySimpleGroupRangeCase0() throws Exception {
		assertMatch("[b-d]", "a", false, false);
	}

	@Test
	public void testVerySimpleGroupRangeCase1() throws Exception {
		assertMatch("[b-d]", "b", true, false);
	}

	@Test
	public void testVerySimpleGroupRangeCase2() throws Exception {
		assertMatch("[b-d]", "c", true, false);
	}

	@Test
	public void testVerySimpleGroupRangeCase3() throws Exception {
		assertMatch("[b-d]", "d", true, false);
	}

	@Test
	public void testVerySimpleGroupRangeCase4() throws Exception {
		assertMatch("[b-d]", "e", false, false);
	}

	@Test
	public void testVerySimpleGroupRangeCase5() throws Exception {
		assertMatch("[b-d]", "-", false, false);
	}

	@Test
	public void testTwoGroupsCase0() throws Exception {
		assertMatch("[b-d][ab]", "bb", true, false);
	}

	@Test
	public void testTwoGroupsCase1() throws Exception {
		assertMatch("[b-d][ab]", "ca", true, false);
	}

	@Test
	public void testTwoGroupsCase2() throws Exception {
		assertMatch("[b-d][ab]", "fa", false, false);
	}

	@Test
	public void testTwoGroupsCase3() throws Exception {
		assertMatch("[b-d][ab]", "bc", false, false);
	}

	@Test
	public void testTwoRangesInOneGroupCase0() throws Exception {
		assertMatch("[b-ce-e]", "a", false, false);
	}

	@Test
	public void testTwoRangesInOneGroupCase1() throws Exception {
		assertMatch("[b-ce-e]", "b", true, false);
	}

	@Test
	public void testTwoRangesInOneGroupCase2() throws Exception {
		assertMatch("[b-ce-e]", "c", true, false);
	}

	@Test
	public void testTwoRangesInOneGroupCase3() throws Exception {
		assertMatch("[b-ce-e]", "d", false, false);
	}

	@Test
	public void testTwoRangesInOneGroupCase4() throws Exception {
		assertMatch("[b-ce-e]", "e", true, false);
	}

	@Test
	public void testTwoRangesInOneGroupCase5() throws Exception {
		assertMatch("[b-ce-e]", "f", false, false);
	}

	@Test
	public void testIncompleteRangesInOneGroupCase0() throws Exception {
		assertMatch("a[b-]", "ab", true, false);
	}

	@Test
	public void testIncompleteRangesInOneGroupCase1() throws Exception {
		assertMatch("a[b-]", "ac", false, false);
	}

	@Test
	public void testIncompleteRangesInOneGroupCase2() throws Exception {
		assertMatch("a[b-]", "a-", true, false);
	}

	@Test
	public void testCombinedRangesInOneGroupCase0() throws Exception {
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
	public void testCombinedRangesInOneGroupCase1() throws Exception {
		assertMatch("[a-c-e]", "d", false, false);
	}

	@Test
	public void testCombinedRangesInOneGroupCase2() throws Exception {
		assertMatch("[a-c-e]", "e", true, false);
	}

	@Test
	public void testInversedGroupCase0() throws Exception {
		assertMatch("[!b-c]", "a", true, false);
	}

	@Test
	public void testInversedGroupCase1() throws Exception {
		assertMatch("[!b-c]", "b", false, false);
	}

	@Test
	public void testInversedGroupCase2() throws Exception {
		assertMatch("[!b-c]", "c", false, false);
	}

	@Test
	public void testInversedGroupCase3() throws Exception {
		assertMatch("[!b-c]", "d", true, false);
	}

	@Test
	public void testAlphaGroupCase0() throws Exception {
		assertMatch("[[:alpha:]]", "d", true, false);
	}

	@Test
	public void testAlphaGroupCase1() throws Exception {
		assertMatch("[[:alpha:]]", ":", false, false);
	}

	@Test
	public void testAlphaGroupCase2() throws Exception {
		// \u00f6 = 'o' with dots on it
		assertMatch("[[:alpha:]]", "\u00f6", true, false);
	}

	@Test
	public void test2AlphaGroupsCase0() throws Exception {
		// \u00f6 = 'o' with dots on it
		assertMatch("[[:alpha:]][[:alpha:]]", "a\u00f6", true, false);
		assertMatch("[[:alpha:]][[:alpha:]]", "a1", false, false);
	}

	@Test
	public void testAlnumGroupCase0() throws Exception {
		assertMatch("[[:alnum:]]", "a", true, false);
	}

	@Test
	public void testAlnumGroupCase1() throws Exception {
		assertMatch("[[:alnum:]]", "1", true, false);
	}

	@Test
	public void testAlnumGroupCase2() throws Exception {
		assertMatch("[[:alnum:]]", ":", false, false);
	}

	@Test
	public void testBlankGroupCase0() throws Exception {
		assertMatch("[[:blank:]]", " ", true, false);
	}

	@Test
	public void testBlankGroupCase1() throws Exception {
		assertMatch("[[:blank:]]", "\t", true, false);
	}

	@Test
	public void testBlankGroupCase2() throws Exception {
		assertMatch("[[:blank:]]", "\r", false, false);
	}

	@Test
	public void testBlankGroupCase3() throws Exception {
		assertMatch("[[:blank:]]", "\n", false, false);
	}

	@Test
	public void testBlankGroupCase4() throws Exception {
		assertMatch("[[:blank:]]", "a", false, false);
	}

	@Test
	public void testCntrlGroupCase0() throws Exception {
		assertMatch("[[:cntrl:]]", "a", false, false);
	}

	@Test
	public void testCntrlGroupCase1() throws Exception {
		assertMatch("[[:cntrl:]]", String.valueOf((char) 7), true, false);
	}

	@Test
	public void testDigitGroupCase0() throws Exception {
		assertMatch("[[:digit:]]", "0", true, false);
	}

	@Test
	public void testDigitGroupCase1() throws Exception {
		assertMatch("[[:digit:]]", "5", true, false);
	}

	@Test
	public void testDigitGroupCase2() throws Exception {
		assertMatch("[[:digit:]]", "9", true, false);
	}

	@Test
	public void testDigitGroupCase3() throws Exception {
		// \u06f9 = EXTENDED ARABIC-INDIC DIGIT NINE
		assertMatch("[[:digit:]]", "\u06f9", true, false);
	}

	@Test
	public void testDigitGroupCase4() throws Exception {
		assertMatch("[[:digit:]]", "a", false, false);
	}

	@Test
	public void testDigitGroupCase5() throws Exception {
		assertMatch("[[:digit:]]", "]", false, false);
	}

	@Test
	public void testGraphGroupCase0() throws Exception {
		assertMatch("[[:graph:]]", "]", true, false);
	}

	@Test
	public void testGraphGroupCase1() throws Exception {
		assertMatch("[[:graph:]]", "a", true, false);
	}

	@Test
	public void testGraphGroupCase2() throws Exception {
		assertMatch("[[:graph:]]", ".", true, false);
	}

	@Test
	public void testGraphGroupCase3() throws Exception {
		assertMatch("[[:graph:]]", "0", true, false);
	}

	@Test
	public void testGraphGroupCase4() throws Exception {
		assertMatch("[[:graph:]]", " ", false, false);
	}

	@Test
	public void testGraphGroupCase5() throws Exception {
		// \u00f6 = 'o' with dots on it
		assertMatch("[[:graph:]]", "\u00f6", true, false);
	}

	@Test
	public void testLowerGroupCase0() throws Exception {
		assertMatch("[[:lower:]]", "a", true, false);
	}

	@Test
	public void testLowerGroupCase1() throws Exception {
		assertMatch("[[:lower:]]", "h", true, false);
	}

	@Test
	public void testLowerGroupCase2() throws Exception {
		assertMatch("[[:lower:]]", "A", false, false);
	}

	@Test
	public void testLowerGroupCase3() throws Exception {
		assertMatch("[[:lower:]]", "H", false, false);
	}

	@Test
	public void testLowerGroupCase4() throws Exception {
		// \u00e4 = small 'a' with dots on it
		assertMatch("[[:lower:]]", "\u00e4", true, false);
	}

	@Test
	public void testLowerGroupCase5() throws Exception {
		assertMatch("[[:lower:]]", ".", false, false);
	}

	@Test
	public void testPrintGroupCase0() throws Exception {
		assertMatch("[[:print:]]", "]", true, false);
	}

	@Test
	public void testPrintGroupCase1() throws Exception {
		assertMatch("[[:print:]]", "a", true, false);
	}

	@Test
	public void testPrintGroupCase2() throws Exception {
		assertMatch("[[:print:]]", ".", true, false);
	}

	@Test
	public void testPrintGroupCase3() throws Exception {
		assertMatch("[[:print:]]", "0", true, false);
	}

	@Test
	public void testPrintGroupCase4() throws Exception {
		assertMatch("[[:print:]]", " ", true, false);
	}

	@Test
	public void testPrintGroupCase5() throws Exception {
		// \u00f6 = 'o' with dots on it
		assertMatch("[[:print:]]", "\u00f6", true, false);
	}

	@Test
	public void testPunctGroupCase0() throws Exception {
		assertMatch("[[:punct:]]", ".", true, false);
	}

	@Test
	public void testPunctGroupCase1() throws Exception {
		assertMatch("[[:punct:]]", "@", true, false);
	}

	@Test
	public void testPunctGroupCase2() throws Exception {
		assertMatch("[[:punct:]]", " ", false, false);
	}

	@Test
	public void testPunctGroupCase3() throws Exception {
		assertMatch("[[:punct:]]", "a", false, false);
	}

	@Test
	public void testSpaceGroupCase0() throws Exception {
		assertMatch("[[:space:]]", " ", true, false);
	}

	@Test
	public void testSpaceGroupCase1() throws Exception {
		assertMatch("[[:space:]]", "\t", true, false);
	}

	@Test
	public void testSpaceGroupCase2() throws Exception {
		assertMatch("[[:space:]]", "\r", true, false);
	}

	@Test
	public void testSpaceGroupCase3() throws Exception {
		assertMatch("[[:space:]]", "\n", true, false);
	}

	@Test
	public void testSpaceGroupCase4() throws Exception {
		assertMatch("[[:space:]]", "a", false, false);
	}

	@Test
	public void testUpperGroupCase0() throws Exception {
		assertMatch("[[:upper:]]", "a", false, false);
	}

	@Test
	public void testUpperGroupCase1() throws Exception {
		assertMatch("[[:upper:]]", "h", false, false);
	}

	@Test
	public void testUpperGroupCase2() throws Exception {
		assertMatch("[[:upper:]]", "A", true, false);
	}

	@Test
	public void testUpperGroupCase3() throws Exception {
		assertMatch("[[:upper:]]", "H", true, false);
	}

	@Test
	public void testUpperGroupCase4() throws Exception {
		// \u00c4 = 'A' with dots on it
		assertMatch("[[:upper:]]", "\u00c4", true, false);
	}

	@Test
	public void testUpperGroupCase5() throws Exception {
		assertMatch("[[:upper:]]", ".", false, false);
	}

	@Test
	public void testXDigitGroupCase0() throws Exception {
		assertMatch("[[:xdigit:]]", "a", true, false);
	}

	@Test
	public void testXDigitGroupCase1() throws Exception {
		assertMatch("[[:xdigit:]]", "d", true, false);
	}

	@Test
	public void testXDigitGroupCase2() throws Exception {
		assertMatch("[[:xdigit:]]", "f", true, false);
	}

	@Test
	public void testXDigitGroupCase3() throws Exception {
		assertMatch("[[:xdigit:]]", "0", true, false);
	}

	@Test
	public void testXDigitGroupCase4() throws Exception {
		assertMatch("[[:xdigit:]]", "5", true, false);
	}

	@Test
	public void testXDigitGroupCase5() throws Exception {
		assertMatch("[[:xdigit:]]", "9", true, false);
	}

	@Test
	public void testXDigitGroupCase6() throws Exception {
		assertMatch("[[:xdigit:]]", "۹", false, false);
	}

	@Test
	public void testXDigitGroupCase7() throws Exception {
		assertMatch("[[:xdigit:]]", ".", false, false);
	}

	@Test
	public void testWordroupCase0() throws Exception {
		assertMatch("[[:word:]]", "g", true, false);
	}

	@Test
	public void testWordroupCase1() throws Exception {
		// \u00f6 = 'o' with dots on it
		assertMatch("[[:word:]]", "\u00f6", true, false);
	}

	@Test
	public void testWordroupCase2() throws Exception {
		assertMatch("[[:word:]]", "5", true, false);
	}

	@Test
	public void testWordroupCase3() throws Exception {
		assertMatch("[[:word:]]", "_", true, false);
	}

	@Test
	public void testWordroupCase4() throws Exception {
		assertMatch("[[:word:]]", " ", false, false);
	}

	@Test
	public void testWordroupCase5() throws Exception {
		assertMatch("[[:word:]]", ".", false, false);
	}

	@Test
	public void testMixedGroupCase0() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "A", true, false);
	}

	@Test
	public void testMixedGroupCase1() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "C", true, false);
	}

	@Test
	public void testMixedGroupCase2() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "e", true, false);
	}

	@Test
	public void testMixedGroupCase3() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "3", true, false);
	}

	@Test
	public void testMixedGroupCase4() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "4", true, false);
	}

	@Test
	public void testMixedGroupCase5() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "5", true, false);
	}

	@Test
	public void testMixedGroupCase6() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "B", false, false);
	}

	@Test
	public void testMixedGroupCase7() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "2", false, false);
	}

	@Test
	public void testMixedGroupCase8() throws Exception {
		assertMatch("[A[:lower:]C3-5]", "6", false, false);
	}

	@Test
	public void testMixedGroupCase9() throws Exception {
		assertMatch("[A[:lower:]C3-5]", ".", false, false);
	}

	@Test
	public void testSpecialGroupCase0() throws Exception {
		assertMatch("[[]", "[", true, false);
	}

	@Test
	public void testSpecialGroupCase1() throws Exception {
		assertMatch("[]]", "]", true, false);
	}

	@Test
	public void testSpecialGroupCase2() throws Exception {
		assertMatch("[]a]", "]", true, false);
	}

	@Test
	public void testSpecialGroupCase3() throws Exception {
		assertMatch("[a[]", "[", true, false);
	}

	@Test
	public void testSpecialGroupCase4() throws Exception {
		assertMatch("[a[]", "a", true, false);
	}

	@Test
	public void testSpecialGroupCase5() throws Exception {
		assertMatch("[!]]", "]", false, false);
	}

	@Test
	public void testSpecialGroupCase6() throws Exception {
		assertMatch("[!]]", "x", true, false);
	}

	@Test
	public void testSpecialGroupCase7() throws Exception {
		assertMatch("[:]]", ":]", true, false);
	}

	@Test
	public void testSpecialGroupCase8() throws Exception {
		assertMatch("[:]]", ":", false, true);
	}

	@Test
	public void testSpecialGroupCase9() throws Exception {
		try {
			assertMatch("[[:]", ":", true, true);
			fail("InvalidPatternException expected");
		} catch (InvalidPatternException e) {
			// expected
		}
	}

	@Test
	public void testUnsupportedGroupCase0() throws Exception {
		try {
			assertMatch("[[=a=]]", "b", false, false);
			fail("InvalidPatternException expected");
		} catch (InvalidPatternException e) {
			assertTrue(e.getMessage().contains("[=a=]"));
		}
	}

	@Test
	public void testUnsupportedGroupCase1() throws Exception {
		try {
			assertMatch("[[.a.]]", "b", false, false);
			fail("InvalidPatternException expected");
		} catch (InvalidPatternException e) {
			assertTrue(e.getMessage().contains("[.a.]"));
		}
	}

	@Test
	public void testFilePathSimpleCase() throws Exception {
		assertFileNameMatch("a/b", "a/b", '/', true, false);
	}

	@Test
	public void testFilePathCase0() throws Exception {
		assertFileNameMatch("a*b", "a/b", '/', false, false);
	}

	@Test
	public void testFilePathCase1() throws Exception {
		assertFileNameMatch("a?b", "a/b", '/', false, false);
	}

	@Test
	public void testFilePathCase2() throws Exception {
		assertFileNameMatch("a*b", "a\\b", '\\', false, false);
	}

	@Test
	public void testFilePathCase3() throws Exception {
		assertFileNameMatch("a?b", "a\\b", '\\', false, false);
	}

	@Test
	public void testReset() throws Exception {
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
	public void testCreateMatcherForSuffix() throws Exception {
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
	public void testCopyConstructor() throws Exception {
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

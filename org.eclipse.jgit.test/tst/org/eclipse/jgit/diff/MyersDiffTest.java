/*
 * Copyright (C) 2009, Johannes E. Schindelin
 * Copyright (C) 2009, Johannes Schindelin <johannes.schindelin@gmx.de>
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

import junit.framework.TestCase;

public class MyersDiffTest extends TestCase {
	public void testAtEnd() {
		assertDiff("HELLO", "HELL", " -4,1 +4,0");
	}

	public void testAtStart() {
		assertDiff("Git", "JGit", " -0,0 +0,1");
	}

	public void testSimple() {
		assertDiff("HELLO WORLD", "LOW",
			" -0,3 +0,0 -5,1 +2,0 -7,4 +3,0");
		// is ambiguous, could be this, too:
		// " -0,2 +0,0 -3,1 +1,0 -5,1 +2,0 -7,4 +3,0"
	}

	public void assertDiff(String a, String b, String edits) {
		MyersDiff diff = new MyersDiff(toCharArray(a), toCharArray(b));
		assertEquals(edits, toString(diff.getEdits()));
	}

	private static String toString(EditList list) {
		StringBuilder builder = new StringBuilder();
		for (Edit e : list)
			builder.append(" -" + e.beginA
					+ "," + (e.endA - e.beginA)
				+ " +" + e.beginB + "," + (e.endB - e.beginB));
		return builder.toString();
	}

	private static CharArray toCharArray(String s) {
		return new CharArray(s);
	}

	protected static String toString(Sequence seq, int begin, int end) {
		CharArray a = (CharArray)seq;
		return new String(a.array, begin, end - begin);
	}

	protected static String toString(CharArray a, CharArray b,
			int x, int k) {
		return "(" + x + "," + (k + x)
			+ (x < 0 ? '<' :
					(x >= a.array.length ?
					 '>' : a.array[x]))
			+ (k + x < 0 ? '<' :
					(k + x >= b.array.length ?
					 '>' : b.array[k + x]))
			+ ")";
	}

	private static class CharArray implements Sequence {
		char[] array;
		public CharArray(String s) { array = s.toCharArray(); }
		public int size() { return array.length; }
		public boolean equals(int i, Sequence other, int j) {
			CharArray o = (CharArray)other;
			return array[i] == o.array[j];
		}
	}
}

/*
 * Copyright (C) 2009, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2009, Christian Halstrick, SAP AG
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

/**
 * Test cases for the performance of the diff implementation. Currently this
 * "TestCase" doesn't contain any assertions but only prints out performance
 * data of the diff algorithm. Nonetheless this "TestCase" has already revealed
 * bugs in the algorithm because it stresses the algorithm with huge amount of
 * data leading to OutOfBoundsExceptions.
 * 
 * @TODO test show that performance decreases non-linear. Check out why
 */
public class MyersDiffPerformanceTest extends TestCase {
	long millis;

	/**
	 * Generate some large chunks test data for performance-testing the diff
	 * 
	 * @param characters
	 *            roughly the number of characters in first generated strring,
	 *            the second one will by around 10% longer
	 * @return a array of size 2 containing the two strings to be compared
	 */
	private String[] generateTestData(int characters) {
		StringBuilder ab = new StringBuilder();
		StringBuilder bb = new StringBuilder();
		for (int i = 0, j = 0; j < characters; ++i) {
			if (i % 13 < 10) {
				j++;
				ab.append(i % 10);
			}
			if (i % 21 < 19) {
				bb.append(i % 10);
			}
		}
		return (new String[] { ab.toString(), bb.toString() });
	}

	public void test1000() {
		test(1000);
	}

	public void test5000() {
		test(5000);
	}

	public void test20000() {
		test(20000);
	}

	public void test50000() {
		test(50000);
	}

	public void test80000() {
		test(80000);
	}

	private void test(int characters) {
		String[] testData = generateTestData(characters);
		CharArray ac = toCharArray(testData[0]);
		CharArray bc = toCharArray(testData[1]);
		millis = System.currentTimeMillis();
		new MyersDiff(ac, bc);
		millis = System.currentTimeMillis() - millis;
		System.out.println("diffing " + ac.size() + " against " + bc.size()
				+ " bytes took " + millis + " ms. " + (ac.size() / millis)
				+ " bytes/ms");
	}

	private static CharArray toCharArray(String s) {
		return new CharArray(s);
	}

	private static class CharArray implements Sequence {
		char[] array;

		public CharArray(String s) {
			array = s.toCharArray();
		}

		public int size() {
			return array.length;
		}

		public boolean equals(int i, Sequence other, int j) {
			CharArray o = (CharArray) other;
			return array[i] == o.array[j];
		}
	}
}

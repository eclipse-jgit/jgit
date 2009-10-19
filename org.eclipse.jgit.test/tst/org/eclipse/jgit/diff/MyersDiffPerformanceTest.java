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

import java.text.DecimalFormat;
import java.text.NumberFormat;

import junit.framework.TestCase;

/**
 * Test cases for the performance of the diff implementation. The tests test
 * that the performance of the MyersDiff algorithm is really O(N*D). Means the
 * time for computing the diff between a and b should depend on the product of
 * a.length+b.length and the number of found differences. The tests compute
 * diffs between chunks of different length, measure the needed time and check
 * that time/(N*D) does not differ more than a certain factor (currently 10)
 */
public class MyersDiffPerformanceTest extends TestCase {
	static PerfData perf1Min, perf1Max;

	public class PerfData {
		private NumberFormat fmt = new DecimalFormat("#.##E0");

		public long t;

		public long D;

		public long N;

		public double perf1() {
			return (t / ((double) N * D));
		}

		public double perf2() {
			return (t / ((double) N * D * D));
		}

		public String toString() {
			return ("diffing " + N / 2 + " bytes took " + t + " ms. N=" + N
					+ ", D=" + D + ", time/(N*D):" + fmt.format(perf1())
					+ ", time/(N*D^2):" + fmt.format(perf2()));
		}
	}

	private void checkPerfData(PerfData p, double maxFaktor) {
		if (perf1Min == null) {
			perf1Min = p;
		}
		if (perf1Max == null) {
			perf1Max = p;
		}
		assertTrue(p.perf1() / perf1Max.perf1() < maxFaktor);
		assertTrue(perf1Min.perf1() / p.perf1() < maxFaktor);
		if (perf1Min.perf1() > p.perf1()) {
			perf1Min = p;
		}
		if (perf1Max.perf1() < p.perf1()) {
			perf1Max = p;
		}
	}

	public void test10000() {
		PerfData p = test(10000);
		checkPerfData(p, 10);
		System.out.println(p);
	}

	public void test20000() {
		PerfData p = test(20000);
		checkPerfData(p, 10);
		System.out.println(p);
	}

	public void test50000() {
		PerfData p = test(50000);
		checkPerfData(p, 10);
		System.out.println(p);
	}

	public void test80000() {
		PerfData p = test(80000);
		checkPerfData(p, 10);
		System.out.println(p);
	}

	public void test99999() {
		PerfData p = test(99999);
		checkPerfData(p, 10);
		System.out.println(p);
	}

	private PerfData test(int characters) {
		PerfData ret = new PerfData();
		String a = DiffTestDataGenerator.generateSequence(characters, 971, 3);
		String b = DiffTestDataGenerator.generateSequence(characters, 1621, 5);
		CharArray ac = toCharArray(a);
		CharArray bc = toCharArray(b);
		long millis = System.currentTimeMillis();
		MyersDiff myersDiff = new MyersDiff(ac, bc);
		millis = System.currentTimeMillis() - millis;
		ret.t = millis;
		ret.N = (ac.size() + bc.size());
		ret.D = myersDiff.getEdits().size();
		return (ret);
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

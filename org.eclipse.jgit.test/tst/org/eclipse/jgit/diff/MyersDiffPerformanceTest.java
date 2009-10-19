/*
 * Copyright (C) 2009, Christian Halstrick <christian.halstrick@sap.com>
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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import junit.framework.TestCase;

import org.eclipse.jgit.util.CPUTimeStopWatch;

/**
 * Test cases for the performance of the diff implementation. The tests test
 * that the performance of the MyersDiff algorithm is really O(N*D). Means the
 * time for computing the diff between a and b should depend on the product of
 * a.length+b.length and the number of found differences. The tests compute
 * diffs between chunks of different length, measure the needed time and check
 * that time/(N*D) does not differ more than a certain factor (currently 10)
 */
public class MyersDiffPerformanceTest extends TestCase {
	private static final long longTaskBoundary = 5000000000L;

	private static final int minCPUTimerTicks = 10;

	private static final int maxFactor = 15;

	private CPUTimeStopWatch stopwatch=CPUTimeStopWatch.createInstance();

	public class PerfData {
		private NumberFormat fmt = new DecimalFormat("#.##E0");

		public long runningTime;

		public long D;

		public long N;

		private double p1 = -1;

		private double p2 = -1;

		public double perf1() {
			if (p1 < 0)
				p1 = runningTime / ((double) N * D);
			return p1;
		}

		public double perf2() {
			if (p2 < 0)
				p2 = runningTime / ((double) N * D * D);
			return p2;
		}

		public String toString() {
			return ("diffing " + N / 2 + " bytes took " + runningTime
					+ " ns. N=" + N + ", D=" + D + ", time/(N*D):"
					+ fmt.format(perf1()) + ", time/(N*D^2):" + fmt
					.format(perf2()));
		}
	}

	public static Comparator<PerfData> getComparator(final int whichPerf) {
		return new Comparator<PerfData>() {
			public int compare(PerfData o1, PerfData o2) {
				double p1 = (whichPerf == 1) ? o1.perf1() : o1.perf2();
				double p2 = (whichPerf == 1) ? o2.perf1() : o2.perf2();
				return (p1 < p2) ? -1 : (p1 > p2) ? 1 : 0;
			}
		};
	}

	public void test() {
		if (stopwatch!=null) {
			List<PerfData> perfData = new LinkedList<PerfData>();
			perfData.add(test(10000));
			perfData.add(test(20000));
			perfData.add(test(50000));
			perfData.add(test(80000));
			perfData.add(test(99999));
			perfData.add(test(999999));

			Comparator<PerfData> c = getComparator(1);
			double factor = Collections.max(perfData, c).perf1()
					/ Collections.min(perfData, c).perf1();
			assertTrue(
					"minimun and maximum of performance-index t/(N*D) differed too much. Measured factor of "
							+ factor
							+ " (maxFactor="
							+ maxFactor
							+ "). Perfdata=<" + perfData.toString() + ">",
					factor < maxFactor);
		}
	}

	/**
	 * Tests the performance of MyersDiff for texts which are similar (not
	 * random data). The CPU time is measured and returned. Because of bad
	 * accuracy of CPU time information the diffs are repeated. During each
	 * repetition the interim CPU time is checked. The diff operation is
	 * repeated until we have seen the CPU time clock changed its value at least
	 * {@link #minCPUTimerTicks} times.
	 *
	 * @param characters
	 *            the size of the diffed character sequences.
	 * @return performance data
	 */
	private PerfData test(int characters) {
		PerfData ret = new PerfData();
		String a = DiffTestDataGenerator.generateSequence(characters, 971, 3);
		String b = DiffTestDataGenerator.generateSequence(characters, 1621, 5);
		CharArray ac = new CharArray(a);
		CharArray bc = new CharArray(b);
		MyersDiff myersDiff = null;
		int cpuTimeChanges = 0;
		long lastReadout = 0;
		long interimTime = 0;
		int repetitions = 0;
		stopwatch.start();
		while (cpuTimeChanges < minCPUTimerTicks && interimTime < longTaskBoundary) {
			myersDiff = new MyersDiff(ac, bc);
			repetitions++;
			interimTime = stopwatch.readout();
			if (interimTime != lastReadout) {
				cpuTimeChanges++;
				lastReadout = interimTime;
			}
		}
		ret.runningTime = stopwatch.stop() / repetitions;
		ret.N = (ac.size() + bc.size());
		ret.D = myersDiff.getEdits().size();

		return ret;
	}

	private static class CharArray implements Sequence {
		private final char[] array;

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

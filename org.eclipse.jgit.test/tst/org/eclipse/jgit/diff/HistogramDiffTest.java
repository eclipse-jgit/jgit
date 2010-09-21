/*
 * Copyright (C) 2010, Google Inc.
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

import org.eclipse.jgit.diff.DiffPerformanceTest.CharArray;
import org.eclipse.jgit.diff.DiffPerformanceTest.CharCmp;

public class HistogramDiffTest extends AbstractDiffTestCase {
	@Override
	protected HistogramDiff algorithm() {
		HistogramDiff hd = new HistogramDiff();
		hd.setFallbackAlgorithm(null);
		return hd;
	}

	public void testEdit_NoUniqueMiddleSide_FlipBlocks() {
		EditList r = diff(t("aRRSSz"), t("aSSRRz"));
		assertEquals(2, r.size());
		assertEquals(new Edit(1, 3, 1, 1), r.get(0)); // DELETE "RR"
		assertEquals(new Edit(5, 5, 3, 5), r.get(1)); // INSERT "RR
	}

	public void testEdit_NoUniqueMiddleSide_Insert2() {
		EditList r = diff(t("aRSz"), t("aRRSSz"));
		assertEquals(1, r.size());
		assertEquals(new Edit(2, 2, 2, 4), r.get(0));
	}

	public void testEdit_NoUniqueMiddleSide_FlipAndExpand() {
		EditList r = diff(t("aRSz"), t("aSSRRz"));
		assertEquals(2, r.size());
		assertEquals(new Edit(1, 2, 1, 1), r.get(0)); // DELETE "R"
		assertEquals(new Edit(3, 3, 2, 5), r.get(1)); // INSERT "SRR"
	}

	public void testExceedsChainLenght_DuringScanOfA() {
		HistogramDiff hd = new HistogramDiff();
		hd.setFallbackAlgorithm(null);
		hd.setMaxChainLength(3);

		SequenceComparator<RawText> cmp = new SequenceComparator<RawText>() {
			@Override
			public boolean equals(RawText a, int ai, RawText b, int bi) {
				return RawTextComparator.DEFAULT.equals(a, ai, b, bi);
			}

			@Override
			public int hash(RawText a, int ai) {
				return 1;
			}
		};

		EditList r = hd.diff(cmp, t("RabS"), t("QabT"));
		assertEquals(1, r.size());
		assertEquals(new Edit(0, 4, 0, 4), r.get(0));
	}

	public void testExceedsChainLenght_DuringScanOfB() {
		HistogramDiff hd = new HistogramDiff();
		hd.setFallbackAlgorithm(null);
		hd.setMaxChainLength(1);

		EditList r = hd.diff(RawTextComparator.DEFAULT, t("RaaS"), t("QaaT"));
		assertEquals(1, r.size());
		assertEquals(new Edit(0, 4, 0, 4), r.get(0));
	}

	public void testFallbackToMyersDiff() {
		HistogramDiff hd = new HistogramDiff();
		hd.setMaxChainLength(64);

		String a = DiffTestDataGenerator.generateSequence(40000, 971, 3);
		String b = DiffTestDataGenerator.generateSequence(40000, 1621, 5);
		CharCmp cmp = new CharCmp();
		CharArray ac = new CharArray(a);
		CharArray bc = new CharArray(b);
		EditList r;

		// Without fallback our results are limited due to collisions.
		hd.setFallbackAlgorithm(null);
		r = hd.diff(cmp, ac, bc);
		assertEquals(70, r.size());

		// Results go when we add a fallback for the high collision regions.
		hd.setFallbackAlgorithm(MyersDiff.INSTANCE);
		r = hd.diff(cmp, ac, bc);
		assertEquals(73, r.size());

		// But they still differ from Myers due to the way we did early steps.
		EditList myersResult = MyersDiff.INSTANCE.diff(cmp, ac, bc);
		assertFalse("Not same as Myers", myersResult.equals(r));
	}

	public void testPerformanceTestDeltaLength() {
		HistogramDiff hd = new HistogramDiff();
		hd.setFallbackAlgorithm(null);

		String a = DiffTestDataGenerator.generateSequence(40000, 971, 3);
		String b = DiffTestDataGenerator.generateSequence(40000, 1621, 5);
		CharCmp cmp = new CharCmp();
		CharArray ac = new CharArray(a);
		CharArray bc = new CharArray(b);
		EditList r;

		hd.setMaxChainLength(64);
		r = hd.diff(cmp, ac, bc);
		assertEquals(70, r.size());

		hd.setMaxChainLength(176);
		r = hd.diff(cmp, ac, bc);
		assertEquals(72, r.size());
	}
}

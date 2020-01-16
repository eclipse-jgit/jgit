/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.diff;

import static org.junit.Assert.assertEquals;

import org.junit.Test;


public class HistogramDiffTest extends AbstractDiffTestCase {
	@Override
	protected HistogramDiff algorithm() {
		HistogramDiff hd = new HistogramDiff();
		hd.setFallbackAlgorithm(null);
		return hd;
	}

	@Test
	public void testEdit_NoUniqueMiddleSide_FlipBlocks() {
		EditList r = diff(t("aRRSSz"), t("aSSRRz"));
		assertEquals(2, r.size());
		assertEquals(new Edit(1, 3, 1, 1), r.get(0)); // DELETE "RR"
		assertEquals(new Edit(5, 5, 3, 5), r.get(1)); // INSERT "RR
	}

	@Test
	public void testEdit_NoUniqueMiddleSide_Insert2() {
		EditList r = diff(t("aRSz"), t("aRRSSz"));
		assertEquals(1, r.size());
		assertEquals(new Edit(2, 2, 2, 4), r.get(0));
	}

	@Test
	public void testEdit_NoUniqueMiddleSide_FlipAndExpand() {
		EditList r = diff(t("aRSz"), t("aSSRRz"));
		assertEquals(2, r.size());
		assertEquals(new Edit(1, 2, 1, 1), r.get(0)); // DELETE "R"
		assertEquals(new Edit(3, 3, 2, 5), r.get(1)); // INSERT "SRR"
	}

	@Test
	public void testEdit_LcsContainsUnique() {
		EditList r = diff(t("nqnjrnjsnm"), t("AnqnjrnjsnjTnmZ"));
		assertEquals(new Edit(0, 0, 0, 1), r.get(0)); // INSERT "A";
		assertEquals(new Edit(9, 9, 10, 13), r.get(1)); // INSERT "jTn";
		assertEquals(new Edit(10, 10, 14, 15), r.get(2)); // INSERT "Z";
		assertEquals(3, r.size());
	}

	@Test
	public void testExceedsChainLength_DuringScanOfA() {
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

	@Test
	public void testExceedsChainLength_DuringScanOfB() {
		HistogramDiff hd = new HistogramDiff();
		hd.setFallbackAlgorithm(null);
		hd.setMaxChainLength(1);

		EditList r = hd.diff(RawTextComparator.DEFAULT, t("RaaS"), t("QaaT"));
		assertEquals(1, r.size());
		assertEquals(new Edit(0, 4, 0, 4), r.get(0));
	}

	@Test
	public void testFallbackToMyersDiff() {
		HistogramDiff hd = new HistogramDiff();
		hd.setMaxChainLength(4);

		RawTextComparator cmp = RawTextComparator.DEFAULT;
		RawText ac = t("bbbbb");
		RawText bc = t("AbCbDbEFbZ");
		EditList r;

		// Without fallback our results are limited due to collisions.
		hd.setFallbackAlgorithm(null);
		r = hd.diff(cmp, ac, bc);
		assertEquals(1, r.size());

		// Results go up when we add a fallback for the high collision regions.
		hd.setFallbackAlgorithm(MyersDiff.INSTANCE);
		r = hd.diff(cmp, ac, bc);
		assertEquals(5, r.size());
	}
}

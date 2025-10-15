/*
 * Copyright (C) 2025, Google LLC.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.internal.storage.pack.PackExt.MULTI_PACK_INDEX;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;

/**
 * Helper class to manipulate a list of packs with (maybe) midxs.
 **/
public final class MidxPackList {

	private static final Comparator<DfsPackFile> PACK_NAME_COMPARATOR = Comparator
			.comparing(pack -> pack.getPackDescription().getPackName());

	/**
	 * Wrap the packs list into a MidxPackList
	 * <p>
	 * The input list is well-formed, doesn't have duplicated packs/midxs.
	 *
	 * @param packs
	 *            list of packs (regular or tip midxs)
	 * @return a MidxPackList instance
	 */
	public static MidxPackList create(DfsPackFile[] packs) {
		return new MidxPackList(packs);
	}

	private final DfsPackFile[] packs;

	private MidxPackList(DfsPackFile[] packs) {
		this.packs = packs;
	}

	/**
	 * Get all plain packs in the list, either top-level or inside midxs
	 *
	 * @return a list of all "real" packs in this pack list, either top level or
	 *         inside midxs.
	 **/
	public List<DfsPackFile> getAllPlainPacks() {
		List<DfsPackFile> plainPacks = new ArrayList<>();
		Queue<DfsPackFile> pending = new ArrayDeque<>(
				Arrays.stream(packs).toList());
		while (!pending.isEmpty()) {
			DfsPackFile pack = pending.poll();
			if (pack instanceof DfsPackFileMidx midxPack) {
				plainPacks.addAll(midxPack.getCoveredPacks());
				if (midxPack.getMultipackIndexBase() != null) {
					pending.add(midxPack.getMultipackIndexBase());
				}
			} else {
				plainPacks.add(pack);
			}
		}
		return plainPacks;
	}

	/**
	 * Get all midx in the list, either top-level or inside other midxs
	 *
	 * @return a list of all midxs in thist list, either top level or nested
	 *         inside other midxs.
	 **/
	public List<DfsPackFileMidx> getAllMidxPacks() {
		List<DfsPackFileMidx> topLevelMidxs = Arrays.stream(packs).filter(
				p -> p.getPackDescription().hasFileExt(MULTI_PACK_INDEX))
				.map(p -> (DfsPackFileMidx) p).toList();

		List<DfsPackFileMidx> midxPacks = new ArrayList<>();
		Queue<DfsPackFileMidx> pending = new ArrayDeque<>(topLevelMidxs);
		while (!pending.isEmpty()) {
			DfsPackFileMidx midx = pending.poll();
			midxPacks.add(midx);
			if (midx.getMultipackIndexBase() != null) {
				pending.add(midx.getMultipackIndexBase());
			}
		}
		return midxPacks;
	}

	/**
	 * Alias for {@link #findAllImpactedMidxs(List)}, as convenience for tests
	 *
	 * @param pack
	 *            a single pack
	 * @return all the midxs that include (directly or indirectly) the pack
	 */
	Set<DfsPackFileMidx> findAllImpactedMidxs(DfsPackFile pack) {
		return findAllImpactedMidxs(List.of(pack));
	}

	/**
	 * Return all the midxs that become invalidated if "toRemove" packs are
	 * removed.
	 * <p>
	 * In a chain like midx3(p6, p5) -> midx2(p4, p3) -> midx1(p2, p1), if we
	 * remove p3, midx3 and midx2 are impacted. midx1 is not, it can still be
	 * used.
	 *
	 * @param toRemove
	 *            subset of packs that are going to be removed
	 * @return set of midxs from the packlist that should also be removed
	 */
	public Set<DfsPackFileMidx> findAllImpactedMidxs(
			List<DfsPackFile> toRemove) {
		if (toRemove.isEmpty()) {
			return Collections.emptySet();
		}

		List<DfsPackFileMidx> topLevelMidxs = Arrays.stream(packs).filter(
				p -> p.getPackDescription().hasFileExt(MULTI_PACK_INDEX))
				.map(p -> (DfsPackFileMidx) p).toList();

		if (topLevelMidxs.isEmpty()) {
			return Collections.emptySet();
		}

		Set<DfsPackFileMidx> impactedMidxs = new TreeSet<>(
				PACK_NAME_COMPARATOR);
		for (DfsPackFileMidx midx : topLevelMidxs) {
			List<DfsPackFileMidx> visitedMidxs = new ArrayList<>();
			DfsPackFileMidx current = midx;
			while (current != null) {
				if (containsAny(current.getCoveredPacks(), toRemove)) {
					impactedMidxs.add(current);
					// Anything above in the chain is also tainted
					impactedMidxs.addAll(visitedMidxs);
					// Reset the list and keep going, maybe something
					// deeper in the chain is also affected
					visitedMidxs.clear();
				} else {
					visitedMidxs.add(current);
				}
				current = current.getMultipackIndexBase();
			}
			visitedMidxs.clear();
		}

		return impactedMidxs;
	}

	private static boolean containsAny(List<DfsPackFile> inMidx,
			List<DfsPackFile> toRemove) {
		Set<DfsPackFile> inMidxSet = asSet(inMidx);
		return toRemove.stream().anyMatch(inMidxSet::contains);
	}

	private static Set<DfsPackFile> asSet(List<DfsPackFile> packs) {
		TreeSet<DfsPackFile> s = new TreeSet<>(PACK_NAME_COMPARATOR);
		s.addAll(packs);
		return s;
	}

}
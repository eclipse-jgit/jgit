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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.internal.storage.pack.PackExt;

/**
 * Format a flat list of packs and midxs into a valid list of packs.
 * <p>
 * A valid list of packs is either:
 * <ul>
 * <li>A list with midxs and uncovered packs</li>
 * <li>A list of packs (without any midx)</li>
 * </ul>
 */
public class MidxPackFilter {

	private MidxPackFilter() {
	}

	/**
	 * Reorganize the flat list of packs removing the midxs
	 *
	 * @param packs
	 *            flat list of all packs in the repo, may include midx. Packs
	 *            covered by the midx appear also on their own.
	 * @return a list of packs without midxs
	 */
	public static List<DfsPackDescription> skipMidxs(
			List<DfsPackDescription> packs) {
		// Covered packs appear also on their own in the list, so we can just
		// take the midx out.
		return packs.stream()
				.filter(desc -> !desc.hasFileExt(PackExt.MULTI_PACK_INDEX))
				.collect(Collectors.toList());
	}

	/**
	 * Remove from the list any packs covered by midxs.
	 * <p>
	 * This verifies that all referenced packs by the midxs exist.
	 *
	 * @param packs
	 *            list of packs with maybe some midxs
	 * @return midxs and uncovered packs. All the input packs if no midx. Ignore
	 *         midxs with missing covered packs.
	 */
	public static List<DfsPackDescription> useMidx(
			List<DfsPackDescription> packs) {
		List<DfsPackDescription> midxs = packs.stream()
				.filter(desc -> desc.hasFileExt(PackExt.MULTI_PACK_INDEX))
				.sorted(DfsPackDescription.objectLookupComparator()).toList();
		if (midxs.isEmpty()) {
			return packs;
		}

		Set<DfsPackDescription> packsSet = new HashSet<>(packs);
		Optional<DfsPackDescription> bestMidx = midxs.stream()
				.filter(midx -> isValid(midx, packsSet)).findFirst();
		if (bestMidx.isEmpty()) {
			return skipMidxs(packs);
		}

		// Take the packs covered by the midxs and other midxs themselves out of
		// the list
		Set<DfsPackDescription> coveredPacksAndMidxs = getAllCoveredPacks(
				bestMidx.get());
		return packs.stream().filter(p -> !coveredPacksAndMidxs.contains(p))
				// At this point, any midx in the list besides bestMidx is a
				// straggler.
				.filter(p -> !p.hasFileExt(PackExt.MULTI_PACK_INDEX)
						|| p.equals(bestMidx.get()))
				.collect(Collectors.toCollection(ArrayList::new));
	}

	private static boolean isValid(DfsPackDescription midx,
			Set<DfsPackDescription> packs) {
		DfsPackDescription tip = midx;
		while (tip != null) {
			if (!packs.containsAll(tip.getCoveredPacks())) {
				return false;
			}

			tip = tip.getMultiPackIndexBase();
		}
		return true;
	}

	private static Set<DfsPackDescription> getAllCoveredPacks(
			DfsPackDescription midx) {
		Set<DfsPackDescription> covered = new HashSet<>();
		DfsPackDescription current = midx;
		while (current != null) {
			if (!current.getCoveredPacks().isEmpty()) {
				covered.addAll(current.getCoveredPacks());
			}

			DfsPackDescription base = current.getMultiPackIndexBase();
			if (base != null) {
				covered.add(base);
			}
			current = base;
		}
		return covered;
	}
}

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

import java.util.HashSet;
import java.util.List;
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
		// Take the packs covered by the midxs out of the list
		List<DfsPackDescription> midxs = packs.stream()
				.filter(desc -> desc.hasFileExt(PackExt.MULTI_PACK_INDEX))
				.toList();
		if (midxs.isEmpty()) {
			return packs;
		}

		Set<DfsPackDescription> inputPacks = new HashSet<>(packs);
		Set<DfsPackDescription> allCoveredPacks = new HashSet<>();
		for (DfsPackDescription midx : midxs) {
			Set<DfsPackDescription> coveredPacks = new HashSet<>();
			findCoveredPacks(midx, coveredPacks);
			if (!inputPacks.containsAll(coveredPacks)) {
				// This midx references packs not in the pack db.
				// It could be part of a chain, so we just ignore all midxs
				throw new IllegalStateException("bla");
				// return skipMidxs(packs);
			}
			allCoveredPacks.addAll(coveredPacks);
		}

		return packs.stream().filter(d -> !allCoveredPacks.contains(d))
				.collect(Collectors.toList());
	}

	private static void findCoveredPacks(DfsPackDescription midx,
			Set<DfsPackDescription> covered) {
		if (!midx.getCoveredPacks().isEmpty()) {
			covered.addAll(midx.getCoveredPacks());
		}

		if (midx.getMultiPackIndexBase() != null) {
			findCoveredPacks(midx.getMultiPackIndexBase(), covered);
			covered.add(midx.getMultiPackIndexBase());
		}
	}
}

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper class for calculations over a list of pack descriptions
 */
public class MidxDescList {
	/**
	 * Wrap the list of pack descriptions into a MidxDescList
	 * <p>
	 * The input list is well-formed, doesn't have duplicated packs/midxs.
	 *
	 * @param descs
	 *            list of pack descriptions
	 * @return amidxDescList instance
	 */
	public static MidxDescList create(DfsPackDescription[] descs) {
		return new MidxDescList(Arrays.asList(descs));
	}

	/**
	 * Wrap the packs list into amidxDescList
	 * <p>
	 * The input list is well-formed, doesn't have duplicated packs/midxs.
	 *
	 * @param descs
	 *            list of pack descriptions
	 * @return amidxDescList instance
	 */
	public static MidxDescList create(List<DfsPackDescription> descs) {
		return new MidxDescList(descs);
	}

	private final List<DfsPackDescription> descs;

	private MidxDescList(List<DfsPackDescription> descs) {
		this.descs = descs;
	}

	/**
	 * Wrapper over {@link #findAllCoveringMidxs(Collection)} for the tests
	 *
	 * @param pack
	 *            a single pack
	 * @return midxs containing the pack
	 */
	public Set<DfsPackDescription> findAllCoveringMidxs(
			DfsPackDescription pack) {
		return findAllCoveringMidxs(List.of(pack));
	}

	/**
	 * Return the union of midxs that cover the query packs (directly or
	 * indirectly).
	 * <p>
	 * An midx covers itself and its bases.
	 *
	 * @param queryPacks
	 *            packs to be modified
	 * @return midxs covering any of the query packs
	 */
	public Set<DfsPackDescription> findAllCoveringMidxs(
			Collection<DfsPackDescription> queryPacks) {
		if (queryPacks.isEmpty()) {
			return Collections.emptySet();
		}

		List<DfsPackDescription> knownMidxs = descs.stream()
				.filter(dsc -> dsc.hasFileExt(MULTI_PACK_INDEX)).toList();

		if (knownMidxs.isEmpty()) {
			return Collections.emptySet();
		}

		// TODO(ifrade): Delete from queryPacks as we find them and stop if we
		// are done
		Set<DfsPackDescription> impactedMidxs = new HashSet<>();
		for (DfsPackDescription midx : knownMidxs) {
			List<DfsPackDescription> visitedMidxs = new ArrayList<>();
			DfsPackDescription current = midx;
			while (current != null) {
				visitedMidxs.add(current);
				if (queryPacks.contains(current)
						|| containsAny(current.getCoveredPacks(), queryPacks)) {
					// Anything above in the chain is also covering this pack
					impactedMidxs.addAll(visitedMidxs);
					// Reset the list and keep going, maybe something
					// deeper in the chain is also affected
					visitedMidxs.clear();
				}
				current = current.getMultiPackIndexBase();
			}
			visitedMidxs.clear();
		}

		return impactedMidxs;
	}

	private static boolean containsAny(List<DfsPackDescription> inMidx,
			Collection<DfsPackDescription> queryPacks) {
		Set<DfsPackDescription> inMidxSet = new HashSet<>(inMidx);
		return queryPacks.stream().anyMatch(inMidxSet::contains);
	}
}

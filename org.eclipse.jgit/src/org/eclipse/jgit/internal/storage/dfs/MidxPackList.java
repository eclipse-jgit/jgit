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
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jgit.annotations.Nullable;

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

	private final List<DfsPackFile> packs;

	private MidxPackList(DfsPackFile[] packs) {
		this.packs = Arrays.asList(packs);
	}

	/**
	 * Get all plain packs in the list, either top-level or inside midxs
	 *
	 * @return a list of all "real" packs in this pack list, either top level or
	 *         inside midxs.
	 **/
	public List<DfsPackFile> getAllPlainPacks() {
		List<DfsPackFile> plainPacks = new ArrayList<>();
		Queue<DfsPackFile> pending = new ArrayDeque<>(packs);
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
		List<DfsPackFileMidx> topLevelMidxs = packs.stream().filter(
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
	 * Alias for {@link #findAllCoveringMidxs(List)}, as convenience for tests
	 *
	 * @param pack
	 *            a single pack
	 * @return all the midxs that include (directly or indirectly) the pack
	 */
	Set<DfsPackFileMidx> findAllCoveringMidxs(DfsPackFile pack) {
		return findAllCoveringMidxs(List.of(pack));
	}

	/**
	 * Return all the midxs that cover (directoy or indirectly) a set of packs
	 * <p>
	 * In a chain like midx3(p6, p5) -> midx2(p4, p3) -> midx1(p2, p1), if we
	 * check for p3, midx3 and midx2 are covering it and midx1 is not.
	 *
	 * @param queryPacks
	 *            subset of packs we are checking for coverage
	 * @return set of midxs from the packlist that should also be removed
	 */
	public Set<DfsPackFileMidx> findAllCoveringMidxs(
			List<DfsPackFile> queryPacks) {
		if (queryPacks.isEmpty()) {
			return Collections.emptySet();
		}

		List<DfsPackFileMidx> topLevelMidxs = packs.stream().filter(
				p -> p.getPackDescription().hasFileExt(MULTI_PACK_INDEX))
				.map(p -> (DfsPackFileMidx) p).toList();

		if (topLevelMidxs.isEmpty()) {
			return Collections.emptySet();
		}

		// TODO(ifrade): Delete from queryPacks as we find them and stop if we
		// are done
		Set<DfsPackFileMidx> impactedMidxs = asSet(List.of());
		for (DfsPackFileMidx midx : topLevelMidxs) {
			List<DfsPackFileMidx> visitedMidxs = new ArrayList<>();
			DfsPackFileMidx current = midx;
			while (current != null) {
				visitedMidxs.add(current);
				if (containsAny(current.getCoveredPacks(), queryPacks)) {
					// Anything above in the chain is also covering this pack
					impactedMidxs.addAll(visitedMidxs);
					// Reset the list and keep going, maybe something
					// deeper in the chain is also affected
					visitedMidxs.clear();
				}
				current = current.getMultipackIndexBase();
			}
			visitedMidxs.clear();
		}

		return impactedMidxs;
	}

	/**
	 * Return the top midx of the chain in the repo
	 *
	 * <p>
	 * In the unlikely case of multiple unconnected midxs, take the most recent.
	 *
	 * @return top midx
	 */
	public DfsPackFileMidx getTopMidxPack() {
		List<DfsPackFileMidx> topLevelMidxs = packs.stream().filter(
				p -> p.getPackDescription().hasFileExt(MULTI_PACK_INDEX))
				.map(p -> (DfsPackFileMidx) p).toList();
		if (topLevelMidxs.isEmpty()) {
			return null;
		}

		if (topLevelMidxs.size() == 1) {
			return topLevelMidxs.get(0);
		}

		Optional<DfsPackFileMidx> newest = topLevelMidxs.stream().max(Comparator
				.comparingLong(p -> p.getPackDescription().getLastModified()));
		return newest.orElse(null);
	}

	/**
	 * Return all the plain packs not covered by this midx or its parents.
	 *
	 * @param midx
	 *            a multipack index. Null midx returns all packs in db.
	 * @return all the packs in the db that are not covered by this midx or its
	 *         parents.
	 */
	public List<DfsPackFile> getPlainPacksNotCoveredBy(
			@Nullable DfsPackFileMidx midx) {
		if (midx == null) {
			return getAllPlainPacks();
		}
		TreeSet<DfsPackFile> covered = asSet(midx.getAllCoveredPacks());
		// We cannot just take "all packs that are not midx" from the list
		// because this midx could be the middle in a chain.
		return getAllPlainPacks().stream().filter(p -> !covered.contains(p))
				.toList();
	}

	private static boolean containsAny(List<DfsPackFile> inMidx,
			List<DfsPackFile> queryPacks) {
		Set<DfsPackFile> inMidxSet = asSet(inMidx);
		return queryPacks.stream().anyMatch(inMidxSet::contains);
	}

	private static <T extends DfsPackFile> TreeSet<T> asSet(
			List<T> initialValues) {
		TreeSet<T> theSet = new TreeSet<>(PACK_NAME_COMPARATOR);
		theSet.addAll(initialValues);
		return theSet;
	}
}
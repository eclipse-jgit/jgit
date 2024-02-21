/*
 * Copyright (C) 2022, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.commitgraph;

import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.COMMIT_DATA_WIDTH;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GRAPH_EDGE_LAST_MASK;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GRAPH_EXTRA_EDGES_NEEDED;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GRAPH_LAST_EDGE;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GRAPH_NO_PARENT;

import java.text.MessageFormat;
import java.util.Arrays;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph.CommitData;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.NB;

/**
 * Represent the collection of {@link CommitData}.
 */
class GraphCommitData {

	private static final int[] NO_PARENTS = {};

	private final byte[] data;

	private final byte[] extraList;

	private final int hashLength;

	private final int commitDataLength;

	/**
	 * Initialize the GraphCommitData.
	 *
	 * @param hashLength
	 *            length of object hash.
	 * @param commitData
	 *            content of CommitData Chunk.
	 * @param extraList
	 *            content of Extra Edge List Chunk.
	 */
	GraphCommitData(int hashLength, @NonNull byte[] commitData,
			byte[] extraList) {
		this.data = commitData;
		this.extraList = extraList;
		this.hashLength = hashLength;
		this.commitDataLength = hashLength + COMMIT_DATA_WIDTH;
	}

	/**
	 * Get the metadata of a commit。
	 *
	 * @param graphPos
	 *            the position in the commit-graph of the object.
	 * @return the metadata of a commit or null if not found.
	 */
	CommitData getCommitData(int graphPos) {
		int dataIdx = commitDataLength * graphPos;

		// parse tree
		ObjectId tree = ObjectId.fromRaw(data, dataIdx);

		// parse date
		long dateHigh = NB.decodeUInt32(data, dataIdx + hashLength + 8) & 0x3;
		long dateLow = NB.decodeUInt32(data, dataIdx + hashLength + 12);
		long commitTime = dateHigh << 32 | dateLow;

		// parse generation
		int generation = NB.decodeInt32(data, dataIdx + hashLength + 8) >> 2;

		// parse first parent
		int parent1 = NB.decodeInt32(data, dataIdx + hashLength);
		if (parent1 == GRAPH_NO_PARENT) {
			return new CommitDataImpl(tree, NO_PARENTS, commitTime, generation);
		}

		// parse second parent
		int parent2 = NB.decodeInt32(data, dataIdx + hashLength + 4);
		if (parent2 == GRAPH_NO_PARENT) {
			return new CommitDataImpl(tree, new int[] { parent1 }, commitTime,
					generation);
		}

		if ((parent2 & GRAPH_EXTRA_EDGES_NEEDED) == 0) {
			return new CommitDataImpl(tree, new int[] { parent1, parent2 },
					commitTime, generation);
		}

		// parse parents for octopus merge
		return new CommitDataImpl(tree,
				findParentsForOctopusMerge(parent1,
						parent2 & GRAPH_EDGE_LAST_MASK),
				commitTime, generation);
	}

	private int[] findParentsForOctopusMerge(int parent1, int extraEdgePos) {
		int maxOffset = extraList.length - 4;
		int offset = extraEdgePos * 4;
		if (offset < 0 || offset > maxOffset) {
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().invalidExtraEdgeListPosition,
					Integer.valueOf(extraEdgePos)));
		}
		int[] pList = new int[32];
		pList[0] = parent1;
		int count = 1;
		int parentPosition;
		for (; offset <= maxOffset; offset += 4) {
			if (count >= pList.length) {
				// expand the pList
				pList = Arrays.copyOf(pList, pList.length + 32);
			}
			parentPosition = NB.decodeInt32(extraList, offset);
			if ((parentPosition & GRAPH_LAST_EDGE) != 0) {
				pList[count++] = parentPosition & GRAPH_EDGE_LAST_MASK;
				break;
			}
			pList[count++] = parentPosition;
		}
		return Arrays.copyOf(pList, count);
	}

	private static class CommitDataImpl implements CommitData {

		private final ObjectId tree;

		private final int[] parents;

		private final long commitTime;

		private final int generation;

		public CommitDataImpl(ObjectId tree, int[] parents, long commitTime,
				int generation) {
			this.tree = tree;
			this.parents = parents;
			this.commitTime = commitTime;
			this.generation = generation;
		}

		@Override
		public ObjectId getTree() {
			return tree;
		}

		@Override
		public int[] getParents() {
			return parents;
		}

		@Override
		public long getCommitTime() {
			return commitTime;
		}

		@Override
		public int getGeneration() {
			return generation;
		}
	}
}

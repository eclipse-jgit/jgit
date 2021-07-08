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

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph.CommitData;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.NB;

/**
 * Represent the collection of {@link CommitData}.
 */
class GraphCommitData {

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
	 * @return the metadata of a commit.
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

		boolean noParents = false;
		int[] pList = new int[0];
		int edgeValue = NB.decodeInt32(data, dataIdx + hashLength);
		if (edgeValue == GRAPH_NO_PARENT) {
			noParents = true;
		}

		// parse parents
		if (!noParents) {
			pList = new int[1];
			int parent = edgeValue;
			pList[0] = parent;

			edgeValue = NB.decodeInt32(data, dataIdx + hashLength + 4);
			if (edgeValue != GRAPH_NO_PARENT) {
				if ((edgeValue & GRAPH_EXTRA_EDGES_NEEDED) != 0) {
					int pptr = edgeValue & GRAPH_EDGE_LAST_MASK;
					int[] eList = findExtraEdgeList(pptr);
					if (eList == null) {
						return null;
					}
					int[] old = pList;
					pList = new int[eList.length + 1];
					pList[0] = old[0];
					for (int i = 0; i < eList.length; i++) {
						parent = eList[i];
						pList[i + 1] = parent;
					}
				} else {
					parent = edgeValue;
					pList = new int[] { pList[0], parent };
				}
			}
		}
		int[] parents = pList;
		return new CommitDataImpl(tree, parents, commitTime, generation);
	}

	/**
	 * Find the list of commit-graph position in extra edge list chunk.
	 * <p>
	 * The extra edge list chunk store the second through nth parents for all
	 * octopus merges.
	 *
	 * @param pptr
	 *            the start position to iterate of extra edge list chunk.
	 * @return the list of commit-graph position or null if not found.
	 */
	private int[] findExtraEdgeList(int pptr) {
		int maxOffset = extraList.length - 4;
		int offset = pptr * 4;
		if (offset < 0 || offset > maxOffset) {
			return null;
		}
		int[] pList = new int[32];
		int count = 0;
		int parentPosition;
		for (;;) {
			if (count >= pList.length) {
				int[] old = pList;
				pList = new int[pList.length + 32];
				System.arraycopy(old, 0, pList, 0, count);
			}
			if (offset > maxOffset) {
				return null;
			}
			parentPosition = NB.decodeInt32(extraList, offset);
			if ((parentPosition & GRAPH_LAST_EDGE) != 0) {
				pList[count] = parentPosition & GRAPH_EDGE_LAST_MASK;
				count++;
				break;
			}
			pList[count++] = parentPosition;
			offset += 4;
		}
		int[] old = pList;
		pList = new int[count];
		System.arraycopy(old, 0, pList, 0, count);

		return pList;
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

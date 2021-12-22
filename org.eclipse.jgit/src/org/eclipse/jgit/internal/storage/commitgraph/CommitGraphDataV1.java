/*
 * Copyright (C) 2021, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.commitgraph;

import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_BLOOM_DATA;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_BLOOM_INDEXES;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_COMMIT_DATA;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_EXTRA_EDGE_LIST;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_OID_FANOUT;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_OID_LOOKUP;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.COMMIT_DATA_EXTRA_LENGTH;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.COMMIT_GRAPH_MAGIC;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GRAPH_CHUNK_LOOKUP_WIDTH;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GRAPH_EDGE_LAST_MASK;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GRAPH_EXTRA_EDGES_NEEDED;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GRAPH_LAST_EDGE;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GRAPH_NO_PARENT;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Arrays;

import org.eclipse.jgit.errors.CommitGraphFormatException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitGraph;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.NB;

/**
 * Support for the commit-graph v1 format.
 *
 * @see CommitGraphData
 */
public class CommitGraphDataV1 extends CommitGraphData {

	private static final int FANOUT = 256;

	private final long commitCnt;

	private final int hashLength;

	private final int commitDataLength;

	private final boolean noBloomFilters;

	private int numHashes;

	private int bitsPerEntry;

	private long[] oidFanout;

	private byte[][] oidLookup;

	private byte[][] commitData;

	private byte[][] bloomIdx;

	private byte[] bloomData;

	private byte[] extraEdgeList;

	CommitGraphDataV1(InputStream fd, byte[] hdr) throws IOException {
		int magic = NB.decodeInt32(hdr, 0);
		if (magic != COMMIT_GRAPH_MAGIC) {
			throw new CommitGraphFormatException(
					JGitText.get().notACommitGraph);
		}

		// Read the hash version (1 byte)
		// 1 => SHA-1
		// 2 => SHA-256 nonsupport now
		int hashVersion = hdr[5];
		if (hashVersion != 1) {
			throw new CommitGraphFormatException(
					JGitText.get().incorrectOBJECT_ID_LENGTH);
		}

		hashLength = OBJECT_ID_LENGTH;
		commitDataLength = hashLength + COMMIT_DATA_EXTRA_LENGTH;

		// Read the number of "chunkOffsets" (1 byte)
		int numberOfChunks = hdr[6];

		byte[] chunkLookup = new byte[GRAPH_CHUNK_LOOKUP_WIDTH
				* (numberOfChunks + 1)];
		IO.readFully(fd, chunkLookup, 0, chunkLookup.length);

		int[] chunkId = new int[numberOfChunks + 1];
		long[] chunkOffset = new long[numberOfChunks + 1];
		for (int i = 0; i <= numberOfChunks; i++) {
			chunkId[i] = NB.decodeInt32(chunkLookup, i * 12);
			for (int j = 0; j < i; j++) {
				if (chunkId[i] == chunkId[j]) {
					throw new CommitGraphFormatException(MessageFormat.format(
							JGitText.get().commitGraphChunkRepeated,
							Integer.toHexString(chunkId[i])));
				}
			}
			chunkOffset[i] = NB.decodeInt64(chunkLookup, i * 12 + 4);
		}

		oidLookup = new byte[FANOUT][];
		commitData = new byte[FANOUT][];
		bloomIdx = new byte[FANOUT][];

		boolean bloomIdxLoaded = false;
		for (int i = 0; i < numberOfChunks; i++) {
			long length = chunkOffset[i + 1] - chunkOffset[i];
			long lengthReaded;
			if (chunkOffset[i] < 0
					|| chunkOffset[i] > chunkOffset[numberOfChunks]) {
				throw new CommitGraphFormatException(MessageFormat.format(
						JGitText.get().commitGraphChunkImproperOffset,
						Integer.toHexString(chunkId[i]),
						Long.valueOf(chunkOffset[i])));
			}
			switch (chunkId[i]) {
			case CHUNK_ID_OID_FANOUT:
				lengthReaded = loadChunkOidFanout(fd);
				break;
			case CHUNK_ID_OID_LOOKUP:
				lengthReaded = loadChunkDataBasedOnFanout(fd, hashLength,
						oidLookup);
				break;
			case CHUNK_ID_COMMIT_DATA:
				lengthReaded = loadChunkDataBasedOnFanout(fd, commitDataLength,
						commitData);
				break;
			case CHUNK_ID_EXTRA_EDGE_LIST:
				lengthReaded = loadChunkExtraEdgeList(fd, length);
				break;
			case CHUNK_ID_BLOOM_INDEXES:
				lengthReaded = loadChunkDataBasedOnFanout(fd, Integer.BYTES,
						bloomIdx);
				bloomIdxLoaded = true;
				break;
			case CHUNK_ID_BLOOM_DATA:
				lengthReaded = loadChunkBloomData(fd, length);
				break;
			default:
				throw new CommitGraphFormatException(MessageFormat.format(
						JGitText.get().commitGraphChunkUnknown,
						Integer.toHexString(chunkId[i])));
			}
			if (length != lengthReaded) {
				throw new CommitGraphFormatException(MessageFormat.format(
						JGitText.get().commitGraphChunkImproperOffset,
						Integer.toHexString(chunkId[i + 1]),
						Long.valueOf(chunkOffset[i + 1])));
			}
		}

		if (oidFanout == null) {
			throw new CommitGraphFormatException(
					JGitText.get().commitGraphOidFanoutNeeded);
		}
		if (bloomIdxLoaded && bloomData != null) {
			noBloomFilters = false;
		} else {
			noBloomFilters = true;
		}
		commitCnt = oidFanout[FANOUT - 1];
	}

	private long loadChunkOidFanout(InputStream fd) throws IOException {
		int fanoutLen = FANOUT * 4;
		byte[] fanoutTable = new byte[fanoutLen];
		IO.readFully(fd, fanoutTable, 0, fanoutLen);
		oidFanout = new long[FANOUT];
		for (int k = 0; k < oidFanout.length; k++) {
			oidFanout[k] = NB.decodeUInt32(fanoutTable, k * 4);
		}
		return fanoutLen;
	}

	private long loadChunkDataBasedOnFanout(InputStream fd, int itemLength,
			byte[][] chunkData) throws IOException {
		if (oidFanout == null) {
			throw new CommitGraphFormatException(
					JGitText.get().commitGraphOidFanoutNeeded);
		}
		long readedLength = 0;
		for (int k = 0; k < oidFanout.length; k++) {
			long n;
			if (k == 0) {
				n = oidFanout[k];
			} else {
				n = oidFanout[k] - oidFanout[k - 1];
			}
			if (n > 0) {
				long len = n * itemLength;
				if (len > Integer.MAX_VALUE - 8) { // http://stackoverflow.com/a/8381338
					throw new CommitGraphFormatException(
							JGitText.get().commitGraphFileIsTooLargeForJgit);
				}

				chunkData[k] = new byte[(int) len];

				IO.readFully(fd, chunkData[k], 0, chunkData[k].length);
				readedLength += len;
			}
		}
		return readedLength;
	}

	private long loadChunkExtraEdgeList(InputStream fd, long len)
			throws IOException {
		if (len > Integer.MAX_VALUE - 8) { // http://stackoverflow.com/a/8381338
			throw new CommitGraphFormatException(
					JGitText.get().commitGraphFileIsTooLargeForJgit);
		}
		extraEdgeList = new byte[(int) len];
		IO.readFully(fd, extraEdgeList, 0, extraEdgeList.length);
		return len;
	}

	private long loadChunkBloomData(InputStream fd, long len) throws IOException {
		if (len > Integer.MAX_VALUE - 8) { // http://stackoverflow.com/a/8381338
			throw new CommitGraphFormatException(
					JGitText.get().commitGraphFileIsTooLargeForJgit);
		}
		byte[] header = new byte[12];
		IO.readFully(fd, header, 0, header.length);

		int hashVersion = NB.decodeInt32(header, 0);
		if (hashVersion != 1) {
			throw new CommitGraphFormatException(MessageFormat.format(
					JGitText.get().requiredHashFunctionNotAvailable,
					Integer.valueOf(hashVersion)));
		}
		numHashes = NB.decodeInt32(header, 4);
		bitsPerEntry = NB.decodeInt32(header, 8);

		bloomData = new byte[(int) len - header.length];
		IO.readFully(fd, bloomData, 0, bloomData.length);
		return len;
	}

	/** {@inheritDoc} */
	@Override
	public int findGraphPosition(AnyObjectId objId) {
		int levelOne = objId.getFirstByte();
		byte[] data = oidLookup[levelOne];
		if (data == null) {
			return -1;
		}
		int high = data.length / (hashLength);
		int low = 0;
		do {
			int mid = (low + high) >>> 1;
			int pos = objIdOffset(mid);
			int cmp = objId.compareTo(data, pos);
			if (cmp < 0) {
				high = mid;
			} else if (cmp == 0) {
				if (levelOne == 0) {
					return mid;
				}
				return (int) (mid + oidFanout[levelOne - 1]);
			} else {
				low = mid + 1;
			}
		} while (low < high);
		return -1;
	}

	/** {@inheritDoc} */
	@Override
	public ObjectId getObjectId(int graphPos) {
		if (graphPos < 0 || graphPos > commitCnt) {
			return null;
		}
		int levelOne = findLevelOne(graphPos);
		int p = getLevelTwo(graphPos, levelOne);
		int dataIdx = objIdOffset(p);
		return ObjectId.fromRaw(oidLookup[levelOne], dataIdx);
	}

	/** {@inheritDoc} */
	@Override
	public CommitGraph.CommitData getCommitData(int graphPos) {
		int levelOne = findLevelOne(graphPos);
		int p = getLevelTwo(graphPos, levelOne);
		int dataIdx = commitDataOffset(p);
		byte[] data = this.commitData[levelOne];

		if (graphPos < 0) {
			return null;
		}

		CommitDataImpl commit = new CommitDataImpl();

		// parse tree
		commit.tree = ObjectId.fromRaw(data, dataIdx);

		// parse date
		long dateHigh = NB.decodeUInt32(data, dataIdx + hashLength + 8) & 0x3;
		long dateLow = NB.decodeUInt32(data, dataIdx + hashLength + 12);
		commit.commitTime = dateHigh << 32 | dateLow;

		// parse generation
		commit.generation = NB.decodeInt32(data, dataIdx + hashLength + 8) >> 2;

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
		commit.parents = pList;

		return commit;
	}

	/** {@inheritDoc} */
	@Override
	public ChangedPathFilter findBloomFilter(int graphPos) {
		if (noBloomFilters || graphPos < 0 || graphPos > commitCnt) {
			return null;
		}
		int levelOne = findLevelOne(graphPos);
		int p = getLevelTwo(graphPos, levelOne);

		int endIdx = NB.decodeInt32(bloomIdx[levelOne], p * 4);
		int startIdx = 0;
		if (graphPos > 0) {
			levelOne = findLevelOne(graphPos - 1);
			p = getLevelTwo(graphPos - 1, levelOne);
			startIdx = NB.decodeInt32(bloomIdx[levelOne], p * 4);
		}

		if (endIdx - startIdx <= 0) {
			return null;
		}
		byte[] data = new byte[endIdx - startIdx];
		System.arraycopy(bloomData, startIdx, data, 0, data.length);
		return new ChangedPathFilter(data, numHashes);
	}

	/**
	 * Find the list of commit-graph position in extra edge list chunk.
	 * <p>
	 * The extra edge list chunk store the second through nth parents for all
	 * octopus merges.
	 *
	 * @param pptr
	 *            the start position to iterate of extra edge list chunk
	 * @return the list of commit-graph position or null if not found
	 */
	int[] findExtraEdgeList(int pptr) {
		if (extraEdgeList == null) {
			return null;
		}
		int maxOffset = extraEdgeList.length - 4;
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
			parentPosition = NB.decodeInt32(extraEdgeList, offset);
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

	/** {@inheritDoc} */
	@Override
	public long getCommitCnt() {
		return commitCnt;
	}

	/** {@inheritDoc} */
	@Override
	public int getHashLength() {
		return hashLength;
	}

	@Override
	int getNumHashes() {
		return numHashes;
	}

	@Override
	int getBitsPerEntry() {
		return bitsPerEntry;
	}

	@Override
	boolean noBloomFilter() {
		return noBloomFilters;
	}

	private int findLevelOne(long nthPosition) {
		int levelOne = Arrays.binarySearch(oidFanout, nthPosition + 1);
		if (levelOne >= 0) {
			// If we hit the bucket exactly the item is in the bucket, or
			// any bucket before it which has the same object count.
			//
			long base = oidFanout[levelOne];
			while (levelOne > 0 && base == oidFanout[levelOne - 1]) {
				levelOne--;
			}
		} else {
			// The item is in the bucket we would insert it into.
			//
			levelOne = -(levelOne + 1);
		}
		return levelOne;
	}

	private int getLevelTwo(long nthPosition, int levelOne) {
		long base = levelOne > 0 ? oidFanout[levelOne - 1] : 0;
		return (int) (nthPosition - base);
	}

	private int objIdOffset(int mid) {
		return hashLength * mid;
	}

	private int commitDataOffset(int mid) {
		return commitDataLength * mid;
	}
}

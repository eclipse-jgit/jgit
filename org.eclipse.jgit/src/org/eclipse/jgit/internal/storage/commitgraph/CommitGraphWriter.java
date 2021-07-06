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

import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_COMMIT_DATA;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_EXTRA_EDGE_LIST;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_OID_FANOUT;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_OID_LOOKUP;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.COMMIT_DATA_EXTRA_LENGTH;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.COMMIT_GRAPH_MAGIC;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GRAPH_CHUNK_LOOKUP_WIDTH;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GRAPH_EXTRA_EDGES_NEEDED;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GRAPH_LAST_EDGE;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GRAPH_NO_PARENT;
import static org.eclipse.jgit.lib.Constants.COMMIT_GENERATION_NOT_COMPUTED;
import static org.eclipse.jgit.lib.Constants.COMMIT_GENERATION_UNKNOWN;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.io.CancellableDigestOutputStream;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.NB;

/**
 * Writes a commit-graph formatted file.
 */
public class CommitGraphWriter {

	private static final int COMMIT_GRAPH_VERSION_GENERATED = 1;

	private static final int OID_HASH_VERSION = 1;

	private static final int GENERATION_NUMBER_MAX = 0x3FFFFFFF;

	private static final int MAX_NUM_CHUNKS = 5;

	private static final int GRAPH_FANOUT_SIZE = 4 * 256;

	private final int hashsz;

	private final ObjectReader reader;

	private InMemoryOidLookup oidLookup;

	private ProgressMonitor pm;

	/**
	 * Create writer for specified repository.
	 *
	 * @param repo
	 *            repository where objects are stored.
	 */
	public CommitGraphWriter(Repository repo) {
		this.reader = repo.newObjectReader();
		this.hashsz = OBJECT_ID_LENGTH;
	}

	/**
	 * Prepare the commits and write them to the supplied stream.
	 *
	 * @param monitor
	 *            progress monitor to report the number of items written.
	 * @param wants
	 *            the list of wanted objects, writer walks commits starting at
	 *            these. Must not be {@code null}.
	 * @param commitGraphStream
	 *            output stream of commit-graph data. The stream should be
	 *            buffered by the caller. The caller is responsible for closing
	 *            the stream.
	 * @throws IOException
	 */
	public void write(ProgressMonitor monitor,
			@NonNull Set<? extends ObjectId> wants,
			OutputStream commitGraphStream) throws IOException {
		if (monitor == null) {
			this.pm = NullProgressMonitor.INSTANCE;
		} else {
			this.pm = monitor;
		}
		try (RevWalk walk = new RevWalk(reader)) {
			oidLookup = InMemoryOidLookup.load(pm, wants, walk);
		}
		writeTo(commitGraphStream);
	}

	private void writeTo(OutputStream commitGraphStream) throws IOException {
		if (getCommitCnt() == 0) {
			return;
		}

		List<ChunkHeader> chunks = createChunks();
		long writeCount = 256 + 2 * oidLookup.size()
				+ oidLookup.getExtraEdgeCnt();
		pm.beginTask(MessageFormat.format(JGitText.get().writingOutCommitGraph,
				Integer.valueOf(chunks.size())), (int) writeCount);

		try (CancellableDigestOutputStream out = new CancellableDigestOutputStream(
				pm, commitGraphStream)) {
			writeHeader(out, chunks.size());
			writeChunkLookup(out, chunks);
			writeChunks(out, chunks);
			writeCheckSum(out);
		} catch (InterruptedIOException e) {
			throw new IOException(JGitText.get().commitGraphWritingCancelled);
		} finally {
			pm.endTask();
		}
	}

	private List<ChunkHeader> createChunks() {
		List<ChunkHeader> chunks = new ArrayList<>();
		chunks.add(new ChunkHeader(CHUNK_ID_OID_FANOUT, GRAPH_FANOUT_SIZE));
		chunks.add(new ChunkHeader(CHUNK_ID_OID_LOOKUP, hashsz * oidLookup.size()));
		chunks.add(new ChunkHeader(CHUNK_ID_COMMIT_DATA, (hashsz + 16) * oidLookup.size()));
		if (oidLookup.getExtraEdgeCnt() > 0) {
			chunks.add(new ChunkHeader(CHUNK_ID_EXTRA_EDGE_LIST, oidLookup.getExtraEdgeCnt() * 4));
		}
		return chunks;
	}

	private void writeHeader(CancellableDigestOutputStream out, int numChunks)
			throws IOException {
		byte[] headerBuffer = new byte[8];
		NB.encodeInt32(headerBuffer, 0, COMMIT_GRAPH_MAGIC);
		byte[] buff = { (byte) COMMIT_GRAPH_VERSION_GENERATED,
				(byte) OID_HASH_VERSION, (byte) numChunks, (byte) 0 };
		System.arraycopy(buff, 0, headerBuffer, 4, 4);
		out.write(headerBuffer, 0, 8);
		out.flush();
	}

	private void writeChunkLookup(CancellableDigestOutputStream out,
			List<ChunkHeader> chunks) throws IOException {
		int numChunks = chunks.size();
		long chunkOffset = 8 + (numChunks + 1) * GRAPH_CHUNK_LOOKUP_WIDTH;
		byte[] buffer = new byte[GRAPH_CHUNK_LOOKUP_WIDTH];
		for (ChunkHeader chunk : chunks) {
			NB.encodeInt32(buffer, 0, chunk.id);
			NB.encodeInt64(buffer, 4, chunkOffset);
			out.write(buffer);
			chunkOffset += chunk.size;
		}
		NB.encodeInt32(buffer, 0, 0);
		NB.encodeInt64(buffer, 4, chunkOffset);
		out.write(buffer);
	}

	private void writeChunks(CancellableDigestOutputStream out,
			List<ChunkHeader> chunks) throws IOException {
		for (ChunkHeader chunk : chunks) {
			int chunkId = chunk.id;

			switch (chunkId) {
			case CHUNK_ID_OID_FANOUT:
				writeFanoutTable(out);
				break;
			case CHUNK_ID_OID_LOOKUP:
				writeOidLookUp(out);
				break;
			case CHUNK_ID_COMMIT_DATA:
				writeCommitData(out);
				break;
			case CHUNK_ID_EXTRA_EDGE_LIST:
				writeExtraEdges(out);
				break;
			}
		}
	}

	private void writeCheckSum(CancellableDigestOutputStream out)
			throws IOException {
		out.write(out.getDigest());
		out.flush();
	}

	/**
	 * Returns commits number that was created by this writer.
	 *
	 * @return number of commits; {@code 0} If
	 *         {@link #write(ProgressMonitor, Set, OutputStream)} was never
	 *         called.
	 */
	public long getCommitCnt() {
		return oidLookup == null ? 0 : oidLookup.size();
	}

	private void writeFanoutTable(CancellableDigestOutputStream out)
			throws IOException {
		byte[] tmp = new byte[4];
		int[] fanout = new int[256];
		for (RevCommit c : oidLookup) {
			fanout[c.getFirstByte() & 0xff]++;
		}
		for (int i = 1; i < fanout.length; i++) {
			fanout[i] += fanout[i - 1];
		}
		for (int n : fanout) {
			NB.encodeInt32(tmp, 0, n);
			out.write(tmp, 0, 4);
			out.getWriteMonitor().update(1);
		}
	}

	private void writeOidLookUp(CancellableDigestOutputStream out)
			throws IOException {
		byte[] tmp = new byte[4 + hashsz];

		for (RevCommit c : oidLookup) {
			c.copyRawTo(tmp, 0);
			out.write(tmp, 0, hashsz);
			out.getWriteMonitor().update(1);
		}
	}

	private void writeCommitData(CancellableDigestOutputStream out)
			throws IOException {
		int[] generations = computeGenerationNumbers();
		int num = 0;
		byte[] tmp = new byte[hashsz + COMMIT_DATA_EXTRA_LENGTH];
		for (int i = 0; i < oidLookup.size(); i++) {
			RevCommit commit = oidLookup.getCommit(i);
			int edgeValue;
			int[] packedDate = new int[2];

			ObjectId treeId = commit.getTree();
			treeId.copyRawTo(tmp, 0);

			RevCommit[] parents = commit.getParents();
			if (parents.length == 0) {
				edgeValue = GRAPH_NO_PARENT;
			} else {
				RevCommit parent = parents[0];
				edgeValue = oidLookup.getOidPosition(parent);
			}
			NB.encodeInt32(tmp, hashsz, edgeValue);
			if (parents.length == 1) {
				edgeValue = GRAPH_NO_PARENT;
			} else if (parents.length == 2) {
				RevCommit parent = parents[1];
				edgeValue = oidLookup.getOidPosition(parent);
			} else if (parents.length > 2) {
				edgeValue = GRAPH_EXTRA_EDGES_NEEDED | num;
				num += parents.length - 1;
			}

			NB.encodeInt32(tmp, hashsz + 4, edgeValue);

			packedDate[0] = 0; // commitTime is an int in JGit now
			packedDate[0] |= generations[i] << 2;
			packedDate[1] = commit.getCommitTime();
			NB.encodeInt32(tmp, hashsz + 8, packedDate[0]);
			NB.encodeInt32(tmp, hashsz + 12, packedDate[1]);

			out.write(tmp);
			out.getWriteMonitor().update(1);
		}
	}

	private void writeExtraEdges(CancellableDigestOutputStream out)
			throws IOException {
		byte[] tmp = new byte[4];
		for (RevCommit commit : oidLookup) {
			RevCommit[] parents = commit.getParents();
			if (parents.length > 2) {
				int edgeValue;
				for (int n = 1; n < parents.length; n++) {
					RevCommit parent = parents[n];
					edgeValue = oidLookup.getOidPosition(parent);
					if (n == parents.length - 1) {
						edgeValue |= GRAPH_LAST_EDGE;
					}
					NB.encodeInt32(tmp, 0, edgeValue);
					out.write(tmp);
					out.getWriteMonitor().update(1);
				}
			}
		}
	}

	private int[] computeGenerationNumbers() throws MissingObjectException {
		int[] generations = new int[oidLookup.size()];
		pm.beginTask(JGitText.get().computingCommitGeneration,
				oidLookup.size());
		for (RevCommit cmit : oidLookup) {
			pm.update(1);
			int generation = generations[oidLookup.getOidPosition(cmit)];
			if (generation != COMMIT_GENERATION_NOT_COMPUTED
					&& generation != COMMIT_GENERATION_UNKNOWN) {
				continue;
			}

			Stack<RevCommit> commitStack = new Stack<>();
			commitStack.push(cmit);

			while (!commitStack.empty()) {
				int maxGeneration = 0;
				boolean allParentComputed = true;
				RevCommit current = commitStack.peek();
				RevCommit parent;

				for (int i = 0; i < current.getParentCount(); i++) {
					parent = current.getParent(i);
					generation = generations[oidLookup.getOidPosition(parent)];
					if (generation == COMMIT_GENERATION_NOT_COMPUTED
							|| generation == COMMIT_GENERATION_UNKNOWN) {
						allParentComputed = false;
						commitStack.push(parent);
						break;
					} else if (generation > maxGeneration) {
						maxGeneration = generation;
					}
				}

				if (allParentComputed) {
					RevCommit commit = commitStack.pop();
					generation = maxGeneration + 1;
					if (generation > GENERATION_NUMBER_MAX) {
						generation = GENERATION_NUMBER_MAX;
					}
					generations[oidLookup.getOidPosition(commit)] = generation;
				}
			}
		}
		pm.endTask();
		return generations;
	}

	private static class ChunkHeader {
		final int id;

		final long size;

		public ChunkHeader(int id, long size) {
			this.id = id;
			this.size = size;
		}
	}
}

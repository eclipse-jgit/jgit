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

import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_GENERATION_DATA;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_GENERATION_DATA_OVERFLOW;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_COMMIT_DATA;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_EXTRA_EDGE_LIST;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_OID_FANOUT;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_OID_LOOKUP;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_LOOKUP_WIDTH;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.COMMIT_DATA_WIDTH;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.COMMIT_GRAPH_MAGIC;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.COMMIT_GRAPH_VERSION_GENERATED;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GENERATION_DATA_MAX_OFFSET;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GENERATION_DATA_OVERFLOW_BIT;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GENERATION_NUMBER_V1_MAX;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GRAPH_EXTRA_EDGES_NEEDED;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GRAPH_FANOUT_SIZE;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GRAPH_LAST_EDGE;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.GRAPH_NO_PARENT;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.OID_HASH_VERSION;
import static org.eclipse.jgit.lib.Constants.COMMIT_GENERATION_NOT_COMPUTED;
import static org.eclipse.jgit.lib.Constants.COMMIT_GENERATION_UNKNOWN_V1;
import static org.eclipse.jgit.lib.Constants.COMMIT_GENERATION_UNKNOWN_V2;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Stack;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.io.CancellableDigestOutputStream;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.NB;

/**
 * Writes a commit-graph formatted file.
 *
 * @since 6.5
 */
public class CommitGraphWriter {

	private final int hashsz;

	private final int generationVersion;

	private final GraphCommits graphCommits;

	/**
	 * Create commit-graph writer for these commits.
	 *
	 * @param graphCommits
	 *            the commits which will be written to the commit-graph.
	 */
	public CommitGraphWriter(@NonNull GraphCommits graphCommits) {
		this.graphCommits = graphCommits;
		this.hashsz = OBJECT_ID_LENGTH;
		this.generationVersion = 1;
	}

	/**
	 * Create commit-graph writer for these commits.
	 *
	 * @param graphCommits
	 *            the commits which will be written to the commit-graph.
	 * @param generationVersion
	 *            the generation version will be used in the newly created
	 *            commit-graph.
	 */
	public CommitGraphWriter(@NonNull GraphCommits graphCommits,
			int generationVersion) {
		this.graphCommits = graphCommits;
		this.hashsz = OBJECT_ID_LENGTH;
		if (generationVersion != 2 && generationVersion != 1) {
			throw new IllegalStateException(MessageFormat.format(
					JGitText.get().unsupportedGenerationNumberVersion,
					Integer.valueOf(generationVersion)));
		}
		this.generationVersion = generationVersion;
	}

	/**
	 * Write commit-graph to the supplied stream.
	 *
	 * @param monitor           progress monitor to report the number of items
	 *                          written.
	 * @param commitGraphStream output stream of commit-graph data. The stream
	 *                          should be buffered by the caller. The caller is
	 *                          responsible for closing the stream.
	 * @throws IOException
	 *             if an error occurred
	 */
	public void write(@NonNull ProgressMonitor monitor, @NonNull OutputStream commitGraphStream) throws IOException {
		if (graphCommits.size() == 0) {
			return;
		}
		List<ChunkHeader> chunks = createChunks(monitor);
		long writeCount = 256 + 2 * graphCommits.size() + graphCommits.getExtraEdgeCnt();
		monitor.beginTask(MessageFormat.format(JGitText.get().writingOutCommitGraph, Integer.valueOf(chunks.size())),
				(int) writeCount);

		try (CancellableDigestOutputStream out = new CancellableDigestOutputStream(monitor, commitGraphStream)) {
			writeHeader(out, chunks.size());
			writeChunkLookup(out, chunks);
			writeChunks(monitor, out, chunks);
			writeCheckSum(out);
		} catch (InterruptedIOException e) {
			throw new IOException(JGitText.get().commitGraphWritingCancelled, e);
		} finally {
			monitor.endTask();
		}
	}

	private List<ChunkHeader> createChunks(@NonNull ProgressMonitor monitor) throws IOException {
		List<ChunkHeader> chunks = new ArrayList<>();
		chunks.add(new ChunkHeader(CHUNK_ID_OID_FANOUT, GRAPH_FANOUT_SIZE));
		chunks.add(new ChunkHeader(CHUNK_ID_OID_LOOKUP, hashsz * graphCommits.size()));
		chunks.add(new ChunkHeader(CHUNK_ID_COMMIT_DATA, (hashsz + 16) * graphCommits.size()));
		if (graphCommits.getExtraEdgeCnt() > 0) {
			chunks.add(new ChunkHeader(CHUNK_ID_EXTRA_EDGE_LIST, graphCommits.getExtraEdgeCnt() * 4));
		}

		if (generationVersion == 2) {
			GenerationDataChunks generationDataChunks = computeGenerationNumberV2(
					monitor);
			chunks.add(new ChunkHeader(CHUNK_GENERATION_DATA,
					generationDataChunks.data));
			if (generationDataChunks.overflow.size() > 0) {
				chunks.add(new ChunkHeader(CHUNK_GENERATION_DATA_OVERFLOW,
						generationDataChunks.overflow));
			}
		}
		return chunks;
	}

	private void writeHeader(CancellableDigestOutputStream out, int numChunks) throws IOException {
		byte[] headerBuffer = new byte[8];
		NB.encodeInt32(headerBuffer, 0, COMMIT_GRAPH_MAGIC);
		byte[] buff = { (byte) COMMIT_GRAPH_VERSION_GENERATED, (byte) OID_HASH_VERSION, (byte) numChunks, (byte) 0 };
		System.arraycopy(buff, 0, headerBuffer, 4, 4);
		out.write(headerBuffer, 0, 8);
		out.flush();
	}

	private void writeChunkLookup(CancellableDigestOutputStream out, List<ChunkHeader> chunks) throws IOException {
		int numChunks = chunks.size();
		long chunkOffset = 8 + (numChunks + 1) * CHUNK_LOOKUP_WIDTH;
		byte[] buffer = new byte[CHUNK_LOOKUP_WIDTH];
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

	private void writeChunks(ProgressMonitor monitor, CancellableDigestOutputStream out, List<ChunkHeader> chunks)
			throws IOException {
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
				writeCommitData(monitor, out);
				break;
			case CHUNK_ID_EXTRA_EDGE_LIST:
				writeExtraEdges(out);
				break;
			case CHUNK_GENERATION_DATA:
			case CHUNK_GENERATION_DATA_OVERFLOW:
				if (chunk.data.isEmpty()) {
					throw new IllegalStateException(
							"data for this chunk must be precomputed");
				}
				chunk.data.get().writeTo(out);
				break;
			}
		}
	}

	private void writeCheckSum(CancellableDigestOutputStream out) throws IOException {
		out.write(out.getDigest());
		out.flush();
	}

	private void writeFanoutTable(CancellableDigestOutputStream out) throws IOException {
		byte[] tmp = new byte[4];
		int[] fanout = new int[256];
		for (RevCommit c : graphCommits) {
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

	private void writeOidLookUp(CancellableDigestOutputStream out) throws IOException {
		byte[] tmp = new byte[4 + hashsz];

		for (RevCommit c : graphCommits) {
			c.copyRawTo(tmp, 0);
			out.write(tmp, 0, hashsz);
			out.getWriteMonitor().update(1);
		}
	}

	private void writeCommitData(ProgressMonitor monitor, CancellableDigestOutputStream out) throws IOException {
		int[] generations = computeGenerationNumberV1(monitor);
		int num = 0;
		byte[] tmp = new byte[hashsz + COMMIT_DATA_WIDTH];
		int i = 0;
		for (RevCommit commit : graphCommits) {
			int edgeValue;
			int[] packedDate = new int[2];

			ObjectId treeId = commit.getTree();
			treeId.copyRawTo(tmp, 0);

			RevCommit[] parents = commit.getParents();
			if (parents.length == 0) {
				edgeValue = GRAPH_NO_PARENT;
			} else {
				RevCommit parent = parents[0];
				edgeValue = graphCommits.getOidPosition(parent);
			}
			NB.encodeInt32(tmp, hashsz, edgeValue);
			if (parents.length == 1) {
				edgeValue = GRAPH_NO_PARENT;
			} else if (parents.length == 2) {
				RevCommit parent = parents[1];
				edgeValue = graphCommits.getOidPosition(parent);
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
			i++;
		}
	}

	private GenerationDataChunks computeGenerationNumberV2(
			ProgressMonitor monitor) throws IOException {
		ByteArrayOutputStream data = new ByteArrayOutputStream();
		ByteArrayOutputStream overflow = new ByteArrayOutputStream();

		long[] correctedCommitDates = new long[graphCommits.size()];
		monitor.beginTask(JGitText.get().computingCommitGenerationV2,
				graphCommits.size());
		for (RevCommit cmit : graphCommits) {
			monitor.update(1);
			long correctedCommitDate = correctedCommitDates[graphCommits
					.getOidPosition(cmit)];
			if (correctedCommitDate != COMMIT_GENERATION_NOT_COMPUTED
					&& correctedCommitDate != COMMIT_GENERATION_UNKNOWN_V1) {
				continue;
			}

			Stack<RevCommit> commitStack = new Stack<>();
			commitStack.push(cmit);

			while (!commitStack.empty()) {
				long maxCorrectedCommitDate = 0;
				boolean allParentComputed = true;
				RevCommit current = commitStack.peek();
				RevCommit parent;

				for (int i = 0; i < current.getParentCount(); i++) {
					parent = current.getParent(i);
					correctedCommitDate = correctedCommitDates[graphCommits
							.getOidPosition(parent)];
					if (correctedCommitDate == COMMIT_GENERATION_NOT_COMPUTED
							|| correctedCommitDate == COMMIT_GENERATION_UNKNOWN_V2) {
						allParentComputed = false;
						commitStack.push(parent);
						break;
					} else if (correctedCommitDate > maxCorrectedCommitDate) {
						maxCorrectedCommitDate = correctedCommitDate;
					}
				}

				if (allParentComputed) {
					RevCommit commit = commitStack.pop();
					correctedCommitDates[graphCommits
							.getOidPosition(commit)] = commit
									.getCommitTime() > maxCorrectedCommitDate
											? commit.getCommitTime()
											: maxCorrectedCommitDate + 1;
				}
			}
		}
		monitor.endTask();

		int overflowPos = 0;
		for (RevCommit c : graphCommits) {
			long correctedCommitDate = correctedCommitDates[graphCommits
					.getOidPosition(c)];
			int commitData = c.getCommitTime();
			long offset = correctedCommitDate - commitData;

			byte[] data_tmp = new byte[4];

			if (offset > GENERATION_DATA_MAX_OFFSET) {
				byte[] overflow_tmp = new byte[8];
				int valueReplacement = GENERATION_DATA_OVERFLOW_BIT
						| overflowPos++;
				NB.encodeInt32(data_tmp, 0, valueReplacement);
				NB.encodeInt64(overflow_tmp, 0, offset);
				data.write(data_tmp);
				overflow.write(overflow_tmp);
			} else {
				NB.encodeInt32(data_tmp, 0, (int) offset);
				data.write(data_tmp);
			}
		}
		return new GenerationDataChunks(data, overflow);
	}

	private int[] computeGenerationNumberV1(ProgressMonitor monitor)
			throws IOException {
		int[] generations = new int[graphCommits.size()];
		monitor.beginTask(JGitText.get().computingCommitGenerationV1, graphCommits.size());
		for (RevCommit cmit : graphCommits) {
			monitor.update(1);
			int generation = generations[graphCommits.getOidPosition(cmit)];
			if (generation != COMMIT_GENERATION_NOT_COMPUTED && generation != COMMIT_GENERATION_UNKNOWN_V1) {
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
					generation = generations[graphCommits.getOidPosition(parent)];
					if (generation == COMMIT_GENERATION_NOT_COMPUTED || generation == COMMIT_GENERATION_UNKNOWN_V1) {
						allParentComputed = false;
						commitStack.push(parent);
						break;
					} else if (generation > maxGeneration) {
						maxGeneration = generation;
					}
				}

				if (allParentComputed) {
					RevCommit commit = commitStack.pop();
					generations[graphCommits.getOidPosition(commit)] = maxGeneration >= GENERATION_NUMBER_V1_MAX
							? GENERATION_NUMBER_V1_MAX
							: maxGeneration + 1;
				}
			}
		}
		monitor.endTask();
		return generations;
	}

	private void writeExtraEdges(CancellableDigestOutputStream out) throws IOException {
		byte[] tmp = new byte[4];
		for (RevCommit commit : graphCommits) {
			RevCommit[] parents = commit.getParents();
			if (parents.length > 2) {
				int edgeValue;
				for (int n = 1; n < parents.length; n++) {
					RevCommit parent = parents[n];
					edgeValue = graphCommits.getOidPosition(parent);
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

	private static class ChunkHeader {
		final int id;

		final long size;

		final Optional<ByteArrayOutputStream> data;

		public ChunkHeader(int id, long size) {
			this.id = id;
			this.size = size;
			this.data = Optional.empty();
		}

		ChunkHeader(int id, ByteArrayOutputStream data) {
			this.id = id;
			this.size = data.size();
			this.data = Optional.of(data);
		}
	}

	private static class GenerationDataChunks {
		final ByteArrayOutputStream data;

		final ByteArrayOutputStream overflow;

		GenerationDataChunks(ByteArrayOutputStream data,
				ByteArrayOutputStream overflow) {
			this.data = data;
			this.overflow = overflow;
		}
	}
}

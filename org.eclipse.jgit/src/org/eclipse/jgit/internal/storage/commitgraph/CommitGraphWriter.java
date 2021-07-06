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
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.CommitGraph;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.BlockList;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.io.CancellableDigestOutputStream;

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

	private final RevWalk walk;

	private List<GraphCommitData> commitDataList = new BlockList<>();

	private ObjectIdOwnerMap<GraphCommitData> commitDataMap = new ObjectIdOwnerMap<>();

	private int numExtraEdges;

	private boolean computeGeneration;

	/**
	 * Create writer for specified repository.
	 *
	 * @param repo
	 *            repository where objects are stored.
	 */
	public CommitGraphWriter(Repository repo) {
		this(repo, repo.newObjectReader());
	}

	/**
	 * Create writer for specified repository.
	 *
	 * @param repo
	 *            repository where objects are stored.
	 * @param reader
	 *            reader to read from the repository with.
	 */
	public CommitGraphWriter(Repository repo, ObjectReader reader) {
		this(new CommitGraphConfig(repo), reader);
	}

	/**
	 * Create writer with a specified configuration.
	 *
	 * @param cfg
	 *            configuration for the commit-graph writer.
	 * @param reader
	 *            reader to read from the repository with.
	 */
	public CommitGraphWriter(CommitGraphConfig cfg, ObjectReader reader) {
		this.walk = new RevWalk(reader);
		this.computeGeneration = cfg.isComputeGeneration();
		this.hashsz = OBJECT_ID_LENGTH;
	}

	/**
	 * Prepare the list of commits to be written to the commit-graph stream.
	 *
	 * @param findingMonitor
	 *            progress monitor to report the number of commits found.
	 * @param computeGenerationMonitor
	 *            progress monitor to report generation computation work.
	 * @param wants
	 *            the list of wanted objects, writer walks commits starting at
	 *            these. Must not be {@code null}.
	 * @throws IOException
	 */
	public void prepareCommitGraph(ProgressMonitor findingMonitor,
			ProgressMonitor computeGenerationMonitor,
			@NonNull Set<? extends ObjectId> wants) throws IOException {
		BlockList<RevCommit> commits = findCommits(findingMonitor, wants);
		if (computeGeneration) {
			computeGenerationNumbers(computeGenerationMonitor, commits);
		}
	}

	/**
	 * Write the prepared commits to the supplied stream.
	 *
	 * @param writeMonitor
	 *            progress monitor to report the number of items written.
	 * @param commitGraphStream
	 *            output stream of commit-graph data. The stream should be
	 *            buffered by the caller. The caller is responsible for closing
	 *            the stream.
	 * @throws IOException
	 */
	public void writeCommitGraph(ProgressMonitor writeMonitor,
			OutputStream commitGraphStream) throws IOException {
		if (writeMonitor == null) {
			writeMonitor = NullProgressMonitor.INSTANCE;
		}

		Chunk[] chunks = createChunks();

		long writeCount = 256 + 2 * commitDataList.size() + numExtraEdges;
		beginPhase(
				MessageFormat.format(JGitText.get().writingOutCommitGraph,
						Integer.valueOf(chunks.length)),
				writeMonitor, writeCount);

		try (CancellableDigestOutputStream out = new CancellableDigestOutputStream(
				writeMonitor, commitGraphStream)) {
			writeHeader(out, chunks.length);
			writeChunkLookup(out, chunks);
			writeChunks(out, chunks);
			writeCheckSum(out);
		} catch (InterruptedIOException e) {
			throw new IOException(JGitText.get().commitGraphWritingCancelled);
		} finally {
			endPhase(writeMonitor);
		}
	}

	private Chunk[] createChunks() {
		Chunk[] chunks = new Chunk[MAX_NUM_CHUNKS];
		for (int i = 0; i < chunks.length; i++) {
			chunks[i] = new Chunk();
		}
		int numChunks = 3;

		chunks[0].id = CHUNK_ID_OID_FANOUT;
		chunks[0].size = GRAPH_FANOUT_SIZE;

		chunks[1].id = CHUNK_ID_OID_LOOKUP;
		chunks[1].size = hashsz * commitDataList.size();

		chunks[2].id = CHUNK_ID_COMMIT_DATA;
		chunks[2].size = (hashsz + 16) * commitDataList.size();

		if (numExtraEdges > 0) {
			chunks[numChunks].id = CHUNK_ID_EXTRA_EDGE_LIST;
			chunks[numChunks].size = numExtraEdges * 4;
			numChunks++;
		}
		chunks[numChunks].id = 0;
		chunks[numChunks].size = 0L;
		return Arrays.copyOfRange(chunks, 0, numChunks);
	}

	private void writeHeader(CancellableDigestOutputStream out, int numChunks)
			throws IOException {
		byte[] headerBuffer = new byte[8];
		NB.encodeInt32(headerBuffer, 0, COMMIT_GRAPH_MAGIC);
		byte[] buff = { (byte) getVersion(), (byte) OID_HASH_VERSION,
				(byte) numChunks, (byte) 0 };
		System.arraycopy(buff, 0, headerBuffer, 4, 4);
		out.write(headerBuffer, 0, 8);
		out.flush();
	}

	private void writeChunkLookup(CancellableDigestOutputStream out, Chunk[] chunks)
			throws IOException {
		int numChunks = chunks.length;
		long chunkOffset = 8 + (numChunks + 1) * GRAPH_CHUNK_LOOKUP_WIDTH;
		byte[] buffer = new byte[GRAPH_CHUNK_LOOKUP_WIDTH];
		for (int i = 0; i < numChunks; i++) {
			Chunk chunk = chunks[i];

			NB.encodeInt32(buffer, 0, chunk.id);
			NB.encodeInt64(buffer, 4, chunkOffset);
			out.write(buffer);
			chunkOffset += chunk.size;
		}
		NB.encodeInt32(buffer, 0, 0);
		NB.encodeInt64(buffer, 4, chunkOffset);
		out.write(buffer);
	}

	private void writeChunks(CancellableDigestOutputStream out, Chunk[] chunks)
			throws IOException {
		Collections.sort(commitDataList); // sorted by name
		for (int i = 0; i < chunks.length; i++) {
			int chunkId = chunks[i].id;

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
	 * @return number of commits.
	 */
	public long getCommitCnt() {
		return commitDataList.size();
	}

	/**
	 * Whether to compute generation numbers.
	 *
	 * Default setting: {@value CommitGraphConfig#DEFAULT_COMPUTE_GENERATION}
	 *
	 * @return {@code true} if the writer should compute generation numbers.
	 */
	public boolean isComputeGeneration() {
		return computeGeneration;
	}

	/**
	 * Whether the writer should compute generation numbers.
	 *
	 * Default setting: {@value CommitGraphConfig#DEFAULT_COMPUTE_GENERATION}
	 *
	 * @param computeGeneration
	 *            if {@code true} the commits in commit-graph will have the
	 *            computed generation number.
	 */
	public void setComputeGeneration(boolean computeGeneration) {
		this.computeGeneration = computeGeneration;
	}

	/**
	 * Whether to write the extra edge list.
	 * <p>
	 * This list of 4-byte values store the second through nth parents for all
	 * octopus merges.
	 *
	 * @return {@code true} if the writer will write the extra edge list.
	 */
	public boolean willWriteExtraEdgeList() {
		return numExtraEdges > 0;
	}

	private void writeFanoutTable(CancellableDigestOutputStream out)
			throws IOException {
		byte[] tmp = new byte[4];
		int[] fanout = new int[256];
		for (GraphCommitData c : commitDataList) {
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

		for (int i = 0; i < commitDataList.size(); i++) {
			GraphCommitData commitData = commitDataList.get(i);
			commitData.setOidPosition(i);
			commitData.copyRawTo(tmp, 0);
			out.write(tmp, 0, hashsz);
			out.getWriteMonitor().update(1);
		}
	}

	private void writeCommitData(CancellableDigestOutputStream out)
			throws IOException {
		int num = 0;
		byte[] tmp = new byte[hashsz + COMMIT_DATA_EXTRA_LENGTH];
		for (GraphCommitData c : commitDataList) {
			int edgeValue;
			int[] packedDate = new int[2];

			RevCommit commit = walk.parseCommit(c);
			ObjectId treeId = commit.getTree();
			treeId.copyRawTo(tmp, 0);

			RevCommit[] parents = commit.getParents();
			if (parents.length == 0) {
				edgeValue = GRAPH_NO_PARENT;
			} else {
				RevCommit parent = parents[0];
				edgeValue = getCommitOidPosition(parent);
			}
			NB.encodeInt32(tmp, hashsz, edgeValue);
			if (parents.length == 1) {
				edgeValue = GRAPH_NO_PARENT;
			} else if (parents.length == 2) {
				RevCommit parent = parents[1];
				edgeValue = getCommitOidPosition(parent);
			} else if (parents.length > 2) {
				edgeValue = GRAPH_EXTRA_EDGES_NEEDED | num;
				num += parents.length - 1;
			}

			NB.encodeInt32(tmp, hashsz + 4, edgeValue);

			packedDate[0] = 0; // commitTime is an int in JGit now
			packedDate[0] |= c.getGeneration() << 2;
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
		for (GraphCommitData c : commitDataList) {
			RevCommit commit = walk.parseCommit(c);
			RevCommit[] parents = commit.getParents();
			if (parents.length > 2) {
				int edgeValue;
				for (int n = 1; n < parents.length; n++) {
					RevCommit parent = parents[n];
					edgeValue = getCommitOidPosition(parent);
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

	private BlockList<RevCommit> findCommits(ProgressMonitor findingMonitor,
			Set<? extends ObjectId> wants) throws IOException {
		if (findingMonitor == null) {
			findingMonitor = NullProgressMonitor.INSTANCE;
		}

		for (ObjectId id : wants) {
			RevObject o = walk.parseAny(id);
			if (o instanceof RevCommit) {
				walk.markStart((RevCommit) o);
			}
		}

		walk.sort(RevSort.COMMIT_TIME_DESC);
		BlockList<RevCommit> commits = new BlockList<>();

		RevCommit c;
		beginPhase(JGitText.get().findingCommitsForCommitGraph, findingMonitor,
				ProgressMonitor.UNKNOWN);
		while ((c = walk.next()) != null) {
			findingMonitor.update(1);
			commits.add(c);
			addCommitData(c);
			if (c.getParentCount() > 2) {
				numExtraEdges += c.getParentCount() - 1;
			}
		}
		endPhase(findingMonitor);

		return commits;
	}

	private void computeGenerationNumbers(
			ProgressMonitor computeGenerationMonitor, List<RevCommit> commits)
			throws MissingObjectException {
		if (computeGenerationMonitor == null) {
			computeGenerationMonitor = NullProgressMonitor.INSTANCE;
		}

		beginPhase(JGitText.get().computingCommitGeneration,
				computeGenerationMonitor, commits.size());
		for (RevCommit cmit : commits) {
			computeGenerationMonitor.update(1);
			int generation = getCommitGeneration(cmit);
			if (generation != CommitGraph.GENERATION_NOT_COMPUTED
					&& generation != CommitGraph.GENERATION_UNKNOWN) {
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
					generation = getCommitGeneration(parent);
					if (generation == CommitGraph.GENERATION_NOT_COMPUTED
							|| generation == CommitGraph.GENERATION_UNKNOWN) {
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
					setCommitGeneration(commit, generation);
				}
			}
		}
		endPhase(computeGenerationMonitor);
	}

	private int getVersion() {
		return COMMIT_GRAPH_VERSION_GENERATED;
	}

	private static class Chunk {
		int id;

		long size;
	}

	private int getCommitGeneration(RevCommit commit)
			throws MissingObjectException {
		GraphCommitData c = commitDataMap.get(commit);
		if (c == null) {
			throw new MissingObjectException(commit, Constants.OBJ_COMMIT);
		}
		return c.getGeneration();
	}

	private void setCommitGeneration(RevCommit commit, int generation)
			throws MissingObjectException {
		GraphCommitData c = commitDataMap.get(commit);
		if (c == null) {
			throw new MissingObjectException(commit, Constants.OBJ_COMMIT);
		}
		c.setGeneration(generation);
	}

	private int getCommitOidPosition(RevCommit commit)
			throws MissingObjectException {
		GraphCommitData c = commitDataMap.get(commit);
		if (c == null) {
			throw new MissingObjectException(commit, Constants.OBJ_COMMIT);
		}
		return c.getOidPosition();
	}

	private void addCommitData(RevCommit commit) {
		GraphCommitData c = new GraphCommitData(commit);
		commitDataList.add(c);
		commitDataMap.add(c);
	}

	private void beginPhase(String task, ProgressMonitor monitor, long cnt) {
		monitor.beginTask(task, (int) cnt);
	}

	private void endPhase(ProgressMonitor monitor) {
		monitor.endTask();
	}
}

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

import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_BLOOM_FILTER_DATA;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_BLOOM_FILTER_INDEX;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_COMMIT_DATA;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_EXTRA_EDGE_LIST;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_OID_FANOUT;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_ID_OID_LOOKUP;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.CHUNK_LOOKUP_WIDTH;
import static org.eclipse.jgit.internal.storage.commitgraph.CommitGraphConstants.COMMIT_GRAPH_MAGIC;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.SystemReader;
import org.eclipse.jgit.util.io.SilentFileInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The loader returns the representation of the commit-graph file content.
 */
public class CommitGraphLoader {

	private final static Logger LOG = LoggerFactory
			.getLogger(CommitGraphLoader.class);

	/**
	 * Open an existing commit-graph file for reading.
	 * <p>
	 * The format of the file will be automatically detected and a proper access
	 * implementation for that format will be constructed and returned to the
	 * caller. The file may or may not be held open by the returned instance.
	 *
	 * @param graphFile
	 *            existing commit-graph to read.
	 * @return a copy of the commit-graph file in memory
	 * @throws FileNotFoundException
	 *             the file does not exist.
	 * @throws CommitGraphFormatException
	 *             commit-graph file's format is different from we expected.
	 * @throws java.io.IOException
	 *             the file exists but could not be read due to security errors
	 *             or unexpected data corruption.
	 */
	public static CommitGraph open(File graphFile) throws FileNotFoundException,
			CommitGraphFormatException, IOException {
		try (SilentFileInputStream fd = new SilentFileInputStream(graphFile)) {
			try {
				return read(fd);
			} catch (CommitGraphFormatException fe) {
				throw fe;
			} catch (IOException ioe) {
				throw new IOException(MessageFormat.format(
						JGitText.get().unreadableCommitGraph,
						graphFile.getAbsolutePath()), ioe);
			}
		}
	}

	/**
	 * Read an existing commit-graph file from a buffered stream.
	 * <p>
	 * The format of the file will be automatically detected and a proper access
	 * implementation for that format will be constructed and returned to the
	 * caller. The file may or may not be held open by the returned instance.
	 *
	 * @param fd
	 *            stream to read the commit-graph file from. The stream must be
	 *            buffered as some small IOs are performed against the stream.
	 *            The caller is responsible for closing the stream.
	 *
	 * @return a copy of the commit-graph file in memory
	 * @throws CommitGraphFormatException
	 *             the commit-graph file's format is different from we expected.
	 * @throws java.io.IOException
	 *             the stream cannot be read.
	 */
	public static CommitGraph read(InputStream fd)
			throws CommitGraphFormatException, IOException {

		boolean readChangedPathFilters;
		try {
			readChangedPathFilters = SystemReader.getInstance().getJGitConfig()
					.getBoolean(ConfigConstants.CONFIG_COMMIT_GRAPH_SECTION,
							ConfigConstants.CONFIG_KEY_READ_CHANGED_PATHS,
							false);
		} catch (ConfigInvalidException e) {
			// Use the default value if, for some reason, the config couldn't be
			// read.
			readChangedPathFilters = false;
		}

		return read(fd, readChangedPathFilters);
	}

	/**
	 * Read an existing commit-graph file from a buffered stream.
	 * <p>
	 * The format of the file will be automatically detected and a proper access
	 * implementation for that format will be constructed and returned to the
	 * caller. The file may or may not be held open by the returned instance.
	 *
	 * @param fd
	 *            stream to read the commit-graph file from. The stream must be
	 *            buffered as some small IOs are performed against the stream.
	 *            The caller is responsible for closing the stream.
	 *
	 * @param readChangedPathFilters
	 *            enable reading bloom filter chunks.
	 *
	 * @return a copy of the commit-graph file in memory
	 * @throws CommitGraphFormatException
	 *             the commit-graph file's format is different from we expected.
	 * @throws java.io.IOException
	 *             the stream cannot be read.
	 */
	public static CommitGraph read(InputStream fd,
			boolean readChangedPathFilters)
			throws CommitGraphFormatException, IOException {
		byte[] hdr = new byte[8];
		IO.readFully(fd, hdr, 0, hdr.length);

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

		// Check commit-graph version
		int v = hdr[4];
		if (v != 1) {
			throw new CommitGraphFormatException(MessageFormat.format(
					JGitText.get().unsupportedCommitGraphVersion,
					Integer.valueOf(v)));
		}

		// Read the number of "chunkOffsets" (1 byte)
		int numberOfChunks = hdr[6];

		// hdr[7] is the number of base commit-graphs, which is not supported in
		// current version

		byte[] lookupBuffer = new byte[CHUNK_LOOKUP_WIDTH
				* (numberOfChunks + 1)];
		IO.readFully(fd, lookupBuffer, 0, lookupBuffer.length);
		List<ChunkSegment> chunks = new ArrayList<>(numberOfChunks + 1);
		for (int i = 0; i <= numberOfChunks; i++) {
			// chunks[numberOfChunks] is just a marker, in order to record the
			// length of the last chunk.
			int id = NB.decodeInt32(lookupBuffer, i * 12);
			long offset = NB.decodeInt64(lookupBuffer, i * 12 + 4);
			chunks.add(new ChunkSegment(id, offset));
		}

		CommitGraphBuilder builder = CommitGraphBuilder.builder();
		for (int i = 0; i < numberOfChunks; i++) {
			long chunkOffset = chunks.get(i).offset;
			int chunkId = chunks.get(i).id;
			long len = chunks.get(i + 1).offset - chunkOffset;

			if (len > Integer.MAX_VALUE - 8) { // http://stackoverflow.com/a/8381338
				throw new CommitGraphFormatException(
						JGitText.get().commitGraphFileIsTooLargeForJgit);
			}

			byte buffer[] = new byte[(int) len];
			IO.readFully(fd, buffer, 0, buffer.length);

			switch (chunkId) {
			case CHUNK_ID_OID_FANOUT:
				builder.addOidFanout(buffer);
				break;
			case CHUNK_ID_OID_LOOKUP:
				builder.addOidLookUp(buffer);
				break;
			case CHUNK_ID_COMMIT_DATA:
				builder.addCommitData(buffer);
				break;
			case CHUNK_ID_EXTRA_EDGE_LIST:
				builder.addExtraList(buffer);
				break;
			case CHUNK_ID_BLOOM_FILTER_INDEX:
				if (readChangedPathFilters) {
					builder.addBloomFilterIndex(buffer);
				}
				break;
			case CHUNK_ID_BLOOM_FILTER_DATA:
				if (readChangedPathFilters) {
					builder.addBloomFilterData(buffer);
				}
				break;
			default:
				LOG.warn(MessageFormat.format(
						JGitText.get().commitGraphChunkUnknown,
						Integer.toHexString(chunkId)));
			}
		}
		return builder.build();
	}

	private static class ChunkSegment {
		final int id;

		final long offset;

		private ChunkSegment(int id, long offset) {
			this.id = id;
			this.offset = offset;
		}
	}
}

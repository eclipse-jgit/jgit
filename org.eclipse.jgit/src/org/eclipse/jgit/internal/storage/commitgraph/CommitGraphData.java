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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;

import org.eclipse.jgit.errors.CommitGraphFormatException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitGraph;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.io.SilentFileInputStream;

/**
 * <p>
 * The commit-graph stores a list of commit OIDs and some associated metadata,
 * including:
 * <ol>
 * <li>The generation number of the commit. Commits with no parents have
 * generation number 1; commits with parents have generation number one more
 * than the maximum generation number of its parents. We reserve zero as
 * special, and can be used to mark a generation number invalid or as "not
 * computed".</li>
 * <li>The root tree OID.</li>
 * <li>The commit date.</li>
 * <li>The parents of the commit, stored using positional references within the
 * graph file.</li>
 * </ol>
 * </p>
 */
public abstract class CommitGraphData {

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
	 *             commit-graph file's format is different than we expected.
	 * @throws java.io.IOException
	 *             the file exists but could not be read due to security errors
	 *             or unexpected data corruption.
	 */
	public static CommitGraphData open(File graphFile)
			throws FileNotFoundException, CommitGraphFormatException,
			IOException {
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
	 *             the commit-graph file's format is different than we expected.
	 * @throws java.io.IOException
	 *             the stream cannot be read.
	 */
	public static CommitGraphData read(InputStream fd) throws IOException {
		byte[] hdr = new byte[8];
		IO.readFully(fd, hdr, 0, hdr.length);

		int v = hdr[4];
		if (v != 1) {
			throw new CommitGraphFormatException(MessageFormat.format(
					JGitText.get().unsupportedCommitGraphVersion,
					Integer.valueOf(v)));
		}

		return new CommitGraphDataV1(fd, hdr);
	}

	/**
	 * Finds the position in the commit-graph of the object.
	 *
	 * @param objId
	 *            the id for which the commit-graph position will be found.
	 * @return the commit-graph id or -1 if the object was not found.
	 */
	public abstract int findGraphPosition(AnyObjectId objId);

	/**
	 * Get the object at the commit-graph position.
	 *
	 * @param graphPos
	 *            the position in the commit-graph of the object.
	 * @return the ObjectId or null if the object was not found.
	 */
	public abstract ObjectId getObjectId(int graphPos);

	/**
	 * Get the metadata of a commit。
	 *
	 * @param graphPos
	 *            the position in the commit-graph of the object.
	 * @return the metadata of a commit or null if it's not found.
	 */
	public abstract CommitGraph.CommitData getCommitData(int graphPos);

	/**
	 * Obtain the total number of commits described by this commit-graph.
	 *
	 * @return number of commits in this commit-graph
	 */
	public abstract long getCommitCnt();

	/**
	 * Get the hash length of this commit-graph
	 *
	 * @return object hash length
	 */
	public abstract int getHashLength();

	static class CommitDataImpl implements CommitGraph.CommitData {

		ObjectId tree;

		int[] parents;

		long commitTime;

		int generation;

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

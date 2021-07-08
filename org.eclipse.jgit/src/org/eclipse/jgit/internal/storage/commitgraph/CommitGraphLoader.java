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
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.io.SilentFileInputStream;

/**
 * The loader returns the representation of the commit-graph file content.
 */
public class CommitGraphLoader {

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
	public static CommitGraphSingleFile open(File graphFile)
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
	public static CommitGraphSingleFile read(InputStream fd)
			throws IOException {
		byte[] hdr = new byte[8];
		IO.readFully(fd, hdr, 0, hdr.length);

		int v = hdr[4];
		if (v != 1) {
			throw new CommitGraphFormatException(MessageFormat.format(
					JGitText.get().unsupportedCommitGraphVersion,
					Integer.valueOf(v)));
		}

		CommitGraphFileContent content = new CommitGraphParserV1(fd, hdr);
		return new CommitGraphSingleFile(content);
	}
}

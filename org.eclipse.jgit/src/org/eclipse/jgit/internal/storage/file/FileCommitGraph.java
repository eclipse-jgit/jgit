/*
 * Copyright (C) 2022, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraphFormatException;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraphLoader;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph;
import org.eclipse.jgit.lib.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Traditional file system for commit-graph.
 * <p>
 * This is the commit graph structure representation for a Git object database.
 */
public class FileCommitGraph {
	private final static Logger LOG = LoggerFactory
			.getLogger(FileCommitGraph.class);

	private final File baseFile;

	private final AtomicReference<GraphSnapshot> graph;

	/**
	 * Initialize a reference to an on-disk commit-graph.
	 *
	 * @param objectsDir
	 *            the location of the <code>objects</code> directory.
	 */
	FileCommitGraph(File objectsDir) {
		this.baseFile = new File(objectsDir, Constants.INFO_COMMIT_GRAPH);
		this.graph = new AtomicReference<>();
	}

	/**
	 * The method will first scan whether the ".git/objects/info/commit-graph"
	 * has been modified, if so, it will re-parse the file, otherwise it will
	 * return the same result as the last time.
	 *
	 * @return commit-graph or null if commit-graph file does not exist and
	 *         corrupt.
	 */
	CommitGraph get() {
		GraphSnapshot now = scanCommitGraph(graph.get());
		if (now == null) {
			return null;
		}
		return now.graph;
	}

	private GraphSnapshot scanCommitGraph(GraphSnapshot original) {
		synchronized (graph) {
			GraphSnapshot o, n;
			do {
				o = graph.get();
				if (o != original) {
					// Another thread did the scan for us, while we
					// were blocked on the monitor above.
					//
					return o;
				}
				n = scanGraphImpl(o);
				if (n == o) {
					return n;
				}
			} while (!graph.compareAndSet(o, n));
			return n;
		}
	}

	private GraphSnapshot scanGraphImpl(GraphSnapshot old) {
		if (baseFile.exists() && baseFile.isFile()) {
			if (old != null && !old.isModified(baseFile)) {
				return old;
			}
			return GraphSnapshot.open(baseFile);
		}
		return null;
	}

	private static class GraphSnapshot {
		final FileSnapshot snapshot;

		final CommitGraph graph;

		static GraphSnapshot open(File file) {
			FileSnapshot fileSnapshot = FileSnapshot.save(file);
			try {
				CommitGraph commitGraph = CommitGraphLoader.open(file);
				return new GraphSnapshot(fileSnapshot, commitGraph);
			} catch (FileNotFoundException noFile) {
				// ignore if file do not exist
				return null;
			} catch (IOException e) {
				if (e instanceof CommitGraphFormatException) {
					LOG.warn(
							MessageFormat.format(
									JGitText.get().corruptCommitGraph, file),
							e);
				} else {
					LOG.error(MessageFormat.format(
							JGitText.get().exceptionWhileLoadingCommitGraph,
							file), e);
				}
				return new GraphSnapshot(fileSnapshot, null);
			}
		}

		private GraphSnapshot(FileSnapshot snapshot, CommitGraph graph) {
			this.snapshot = snapshot;
			this.graph = graph;
		}

		boolean isModified(File now) {
			return snapshot.isModified(now);
		}
	}
}

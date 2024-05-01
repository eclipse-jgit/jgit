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

import org.eclipse.jgit.annotations.NonNull;
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
 * This is the commit-graph file representation for a Git object database. Each
 * call to {@link FileCommitGraph#get()} will recheck for newer versions.
 */
public class FileCommitGraph {
	private final static Logger LOG = LoggerFactory
			.getLogger(FileCommitGraph.class);

	private final AtomicReference<GraphSnapshot> baseGraph;

	/**
	 * Initialize a reference to an on-disk commit-graph.
	 *
	 * @param objectsDir
	 *            the location of the <code>objects</code> directory.
	 */
	FileCommitGraph(File objectsDir) {
		this.baseGraph = new AtomicReference<>(new GraphSnapshot(
				new File(objectsDir, Constants.INFO_COMMIT_GRAPH)));
	}

	/**
	 * The method will first scan whether the ".git/objects/info/commit-graph"
	 * has been modified, if so, it will re-parse the file, otherwise it will
	 * return the same result as the last time.
	 *
	 * @return commit-graph or null if commit-graph file does not exist or
	 *         corrupt.
	 */
	CommitGraph get() {
		GraphSnapshot original = baseGraph.get();
		synchronized (baseGraph) {
			GraphSnapshot o, n;
			do {
				o = baseGraph.get();
				if (o != original) {
					// Another thread did the scan for us, while we
					// were blocked on the monitor above.
					//
					return o.getCommitGraph();
				}
				n = o.refresh();
				if (n == o) {
					return n.getCommitGraph();
				}
			} while (!baseGraph.compareAndSet(o, n));
			return n.getCommitGraph();
		}
	}

	private static final class GraphSnapshot {
		private final File file;

		private final FileSnapshot snapshot;

		private final CommitGraph graph;

		GraphSnapshot(@NonNull File file) {
			this(file, null, null);
		}

		GraphSnapshot(@NonNull File file, FileSnapshot snapshot,
				CommitGraph graph) {
			this.file = file;
			this.snapshot = snapshot;
			this.graph = graph;
		}

		CommitGraph getCommitGraph() {
			return graph;
		}

		GraphSnapshot refresh() {
			if (graph == null && !file.exists()) {
				// commit-graph file didn't exist
				return this;
			}
			if (snapshot != null && !snapshot.isModified(file)) {
				// commit-graph file was not modified
				return this;
			}
			return new GraphSnapshot(file, FileSnapshot.save(file),
					open(file));
		}

		private static CommitGraph open(File file) {
			try {
				return CommitGraphLoader.open(file);
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
				return null;
			}
		}
	}
}

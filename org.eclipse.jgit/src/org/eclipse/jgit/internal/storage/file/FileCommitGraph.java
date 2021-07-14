/*
 * Copyright (C) 2021, Tencent.
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

import org.eclipse.jgit.errors.CommitGraphFormatException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraphData;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraphSingleImpl;
import org.eclipse.jgit.lib.CommitGraph;
import org.eclipse.jgit.lib.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Traditional file system for commit-graph.
 * <p>
 * This is the commit graph structure representation for a Git object database
 */
public class FileCommitGraph {
	private final static Logger LOG = LoggerFactory
			.getLogger(FileCommitGraph.class);

	private final static GraphInfo NO_COMMIT_GRAPH = new GraphInfo(null);

	private final File baseFile;

	private final AtomicReference<GraphInfo> graph;

	/**
	 * Initialize a reference to an on-disk commit-graph.
	 *
	 * @param objectsDir
	 *            the location of the <code>objects</code> directory.
	 */
	FileCommitGraph(File objectsDir) {
		this.baseFile = new File(objectsDir, Constants.INFO_COMMIT_GRAPH);
		this.graph = new AtomicReference<>(NO_COMMIT_GRAPH);
	}

	CommitGraph get() {
		GraphInfo now = scanCommitGraph(graph.get());
		if (now == NO_COMMIT_GRAPH) {
			return null;
		}

		if (now.baseGraph != null) {
			return new CommitGraphSingleImpl(now.baseGraph.graphData);
		}

		return null;
	}

	private GraphInfo scanCommitGraph(GraphInfo original) {
		synchronized (graph) {
			GraphInfo o, n;
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

	private GraphInfo scanGraphImpl(GraphInfo old) {
		File nowFile = new File(baseFile.getPath());

		if (nowFile.exists() && nowFile.isFile()) {
			GraphFile oldBaseGraph = old.baseGraph;
			if (oldBaseGraph != null
					&& !oldBaseGraph.snapshot.isModified(nowFile)) {
				return old;
			}
			try {
				GraphFile baseGraph = new GraphFile(nowFile);
				return new GraphInfo(baseGraph);
			} catch (IOException e) {
				handleGraphError(e, nowFile);
			}
		}

		return NO_COMMIT_GRAPH;
	}

	private void handleGraphError(IOException e, File graphFile) {
		String errTmpl = JGitText.get().exceptionWhileLoadingCommitGraph;
		String warnTmpl = null;
		if (e instanceof FileNotFoundException) {
			// ignore if file do not exist
			return;
		}
		if (e instanceof CommitGraphFormatException) {
			warnTmpl = JGitText.get().corruptCommitGraph;
		}
		if (warnTmpl != null) {
			LOG.warn(MessageFormat.format(warnTmpl, graphFile), e);
		} else {
			LOG.error(MessageFormat.format(errTmpl, graphFile), e);
		}
	}

	private static class GraphInfo {
		final GraphFile baseGraph;

		GraphInfo(GraphFile baseGraph) {
			this.baseGraph = baseGraph;
		}
	}

	private static class GraphFile {
		final FileSnapshot snapshot;

		final CommitGraphData graphData;

		GraphFile(File file) throws IOException {
			this.snapshot = FileSnapshot.save(file);
			this.graphData = CommitGraphData.open(file);
		}
	}
}

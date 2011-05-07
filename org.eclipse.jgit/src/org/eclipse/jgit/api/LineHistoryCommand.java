/*
 * Copyright (C) 2011, Kevin Sawicki <kevin@github.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.api;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.blame.Revision;
import org.eclipse.jgit.blame.RevisionBuilder;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

/**
 * Annotate command for building a {@link Revision} list for a file path.
 */
public class LineHistoryCommand extends GitCommand<List<Revision>> {

	private String path;

	private DiffAlgorithm diffAlgorithm;

	private RawTextComparator textComparator;

	private ObjectId startCommit;

	private ObjectId endCommit;

	private boolean latestOnly;

	/**
	 * @param repo
	 */
	public LineHistoryCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Set file path
	 *
	 * @param filePath
	 * @return this command
	 */
	public LineHistoryCommand setFilePath(String filePath) {
		this.path = filePath;
		return this;
	}

	/**
	 * Set diff algorithm
	 *
	 * @param diffAlgorithm
	 * @return this command
	 */
	public LineHistoryCommand setDiffAlgorithm(DiffAlgorithm diffAlgorithm) {
		this.diffAlgorithm = diffAlgorithm;
		return this;
	}

	/**
	 * Set raw text comparator
	 *
	 * @param textComparator
	 * @return this command
	 */
	public LineHistoryCommand setTextComparator(RawTextComparator textComparator) {
		this.textComparator = textComparator;
		return this;
	}

	/**
	 * Set start commit id
	 *
	 * @param commit
	 * @return this command
	 */
	public LineHistoryCommand setStartCommit(ObjectId commit) {
		this.startCommit = commit;
		return this;
	}

	/**
	 * Set end commit id
	 *
	 * @param commit
	 * @return this command
	 */
	public LineHistoryCommand setEndCommit(ObjectId commit) {
		this.endCommit = commit;
		return this;
	}

	/**
	 * Set whether to build latest revision only
	 *
	 * @param latest
	 * @return this command
	 */
	public LineHistoryCommand setLatestOnly(boolean latest) {
		this.latestOnly = latest;
		return this;
	}

	/**
	 * Generate a revision container with the comprehensive line history.
	 *
	 * @return revision container
	 */
	public List<Revision> call() throws JGitInternalException {
		checkCallable();
		RevisionBuilder builder = new RevisionBuilder(repo, path);
		try {
			if (diffAlgorithm != null)
				builder.setDiffAlgorithm(diffAlgorithm);
			if (textComparator != null)
				builder.setTextComparator(textComparator);
			builder.setStart(startCommit);
			builder.setEnd(endCommit);
			if (latestOnly)
				return Collections.singletonList(builder.buildLatest());
			else
				return builder.buildAll();
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}
}

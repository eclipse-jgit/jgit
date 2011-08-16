/*
 * Copyright (C) 2011, Tomasz Zarna <Tomasz.Zarna@pl.ibm.com>
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

import static org.eclipse.jgit.lib.Constants.HEAD;

import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/**
 * Show changes between commits, commit and working tree, etc.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-diff.html"
 *      >Git documentation about diff</a>
 */
public class DiffCommand extends GitCommand<List<DiffEntry>> {
	private AbstractTreeIterator oldTree;

	private AbstractTreeIterator newTree;

	private boolean cached;

	private TreeFilter pathFilter = TreeFilter.ALL;

	// TODO: fixed to true for now
	private boolean showNameAndStatusOnly = true;

	/**
	 * @param repo
	 */
	protected DiffCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Executes the {@code Diff} command with all the options and parameters
	 * collected by the setter methods (e.g. {@link #setCached(boolean)} of this
	 * class. Each instance of this class should only be used for one invocation
	 * of the command. Don't call this method twice on an instance.
	 *
	 * @return a DiffEntry for each path which is different
	 */
	public List<DiffEntry> call() throws GitAPIException, IOException {
		final DiffFormatter diffFmt = new DiffFormatter(null);
		diffFmt.setRepository(repo);
		try {
			if (cached) {
				if (oldTree == null) {
					ObjectId head = repo.resolve(HEAD + "^{tree}");
					if (head == null)
						throw new NoHeadException(JGitText.get().cannotReadTree);
					CanonicalTreeParser p = new CanonicalTreeParser();
					ObjectReader reader = repo.newObjectReader();
					try {
						p.reset(reader, head);
					} finally {
						reader.release();
					}
					oldTree = p;
				}
				newTree = new DirCacheIterator(repo.readDirCache());
			} else {
				if (oldTree == null)
					oldTree = new DirCacheIterator(repo.readDirCache());
				if (newTree == null)
					newTree = new FileTreeIterator(repo);
			}

			diffFmt.setPathFilter(pathFilter);

			if (showNameAndStatusOnly) {
				return diffFmt.scan(oldTree, newTree);
			} else {
				// TODO: not implemented yet
				throw new UnsupportedOperationException();
			}
		} finally {
			diffFmt.release();
		}
	}

	/**
	 *
	 * @param cached
	 *            whether to view the changes you staged for the next commit
	 * @return this instance
	 */
	public DiffCommand setCached(boolean cached) {
		this.cached = cached;
		return this;
	}

	/**
	 * @param pathFilter
	 *            parameter, used to limit the diff to the named path
	 * @return this instance
	 */
	public DiffCommand setPathFilter(TreeFilter pathFilter) {
		this.pathFilter = pathFilter;
		return this;
	}

	/**
	 * @param oldTree
	 *            the previous state
	 * @return this instance
	 */
	public DiffCommand setOldTree(AbstractTreeIterator oldTree) {
		this.oldTree = oldTree;
		return this;
	}

	/**
	 * @param newTree
	 *            the updated state
	 * @return this instance
	 */
	public DiffCommand setNewTree(AbstractTreeIterator newTree) {
		this.newTree = newTree;
		return this;
	}

	/**
	 * @param showNameAndStatusOnly
	 *            whether to return only names and status of changed files
	 * @return this instance
	 */
	public DiffCommand setShowNameAndStatusOnly(boolean showNameAndStatusOnly) {
		// TODO: not implemented yet
		if (!showNameAndStatusOnly)
			throw new UnsupportedOperationException();
		this.showNameAndStatusOnly = showNameAndStatusOnly;
		return this;
	}
}
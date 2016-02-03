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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.io.NullOutputStream;

/**
 * Show changes between commits, commit and working tree, etc.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-diff.html"
 *      >Git documentation about diff</a>
 */
public class DiffCommand extends GitCommand<List<DiffEntry>> {
	private ObjectId oldTreeish;

	private AbstractTreeIterator oldTree;

	private ObjectId newTreeish;

	private AbstractTreeIterator newTree;

	private boolean cached;

	private TreeFilter pathFilter = TreeFilter.ALL;

	private boolean showNameAndStatusOnly;

	private OutputStream out;

	private int contextLines = -1;

	private String sourcePrefix;

	private String destinationPrefix;

	private ProgressMonitor monitor = NullProgressMonitor.INSTANCE;

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
	public List<DiffEntry> call() throws GitAPIException {
		try (DiffFormatter diffFmt = newDiffFormatter();
				RevWalk rw = new RevWalk(diffFmt.getObjectReader())) {
			AbstractTreeIterator oldTreeIt = resolveOldTree(rw);
			AbstractTreeIterator newTreeIt = resolveNewTree(rw);
			List<DiffEntry> result = diffFmt.scan(oldTreeIt, newTreeIt);
			if (showNameAndStatusOnly) {
				return result;
			}
			if (contextLines >= 0)
				diffFmt.setContext(contextLines);
			if (destinationPrefix != null)
				diffFmt.setNewPrefix(destinationPrefix);
			if (sourcePrefix != null)
				diffFmt.setOldPrefix(sourcePrefix);
			diffFmt.format(result);
			diffFmt.flush();
			return result;
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

	private DiffFormatter newDiffFormatter() {
		final DiffFormatter diffFmt;
		if (out != null && !showNameAndStatusOnly) {
			diffFmt = new DiffFormatter(new BufferedOutputStream(out));
		} else {
			diffFmt = new DiffFormatter(NullOutputStream.INSTANCE);
		}
		diffFmt.setRepository(repo);
		diffFmt.setProgressMonitor(monitor);
		diffFmt.setPathFilter(pathFilter);
		return diffFmt;
	}

	private AbstractTreeIterator resolveOldTree(RevWalk rw)
			throws NoHeadException, IOException {
		if (oldTree != null) {
			return oldTree;
		} else if (oldTreeish != null) {
			return getTreeIterator(rw, oldTreeish);
		} else if (cached) {
			ObjectId head = repo.resolve(HEAD + "^{tree}"); //$NON-NLS-1$
			if (head == null) {
				throw new NoHeadException(JGitText.get().cannotReadTree);
			}
			return getTreeIterator(rw, head);
		} else {
			return new DirCacheIterator(repo.readDirCache());
		}
	}

	private AbstractTreeIterator resolveNewTree(RevWalk rw) throws IOException {
		if (cached) {
			return new DirCacheIterator(repo.readDirCache());
		} else if (newTree != null) {
			return newTree;
		} else if (newTreeish != null) {
			return getTreeIterator(rw, newTreeish);
		} else {
			return new FileTreeIterator(repo);
		}
	}

	private static AbstractTreeIterator getTreeIterator(RevWalk rw, ObjectId id)
			throws IOException {
		RevTree tree = rw.parseTree(id);
		CanonicalTreeParser p = new CanonicalTreeParser();
		p.reset(rw.getObjectReader(), tree);
		return p;
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
	 * @param oldTreeish
	 *            id of the previous state
	 * @return this instance
	 * @since 4.3
	 */
	public DiffCommand setOldTree(ObjectId oldTreeish) {
		this.oldTreeish = oldTreeish.copy();
		oldTree = null;
		return this;
	}

	/**
	 * @param oldTree
	 *            the previous state
	 * @return this instance
	 */
	public DiffCommand setOldTree(AbstractTreeIterator oldTree) {
		this.oldTree = oldTree;
		oldTreeish = null;
		return this;
	}

	/**
	 * @param newTreeish
	 *            id of the updated state
	 * @return this instance
	 * @since 4.3
	 */
	public DiffCommand setNewTree(ObjectId newTreeish) {
		this.newTreeish = newTreeish.copy();
		newTree = null;
		return this;
	}

	/**
	 * @param newTree
	 *            the updated state
	 * @return this instance
	 */
	public DiffCommand setNewTree(AbstractTreeIterator newTree) {
		this.newTree = newTree;
		newTreeish = null;
		return this;
	}

	/**
	 * @param showNameAndStatusOnly
	 *            whether to return only names and status of changed files
	 * @return this instance
	 */
	public DiffCommand setShowNameAndStatusOnly(boolean showNameAndStatusOnly) {
		this.showNameAndStatusOnly = showNameAndStatusOnly;
		return this;
	}

	/**
	 * @param out
	 *            the stream to write line data
	 * @return this instance
	 */
	public DiffCommand setOutputStream(OutputStream out) {
		this.out = out;
		return this;
	}

	/**
	 * Set number of context lines instead of the usual three.
	 *
	 * @param contextLines
	 *            the number of context lines
	 * @return this instance
	 */
	public DiffCommand setContextLines(int contextLines) {
		this.contextLines = contextLines;
		return this;
	}

	/**
	 * Set the given source prefix instead of "a/".
	 *
	 * @param sourcePrefix
	 *            the prefix
	 * @return this instance
	 */
	public DiffCommand setSourcePrefix(String sourcePrefix) {
		this.sourcePrefix = sourcePrefix;
		return this;
	}

	/**
	 * Set the given destination prefix instead of "b/".
	 *
	 * @param destinationPrefix
	 *            the prefix
	 * @return this instance
	 */
	public DiffCommand setDestinationPrefix(String destinationPrefix) {
		this.destinationPrefix = destinationPrefix;
		return this;
	}

	/**
	 * The progress monitor associated with the diff operation. By default, this
	 * is set to <code>NullProgressMonitor</code>
	 *
	 * @see NullProgressMonitor
	 *
	 * @param monitor
	 *            a progress monitor
	 * @return this instance
	 */
	public DiffCommand setProgressMonitor(ProgressMonitor monitor) {
		if (monitor == null) {
			monitor = NullProgressMonitor.INSTANCE;
		}
		this.monitor = monitor;
		return this;
	}
}

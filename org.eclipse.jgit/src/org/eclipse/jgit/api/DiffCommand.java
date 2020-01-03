/*
 * Copyright (C) 2011, Tomasz Zarna <Tomasz.Zarna@pl.ibm.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
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
	private AbstractTreeIterator oldTree;

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
	 * Constructor for DiffCommand
	 *
	 * @param repo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 */
	protected DiffCommand(Repository repo) {
		super(repo);
	}

	private DiffFormatter getDiffFormatter() {
		return out != null && !showNameAndStatusOnly
				? new DiffFormatter(new BufferedOutputStream(out))
				: new DiffFormatter(NullOutputStream.INSTANCE);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Executes the {@code Diff} command with all the options and parameters
	 * collected by the setter methods (e.g. {@link #setCached(boolean)} of this
	 * class. Each instance of this class should only be used for one invocation
	 * of the command. Don't call this method twice on an instance.
	 */
	@Override
	public List<DiffEntry> call() throws GitAPIException {
		try (DiffFormatter diffFmt = getDiffFormatter()) {
			diffFmt.setRepository(repo);
			diffFmt.setProgressMonitor(monitor);
			if (cached) {
				if (oldTree == null) {
					ObjectId head = repo.resolve(HEAD + "^{tree}"); //$NON-NLS-1$
					if (head == null)
						throw new NoHeadException(JGitText.get().cannotReadTree);
					CanonicalTreeParser p = new CanonicalTreeParser();
					try (ObjectReader reader = repo.newObjectReader()) {
						p.reset(reader, head);
					}
					oldTree = p;
				}
				newTree = new DirCacheIterator(repo.readDirCache());
			} else {
				if (oldTree == null) {
					oldTree = new DirCacheIterator(repo.readDirCache());
				}
				if (newTree == null) {
					newTree = new FileTreeIterator(repo);
				}
			}

			diffFmt.setPathFilter(pathFilter);

			List<DiffEntry> result = diffFmt.scan(oldTree, newTree);
			if (showNameAndStatusOnly) {
				return result;
			}
			if (contextLines >= 0) {
				diffFmt.setContext(contextLines);
			}
			if (destinationPrefix != null) {
				diffFmt.setNewPrefix(destinationPrefix);
			}
			if (sourcePrefix != null) {
				diffFmt.setOldPrefix(sourcePrefix);
			}
			diffFmt.format(result);
			diffFmt.flush();
			return result;
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

	/**
	 * Whether to view the changes staged for the next commit
	 *
	 * @param cached
	 *            whether to view the changes staged for the next commit
	 * @return this instance
	 */
	public DiffCommand setCached(boolean cached) {
		this.cached = cached;
		return this;
	}

	/**
	 * Set path filter
	 *
	 * @param pathFilter
	 *            parameter, used to limit the diff to the named path
	 * @return this instance
	 */
	public DiffCommand setPathFilter(TreeFilter pathFilter) {
		this.pathFilter = pathFilter;
		return this;
	}

	/**
	 * Set old tree
	 *
	 * @param oldTree
	 *            the previous state
	 * @return this instance
	 */
	public DiffCommand setOldTree(AbstractTreeIterator oldTree) {
		this.oldTree = oldTree;
		return this;
	}

	/**
	 * Set new tree
	 *
	 * @param newTree
	 *            the updated state
	 * @return this instance
	 */
	public DiffCommand setNewTree(AbstractTreeIterator newTree) {
		this.newTree = newTree;
		return this;
	}

	/**
	 * Set whether to return only names and status of changed files
	 *
	 * @param showNameAndStatusOnly
	 *            whether to return only names and status of changed files
	 * @return this instance
	 */
	public DiffCommand setShowNameAndStatusOnly(boolean showNameAndStatusOnly) {
		this.showNameAndStatusOnly = showNameAndStatusOnly;
		return this;
	}

	/**
	 * Set output stream
	 *
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

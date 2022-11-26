/*
 * Copyright (C) 2022 Yuriy Mitrofanov <mitr15fan15v@gmail.com>
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
 * Copyright (C) 2008, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuildIterator;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.events.WorkingTreeModifiedEvent;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Restore files from the index or working directory.
 * <p>
 * It has setters for all supported options and arguments of this command and a
 * {@link #call()} method to finally execute the command. Each instance of this
 * class should only be used for one invocation of the command (means: one call
 * to {@link #call()}).
 * <p>
 * Examples (<code>git</code> is a {@link Git} instance):
 * <p>
 * restore file "test.txt" from working directory:
 *
 * <pre>
 * git.restore().addFilepattern(&quot;test.txt&quot;).call();
 * </pre>
 * <p>
 * Restore file "new.txt" from the index:
 *
 * <pre>
 * git.restore().setCached(true).addFilepattern(&quot;new.txt&quot;).call();
 * </pre>
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-restore.html"
 *      >Git documentation about Restore</a>
 */
public class RestoreCommand extends GitCommand<DirCache> {

	private Collection<String> filepatterns;

	private	List<String> actuallyRestoredFiles = new ArrayList<>();

	/** Only restore files from index, not from a working directory */
	private boolean cached = false;

	private boolean addAll = false;
	/**
	 * Constructor for RestoreCommand.
	 *
	 * @param repo
	 *            the {@link Repository}
	 */
	public RestoreCommand(Repository repo) {
		super(repo);
		filepatterns = new LinkedList<>();
	}

	/**
	 * Add file name pattern of files to be restored
	 *
	 * @param filepattern
	 *            repository-relative path of file to restore (with
	 *            <code>/</code> as separator)
	 * @return {@code this}
	 */
	public RestoreCommand addFilepattern(String filepattern) {
		checkCallable();
		filepatterns.add(filepattern);
		return this;
	}

	/**
	 * Only restore the specified files from the index or.
	 *
	 * @param cached
	 *            {@code true} if files should only be restored from index,
	 *            {@code false} if files should only be restored from working
	 *            directory.
	 * @return {@code this}
	 * @since 6.4
	 */
	public RestoreCommand setCached(boolean cached) {
		checkCallable();
		this.cached = cached;
		return this;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Executes the {@code Restore} command. Each instance of this class
	 * should only be used for one invocation of the command.
	 * Don't call this method twice
	 * on an instance.
	 * @since 6.4
	 */
	@Override
	public DirCache call() throws GitAPIException,
			NoFilepatternException {

		if (filepatterns.isEmpty()) {
			throw new NoFilepatternException(
					JGitText.get().atLeastOnePatternIsRequired);
		}
		addAll = filepatterns.contains("."); //$NON-NLS-1$

		checkCallable();
		return restore();
	}

	private DirCache restore() throws GitAPIException {
		DirCache dc = null;

		try (TreeWalk tw = new TreeWalk(repo)) {
			dc = repo.readDirCache();

			DirCacheBuilder builder = dc.builder();
			tw.reset(); // drop the first empty tree, which we do not need here
			tw.setRecursive(true);
			if (!addAll) {
				tw.setFilter(PathFilterGroup.createFromStrings(filepatterns));
			}
			tw.addTree(new DirCacheBuildIterator(builder));

			while (tw.next()) {
				final FileMode mode = tw.getFileMode(0);
				if (mode.getObjectType() == Constants.OBJ_BLOB) {
					String relativePath = tw.getPathString();
					if (cached) {
						if (restoreFromCache(relativePath)) {
							actuallyRestoredFiles.add(relativePath);
						}
					} else if (restoreFromIndex(relativePath)) {
							actuallyRestoredFiles.add(relativePath);
					}
				}
			}
			setCallable(false);
		} catch (IOException e) {
			throw new JGitInternalException(
					JGitText.get()
							.exceptionCaughtDuringExecutionOfRestoreCommand,
					e);
		} finally {
			if (!actuallyRestoredFiles.isEmpty()) {
				repo.fireEvent(new WorkingTreeModifiedEvent(null,
						actuallyRestoredFiles));
			}
		}

		return dc;
	}

	private boolean restoreFromIndex(String relativePath)
			throws GitAPIException {
		try (Git git = new Git(repo)) {
			File p = new File(repo.getWorkTree(),
					relativePath);
			CheckoutCommand checkout = git.checkout();
			boolean restored = false;
			while (p != null && !p.equals(repo.getWorkTree()) && p.isFile()) {
				restored = true;
				checkout.addPath(relativePath);
				p = p.getParentFile();
			}
			checkout.call();

			return restored;
		}
	}

	private boolean restoreFromCache(String relativePath)
			throws GitAPIException {
		try (Git git = new Git(repo)) {
			File p = new File(repo.getWorkTree(),
					relativePath);
			ResetCommand reset = git.reset();
			boolean restored = false;
			while (p != null && !p.equals(repo.getWorkTree()) && p.isFile()) {
				restored = true;
				reset.addPath(relativePath);
				p = p.getParentFile();
			}
			reset.call();

			return restored;
		}
	}

}

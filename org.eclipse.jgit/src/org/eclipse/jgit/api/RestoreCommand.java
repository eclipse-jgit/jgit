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

import org.eclipse.jgit.api.errors.FileNotFoundException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
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
public class RestoreCommand extends GitCommand<List<String>> {

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
	 * Only restore the specified files from the index or working directory.
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
	public List<String> call() throws GitAPIException, NoWorkTreeException {

		if (filepatterns.isEmpty()) {
			throw new NoFilepatternException(
					JGitText.get().atLeastOnePatternIsRequired);
		}

		addAll = filepatterns.contains("."); //$NON-NLS-1$
		if(!addAll) {
			for (String name : filepatterns) {
				File p = new File(repo.getWorkTree(),	name);
				if (!p.exists()) {
					throw new FileNotFoundException(MessageFormat.format(
							JGitText.get().pathspecDidNotMatch, name));
				}
			}
		}

		checkCallable();
		return restore();
	}

	private List<String> restore() throws GitAPIException {
		WorkingTreeIterator workingTreeIt = new FileTreeIterator(repo);
		try {
			IndexDiff diff = new IndexDiff(repo, Constants.HEAD,
					workingTreeIt);
			if (!addAll) {
				diff.setFilter(PathFilterGroup.createFromStrings(
						filepatterns));
			}
			diff.diff();

			try (Git git = new Git(repo)) {
				if (cached) {
					if (diff.getChanged().size() > 0 ||
							diff.getAdded().size() > 0) {
						ResetCommand reset = git.reset();
						for (String ent : diff.getChanged()) {
							File p = new File(repo.getWorkTree(), ent);
							if (p.isFile()) {
								reset.addPath(ent);
								actuallyRestoredFiles.add(ent);
							}
						}
						for (String ent : diff.getAdded()) {
							File p = new File(repo.getWorkTree(), ent);
							if (p.isFile()) {
								reset.addPath(ent);
								actuallyRestoredFiles.add(ent);
							}
						}
						reset.call();
					}
				} else {
					if (diff.getModified().size() > 0) {
						CheckoutCommand checkout = git.checkout();
						for (String ent : diff.getModified()) {
							File p = new File(repo.getWorkTree(), ent);
							if (p.isFile()) {
								checkout.addPath(ent);
								actuallyRestoredFiles.add(ent);
							}
						}
						checkout.call();
					}
				}
			}
			setCallable(false);
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
		return actuallyRestoredFiles;
	}
}

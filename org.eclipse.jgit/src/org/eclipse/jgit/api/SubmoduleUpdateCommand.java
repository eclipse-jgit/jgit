/*
 * Copyright (C) 2011, GitHub Inc.
 * Copyright (C) 2016, Laurent Delaigue <laurent.delaigue@obeo.fr>
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidConfigurationException;
import org.eclipse.jgit.api.errors.InvalidMergeHeadsException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;

/**
 * A class used to execute a submodule update command.
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-submodule.html"
 *      >Git documentation about submodules</a>
 */
public class SubmoduleUpdateCommand extends
		TransportCommand<SubmoduleUpdateCommand, Collection<String>> {

	private ProgressMonitor monitor;

	private final Collection<String> paths;

	private MergeStrategy strategy = MergeStrategy.RECURSIVE;

	private CloneCommand.Callback callback;

	private FetchCommand.Callback fetchCallback;

	private boolean fetch = false;

	/**
	 * <p>
	 * Constructor for SubmoduleUpdateCommand.
	 * </p>
	 *
	 * @param repo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 */
	public SubmoduleUpdateCommand(Repository repo) {
		super(repo);
		paths = new ArrayList<>();
	}

	/**
	 * The progress monitor associated with the clone operation. By default,
	 * this is set to <code>NullProgressMonitor</code>
	 *
	 * @see NullProgressMonitor
	 * @param monitor
	 *            a {@link org.eclipse.jgit.lib.ProgressMonitor} object.
	 * @return this command
	 */
	public SubmoduleUpdateCommand setProgressMonitor(
			final ProgressMonitor monitor) {
		this.monitor = monitor;
		return this;
	}

	/**
	 * Whether to fetch the submodules before we update them. By default, this
	 * is set to <code>false</code>
	 *
	 * @param fetch
	 *            whether to fetch the submodules before we update them
	 * @return this command
	 * @since 4.9
	 */
	public SubmoduleUpdateCommand setFetch(boolean fetch) {
		this.fetch = fetch;
		return this;
	}

	/**
	 * Add repository-relative submodule path to initialize
	 *
	 * @param path
	 *            (with <code>/</code> as separator)
	 * @return this command
	 */
	public SubmoduleUpdateCommand addPath(String path) {
		paths.add(path);
		return this;
	}

	private Repository getOrCloneSubmodule(SubmoduleWalk generator, String url)
			throws IOException, GitAPIException {
		Repository repository = generator.getRepository();
		if (repository == null) {
			if (callback != null) {
				callback.cloningSubmodule(generator.getPath());
			}
			CloneCommand clone = Git.cloneRepository();
			configure(clone);
			clone.setURI(url);
			clone.setDirectory(generator.getDirectory());
			clone.setGitDir(
					new File(new File(repo.getDirectory(), Constants.MODULES),
							generator.getPath()));
			if (monitor != null) {
				clone.setProgressMonitor(monitor);
			}
			repository = clone.call().getRepository();
		} else if (this.fetch) {
			if (fetchCallback != null) {
				fetchCallback.fetchingSubmodule(generator.getPath());
			}
			FetchCommand fetchCommand = Git.wrap(repository).fetch();
			if (monitor != null) {
				fetchCommand.setProgressMonitor(monitor);
			}
			configure(fetchCommand);
			fetchCommand.call();
		}
		return repository;
	}

	/**
	 * {@inheritDoc}
	 *
	 * Execute the SubmoduleUpdateCommand command.
	 */
	@Override
	public Collection<String> call() throws InvalidConfigurationException,
			NoHeadException, ConcurrentRefUpdateException,
			CheckoutConflictException, InvalidMergeHeadsException,
			WrongRepositoryStateException, NoMessageException, NoHeadException,
			RefNotFoundException, GitAPIException {
		checkCallable();

		try (SubmoduleWalk generator = SubmoduleWalk.forIndex(repo)) {
			if (!paths.isEmpty())
				generator.setFilter(PathFilterGroup.createFromStrings(paths));
			List<String> updated = new ArrayList<>();
			while (generator.next()) {
				// Skip submodules not registered in .gitmodules file
				if (generator.getModulesPath() == null)
					continue;
				// Skip submodules not registered in parent repository's config
				String url = generator.getConfigUrl();
				if (url == null)
					continue;

				try (Repository submoduleRepo = getOrCloneSubmodule(generator,
						url); RevWalk walk = new RevWalk(submoduleRepo)) {
					RevCommit commit = walk
							.parseCommit(generator.getObjectId());

					String update = generator.getConfigUpdate();
					if (null == update) {
						// Checkout commit referenced in parent repository's
						// index as a detached HEAD
						DirCacheCheckout co = new DirCacheCheckout(
								submoduleRepo, submoduleRepo.lockDirCache(),
								commit.getTree());
						co.setFailOnConflict(true);
						co.setProgressMonitor(monitor);
						co.checkout();
						RefUpdate refUpdate = submoduleRepo.updateRef(
								Constants.HEAD, true);
						refUpdate.setNewObjectId(commit);
						refUpdate.forceUpdate();
						if (callback != null) {
							callback.checkingOut(commit,
									generator.getPath());
						}
					} else
						switch (update) {
						case ConfigConstants.CONFIG_KEY_MERGE:
							MergeCommand merge = new MergeCommand(
									submoduleRepo);
							merge.include(commit);
							merge.setProgressMonitor(monitor);
							merge.setStrategy(strategy);
							merge.call();
							break;
						case ConfigConstants.CONFIG_KEY_REBASE:
							RebaseCommand rebase = new RebaseCommand(
									submoduleRepo);
							rebase.setUpstream(commit);
							rebase.setProgressMonitor(monitor);
							rebase.setStrategy(strategy);
							rebase.call();
							break;
						default:
							// Checkout commit referenced in parent repository's
							// index as a detached HEAD
							DirCacheCheckout co = new DirCacheCheckout(
									submoduleRepo, submoduleRepo.lockDirCache(),
									commit.getTree());
							co.setFailOnConflict(true);
							co.setProgressMonitor(monitor);
							co.checkout();
							RefUpdate refUpdate = submoduleRepo
									.updateRef(Constants.HEAD, true);
							refUpdate.setNewObjectId(commit);
							refUpdate.forceUpdate();
							if (callback != null) {
								callback.checkingOut(commit,
										generator.getPath());
							}
							break;
						}
				}
				updated.add(generator.getPath());
			}
			return updated;
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} catch (ConfigInvalidException e) {
			throw new InvalidConfigurationException(e.getMessage(), e);
		}
	}

	/**
	 * Setter for the field <code>strategy</code>.
	 *
	 * @param strategy
	 *            The merge strategy to use during this update operation.
	 * @return {@code this}
	 * @since 3.4
	 */
	public SubmoduleUpdateCommand setStrategy(MergeStrategy strategy) {
		this.strategy = strategy;
		return this;
	}

	/**
	 * Set status callback for submodule clone operation.
	 *
	 * @param callback
	 *            the callback
	 * @return {@code this}
	 * @since 4.8
	 */
	public SubmoduleUpdateCommand setCallback(CloneCommand.Callback callback) {
		this.callback = callback;
		return this;
	}

	/**
	 * Set status callback for submodule fetch operation.
	 *
	 * @param callback
	 *            the callback
	 * @return {@code this}
	 * @since 4.9
	 */
	public SubmoduleUpdateCommand setFetchCallback(
			FetchCommand.Callback callback) {
		this.fetchCallback = callback;
		return this;
	}
}

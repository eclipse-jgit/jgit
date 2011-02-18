/*
 * Copyright (C) 2011, Chris Aniszczyk <caniszczyk@gmail.com>
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
import java.net.URISyntaxException;
import java.util.concurrent.Callable;

import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

/**
 * Clone a repository into a new working directory
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-clone.html"
 *      >Git documentation about Clone</a>
 */
public class CloneCommand implements Callable<Git> {

	private String uri;

	private File directory;

	private boolean bare;

	private String remote = Constants.DEFAULT_REMOTE_NAME;

	private String branch = Constants.HEAD;

	private ProgressMonitor monitor = NullProgressMonitor.INSTANCE;

	private CredentialsProvider credentialsProvider;

	/**
	 * Executes the {@code Clone} command.
	 *
	 * @throws JGitInternalException
	 *             if the repository can't be created
	 * @return the newly created {@code Git} object with associated repository
	 */
	public Git call() throws JGitInternalException {
		try {
			URIish u = new URIish(uri);
			Repository repository = init(u);
			FetchResult result = fetch(repository, u);
			checkout(repository, result);
			return new Git(repository);
		} catch (IOException ioe) {
			throw new JGitInternalException(ioe.getMessage(), ioe);
		} catch (InvalidRemoteException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} catch (URISyntaxException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}

	private Repository init(URIish u) {
		InitCommand command = Git.init();
		command.setBare(bare);
		if (directory == null)
			directory = new File(u.getHumanishName(), Constants.DOT_GIT);
		command.setDirectory(directory);
		return command.call().getRepository();
	}

	private FetchResult fetch(Repository repo, URIish u)
			throws URISyntaxException,
			JGitInternalException,
			InvalidRemoteException, IOException {
		// create the remote config and save it
		RemoteConfig config = new RemoteConfig(repo.getConfig(), remote);
		config.addURI(u);

		final String dst = Constants.R_REMOTES + config.getName();
		RefSpec refSpec = new RefSpec();
		refSpec = refSpec.setForceUpdate(true);
		refSpec = refSpec.setSourceDestination(Constants.R_HEADS + "*", dst + "/*"); //$NON-NLS-1$ //$NON-NLS-2$

		config.addFetchRefSpec(refSpec);
		config.update(repo.getConfig());

		repo.getConfig().setString(ConfigConstants.CONFIG_BRANCH_SECTION,
				branch, ConfigConstants.CONFIG_KEY_REMOTE, remote);
		repo.getConfig().setString(ConfigConstants.CONFIG_BRANCH_SECTION,
				branch, ConfigConstants.CONFIG_KEY_MERGE, branch);

		repo.getConfig().save();

		// run the fetch command
		FetchCommand command = new FetchCommand(repo);
		command.setRemote(remote);
		command.setProgressMonitor(monitor);
		if (credentialsProvider != null)
			command.setCredentialsProvider(credentialsProvider);
		return command.call();
	}

	private void checkout(Repository repo, FetchResult result)
			throws JGitInternalException,
			MissingObjectException, IncorrectObjectTypeException, IOException {

		if (branch.startsWith(Constants.R_HEADS)) {
			final RefUpdate head = repo.updateRef(Constants.HEAD);
			head.disableRefLog();
			head.link(branch);
		}

		final Ref head = result.getAdvertisedRef(branch);
		if (head == null || head.getObjectId() == null)
			return; // throw exception?

		final RevCommit commit = parseCommit(repo, head);

		boolean detached = !head.getName().startsWith(Constants.R_HEADS);
		RefUpdate u = repo.updateRef(Constants.HEAD, detached);
		u.setNewObjectId(commit.getId());
		u.forceUpdate();

		DirCache dc = repo.lockDirCache();
		DirCacheCheckout co = new DirCacheCheckout(repo, dc, commit.getTree());
		co.checkout();
	}

	private RevCommit parseCommit(final Repository repo, final Ref ref)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		final RevWalk rw = new RevWalk(repo);
		final RevCommit commit;
		try {
			commit = rw.parseCommit(ref.getObjectId());
		} finally {
			rw.release();
		}
		return commit;
	}

	/**
	 * @param uri
	 *            the uri to clone from
	 * @return this instance
	 */
	public CloneCommand setURI(String uri) {
		this.uri = uri;
		return this;
	}

	/**
	 * The optional directory associated with the clone operation. If the
	 * directory isn't set, a name associated with the source uri will be used.
	 *
	 * @see URIish#getHumanishName()
	 *
	 * @param directory
	 *            the directory to clone to
	 * @return this instance
	 */
	public CloneCommand setDirectory(File directory) {
		this.directory = directory;
		return this;
	}

	/**
	 * @param bare
	 *            whether the cloned repository is bare or not
	 * @return this instance
	 */
	public CloneCommand setBare(boolean bare) {
		this.bare = bare;
		return this;
	}

	/**
	 * @param remote
	 *            the branch to keep track of in the origin repository
	 * @return this instance
	 */
	public CloneCommand setRemote(String remote) {
		this.remote = remote;
		return this;
	}

	/**
	 * @param branch
	 *            the initial branch to check out when cloning the repository
	 * @return this instance
	 */
	public CloneCommand setBranch(String branch) {
		this.branch = branch;
		return this;
	}

	/**
	 * The progress monitor associated with the clone operation. By default,
	 * this is set to <code>NullProgressMonitor</code>
	 *
	 * @see NullProgressMonitor
	 *
	 * @param monitor
	 * @return {@code this}
	 */
	public CloneCommand setProgressMonitor(ProgressMonitor monitor) {
		this.monitor = monitor;
		return this;
	}

	/**
	 * @param credentialsProvider
	 *            the {@link CredentialsProvider} to use
	 * @return {@code this}
	 */
	public CloneCommand setCredentialsProvider(
			CredentialsProvider credentialsProvider) {
		this.credentialsProvider = credentialsProvider;
		return this;
	}

}

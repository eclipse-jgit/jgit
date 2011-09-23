/*
 * Copyright (C) 2011, GitHub Inc.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.submodule.SubmoduleGenerator;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/**
 * A class used to execute a submodule update command.
 * 
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-submodule.html"
 *      >Git documentation about submodules</a>
 */
public class SubmoduleUpdateCommand extends GitCommand<Collection<String>> {

	private ProgressMonitor monitor = NullProgressMonitor.INSTANCE;

	private CredentialsProvider credentialsProvider;

	private final Collection<String> paths;

	/**
	 * @param repo
	 */
	public SubmoduleUpdateCommand(Repository repo) {
		super(repo);
		paths = new ArrayList<String>();
	}

	/**
	 * The progress monitor associated with the clone operation. By default,
	 * this is set to <code>NullProgressMonitor</code>
	 * 
	 * @see NullProgressMonitor
	 * @param monitor
	 * @return this command
	 */
	public SubmoduleUpdateCommand setProgressMonitor(ProgressMonitor monitor) {
		this.monitor = monitor;
		return this;
	}

	/**
	 * @param credentialsProvider
	 *            the {@link CredentialsProvider} to use
	 * @return this command
	 */
	public SubmoduleUpdateCommand setCredentialsProvider(
			CredentialsProvider credentialsProvider) {
		this.credentialsProvider = credentialsProvider;
		return this;
	}

	/**
	 * Add submodule path to initialize
	 * 
	 * @param path
	 * @return this command
	 */
	public SubmoduleUpdateCommand addPath(String path) {
		checkCallable();
		paths.add(path);
		return this;
	}

	public Collection<String> call() throws JGitInternalException {
		checkCallable();

		TreeFilter filter = null;
		if (!paths.isEmpty())
			filter = PathFilterGroup.createFromStrings(paths);
		try {
			SubmoduleGenerator generator = new SubmoduleGenerator(repo, filter);
			List<String> updated = new ArrayList<String>();
			while (generator.next()) {
				// Skip existing repositories
				if (generator.getRepository() != null)
					continue;
				String url = generator.getConfigUrl();
				if (url == null)
					continue;

				CloneCommand clone = Git.cloneRepository();
				clone.setProgressMonitor(monitor);
				clone.setCredentialsProvider(credentialsProvider);
				clone.setURI(url);
				clone.setDirectory(generator.getDirectory());
				Repository submoduleRepo = clone.call().getRepository();

				RevWalk walk = new RevWalk(submoduleRepo);
				RevCommit commit = walk.parseCommit(generator.getObjectId());
				// Checkout commit referenced in parent repository's index as a
				// detached HEAD
				DirCacheCheckout co = new DirCacheCheckout(submoduleRepo,
						submoduleRepo.lockDirCache(), commit.getTree());
				co.setFailOnConflict(true);
				co.checkout();
				RefUpdate update = submoduleRepo
						.updateRef(Constants.HEAD, true);
				update.setNewObjectId(commit);
				update.forceUpdate();
			}
			return updated;
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} catch (ConfigInvalidException e) {
			throw new JGitInternalException(e.getMessage(), e);
		}
	}
}

/*
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
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

import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.Transport;

/**
 * A class used to execute a {@code Fetch} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-fetch.html"
 *      >Git documentation about Fetch</a>
 */
public class FetchCommand extends TransportCommand<FetchCommand, FetchResult> {

	private String remote = Constants.DEFAULT_REMOTE_NAME;

	private List<RefSpec> refSpecs;

	private ProgressMonitor monitor = NullProgressMonitor.INSTANCE;

	private boolean checkFetchedObjects;

	private Boolean removeDeletedRefs;

	private boolean dryRun;

	private boolean thin = Transport.DEFAULT_FETCH_THIN;

	private TagOpt tagOption;

	/**
	 * @param repo
	 */
	protected FetchCommand(Repository repo) {
		super(repo);
		refSpecs = new ArrayList<RefSpec>(3);
	}

	/**
	 * Executes the {@code fetch} command with all the options and parameters
	 * collected by the setter methods of this class. Each instance of this
	 * class should only be used for one invocation of the command (means: one
	 * call to {@link #call()})
	 *
	 * @return a {@link FetchResult} object representing the successful fetch
	 *         result
	 * @throws InvalidRemoteException
	 *             when called with an invalid remote uri
	 * @throws org.eclipse.jgit.api.errors.TransportException
	 *             when an error occurs during transport
	 */
	public FetchResult call() throws GitAPIException, InvalidRemoteException,
			org.eclipse.jgit.api.errors.TransportException {
		checkCallable();

		try {
			Transport transport = Transport.open(repo, remote);
			try {
				transport.setCheckFetchedObjects(checkFetchedObjects);
				transport.setRemoveDeletedRefs(isRemoveDeletedRefs());
				transport.setDryRun(dryRun);
				if (tagOption != null)
					transport.setTagOpt(tagOption);
				transport.setFetchThin(thin);
				configure(transport);

				FetchResult result = transport.fetch(monitor, refSpecs);
				return result;
			} finally {
				transport.close();
			}
		} catch (NoRemoteRepositoryException e) {
			throw new InvalidRemoteException(MessageFormat.format(
					JGitText.get().invalidRemote, remote), e);
		} catch (TransportException e) {
			throw new org.eclipse.jgit.api.errors.TransportException(
					e.getMessage(), e);
		} catch (URISyntaxException e) {
			throw new InvalidRemoteException(MessageFormat.format(
					JGitText.get().invalidRemote, remote));
		} catch (NotSupportedException e) {
			throw new JGitInternalException(
					JGitText.get().exceptionCaughtDuringExecutionOfFetchCommand,
					e);
		}

	}

	/**
	 * The remote (uri or name) used for the fetch operation. If no remote is
	 * set, the default value of <code>Constants.DEFAULT_REMOTE_NAME</code> will
	 * be used.
	 *
	 * @see Constants#DEFAULT_REMOTE_NAME
	 * @param remote
	 * @return {@code this}
	 */
	public FetchCommand setRemote(String remote) {
		checkCallable();
		this.remote = remote;
		return this;
	}

	/**
	 * @return the remote used for the remote operation
	 */
	public String getRemote() {
		return remote;
	}

	/**
	 * @return the timeout used for the fetch operation
	 */
	public int getTimeout() {
		return timeout;
	}

	/**
	 * @return whether to check received objects checked for validity
	 */
	public boolean isCheckFetchedObjects() {
		return checkFetchedObjects;
	}

	/**
	 * If set to true, objects received will be checked for validity
	 *
	 * @param checkFetchedObjects
	 * @return {@code this}
	 */
	public FetchCommand setCheckFetchedObjects(boolean checkFetchedObjects) {
		checkCallable();
		this.checkFetchedObjects = checkFetchedObjects;
		return this;
	}

	/**
	 * @return whether or not to remove refs which no longer exist in the source
	 */
	public boolean isRemoveDeletedRefs() {
		if (removeDeletedRefs != null)
			return removeDeletedRefs.booleanValue();
		else { // fall back to configuration
			boolean result = false;
			StoredConfig config = repo.getConfig();
			result = config.getBoolean(ConfigConstants.CONFIG_FETCH_SECTION,
					null, ConfigConstants.CONFIG_KEY_PRUNE, result);
			result = config.getBoolean(ConfigConstants.CONFIG_REMOTE_SECTION,
					remote, ConfigConstants.CONFIG_KEY_PRUNE, result);
			return result;
		}
	}

	/**
	 * If set to true, refs are removed which no longer exist in the source
	 *
	 * @param removeDeletedRefs
	 * @return {@code this}
	 */
	public FetchCommand setRemoveDeletedRefs(boolean removeDeletedRefs) {
		checkCallable();
		this.removeDeletedRefs = Boolean.valueOf(removeDeletedRefs);
		return this;
	}

	/**
	 * @return the progress monitor for the fetch operation
	 */
	public ProgressMonitor getProgressMonitor() {
		return monitor;
	}

	/**
	 * The progress monitor associated with the fetch operation. By default,
	 * this is set to <code>NullProgressMonitor</code>
	 *
	 * @see NullProgressMonitor
	 *
	 * @param monitor
	 * @return {@code this}
	 */
	public FetchCommand setProgressMonitor(ProgressMonitor monitor) {
		checkCallable();
		this.monitor = monitor;
		return this;
	}

	/**
	 * @return the ref specs
	 */
	public List<RefSpec> getRefSpecs() {
		return refSpecs;
	}

	/**
	 * The ref specs to be used in the fetch operation
	 *
	 * @param specs
	 * @return {@code this}
	 */
	public FetchCommand setRefSpecs(RefSpec... specs) {
		checkCallable();
		this.refSpecs.clear();
		for (RefSpec spec : specs)
			refSpecs.add(spec);
		return this;
	}

	/**
	 * The ref specs to be used in the fetch operation
	 *
	 * @param specs
	 * @return {@code this}
	 */
	public FetchCommand setRefSpecs(List<RefSpec> specs) {
		checkCallable();
		this.refSpecs.clear();
		this.refSpecs.addAll(specs);
		return this;
	}

	/**
	 * @return the dry run preference for the fetch operation
	 */
	public boolean isDryRun() {
		return dryRun;
	}

	/**
	 * Sets whether the fetch operation should be a dry run
	 *
	 * @param dryRun
	 * @return {@code this}
	 */
	public FetchCommand setDryRun(boolean dryRun) {
		checkCallable();
		this.dryRun = dryRun;
		return this;
	}

	/**
	 * @return the thin-pack preference for fetch operation
	 */
	public boolean isThin() {
		return thin;
	}

	/**
	 * Sets the thin-pack preference for fetch operation.
	 *
	 * Default setting is Transport.DEFAULT_FETCH_THIN
	 *
	 * @param thin
	 * @return {@code this}
	 */
	public FetchCommand setThin(boolean thin) {
		checkCallable();
		this.thin = thin;
		return this;
	}

	/**
	 * Sets the specification of annotated tag behavior during fetch
	 *
	 * @param tagOpt
	 * @return {@code this}
	 */
	public FetchCommand setTagOpt(TagOpt tagOpt) {
		checkCallable();
		this.tagOption = tagOpt;
		return this;
	}
}

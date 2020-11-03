/*
 * Copyright (C) 2011, Christoph Brill <egore911@egore911.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;

/**
 * The ls-remote command
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-ls-remote.html"
 *      >Git documentation about ls-remote</a>
 */
public class LsRemoteCommand extends
		TransportCommand<LsRemoteCommand, Collection<Ref>> {

	private String remote = Constants.DEFAULT_REMOTE_NAME;

	private boolean heads;

	private boolean tags;

	private String uploadPack;

	/**
	 * Constructor for LsRemoteCommand
	 *
	 * @param repo
	 *            local repository or null for operation without local
	 *            repository
	 */
	public LsRemoteCommand(Repository repo) {
		super(repo);
	}

	/**
	 * The remote (uri or name) used for the fetch operation. If no remote is
	 * set, the default value of <code>Constants.DEFAULT_REMOTE_NAME</code> will
	 * be used.
	 *
	 * @see Constants#DEFAULT_REMOTE_NAME
	 * @param remote
	 *            a {@link java.lang.String} object.
	 * @return {@code this}
	 */
	public LsRemoteCommand setRemote(String remote) {
		checkCallable();
		this.remote = remote;
		return this;
	}

	/**
	 * Include refs/heads in references results
	 *
	 * @param heads
	 *            whether to include refs/heads
	 * @return {@code this}
	 */
	public LsRemoteCommand setHeads(boolean heads) {
		this.heads = heads;
		return this;
	}

	/**
	 * Include refs/tags in references results
	 *
	 * @param tags
	 *            whether to include tags
	 * @return {@code this}
	 */
	public LsRemoteCommand setTags(boolean tags) {
		this.tags = tags;
		return this;
	}

	/**
	 * The full path of git-upload-pack on the remote host
	 *
	 * @param uploadPack
	 *            the full path of executable providing the git-upload-pack
	 *            service on remote host
	 * @return {@code this}
	 */
	public LsRemoteCommand setUploadPack(String uploadPack) {
		this.uploadPack = uploadPack;
		return this;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Execute the {@code LsRemote} command with all the options and parameters
	 * collected by the setter methods (e.g. {@link #setHeads(boolean)}) of this
	 * class. Each instance of this class should only be used for one invocation
	 * of the command. Don't call this method twice on an instance.
	 */
	@Override
	public Collection<Ref> call() throws GitAPIException,
			InvalidRemoteException,
			org.eclipse.jgit.api.errors.TransportException {
		return execute().values();
	}

	/**
	 * Same as {@link #call()}, but return Map instead of Collection.
	 *
	 * @return a map from names to references in the remote repository
	 * @throws org.eclipse.jgit.api.errors.GitAPIException
	 *             or subclass thereof when an error occurs
	 * @throws org.eclipse.jgit.api.errors.InvalidRemoteException
	 *             when called with an invalid remote uri
	 * @throws org.eclipse.jgit.api.errors.TransportException
	 *             for errors that occurs during transport
	 * @since 3.5
	 */
	public Map<String, Ref> callAsMap() throws GitAPIException,
			InvalidRemoteException,
			org.eclipse.jgit.api.errors.TransportException {
		return Collections.unmodifiableMap(execute());
	}

	private Map<String, Ref> execute() throws GitAPIException,
			InvalidRemoteException,
			org.eclipse.jgit.api.errors.TransportException {
		checkCallable();

		try (Transport transport = repo != null
				? Transport.open(repo, remote)
				: Transport.open(new URIish(remote))) {
			transport.setOptionUploadPack(uploadPack);
			configure(transport);
			Collection<RefSpec> refSpecs = new ArrayList<>(1);
			if (tags)
				refSpecs.add(new RefSpec(
						"refs/tags/*:refs/remotes/origin/tags/*")); //$NON-NLS-1$
			if (heads)
				refSpecs.add(new RefSpec("refs/heads/*:refs/remotes/origin/*")); //$NON-NLS-1$
			Collection<Ref> refs;
			Map<String, Ref> refmap = new HashMap<>();
			try (FetchConnection fc = transport.openFetch()) {
				refs = fc.getRefs();
				if (refSpecs.isEmpty())
					for (Ref r : refs)
						refmap.put(r.getName(), r);
				else
					for (Ref r : refs)
						for (RefSpec rs : refSpecs)
							if (rs.matchSource(r)) {
								refmap.put(r.getName(), r);
								break;
							}
				return refmap;
			}
		} catch (URISyntaxException e) {
			throw new InvalidRemoteException(MessageFormat.format(
					JGitText.get().invalidRemote, remote), e);
		} catch (NotSupportedException e) {
			throw new JGitInternalException(
					JGitText.get().exceptionCaughtDuringExecutionOfLsRemoteCommand,
					e);
		} catch (TransportException e) {
			throw new org.eclipse.jgit.api.errors.TransportException(
					e.getMessage(),
					e);
		}
	}

}

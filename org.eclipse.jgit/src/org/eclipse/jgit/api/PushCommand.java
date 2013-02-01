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

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;

/**
 * A class used to execute a {@code Push} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command.
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-push.html"
 *      >Git documentation about Push</a>
 */
public class PushCommand extends
		TransportCommand<PushCommand, Iterable<PushResult>> {

	private String remote = Constants.DEFAULT_REMOTE_NAME;

	private final List<RefSpec> refSpecs;

	private ProgressMonitor monitor = NullProgressMonitor.INSTANCE;

	private String receivePack = RemoteConfig.DEFAULT_RECEIVE_PACK;

	private boolean dryRun;

	private boolean force;

	private boolean thin = Transport.DEFAULT_PUSH_THIN;

	private OutputStream out;

	/**
	 * @param repo
	 */
	protected PushCommand(Repository repo) {
		super(repo);
		refSpecs = new ArrayList<RefSpec>(3);
	}

	/**
	 * Executes the {@code push} command with all the options and parameters
	 * collected by the setter methods of this class. Each instance of this
	 * class should only be used for one invocation of the command (means: one
	 * call to {@link #call()})
	 *
	 * @return an iteration over {@link PushResult} objects
	 * @throws InvalidRemoteException
	 *             when called with an invalid remote uri
	 * @throws org.eclipse.jgit.api.errors.TransportException
	 *             when an error occurs with the transport
	 * @throws GitAPIException
	 */
	public Iterable<PushResult> call() throws GitAPIException,
			InvalidRemoteException,
			org.eclipse.jgit.api.errors.TransportException {
		checkCallable();

		ArrayList<PushResult> pushResults = new ArrayList<PushResult>(3);

		try {
			if (refSpecs.isEmpty()) {
				RemoteConfig config = new RemoteConfig(repo.getConfig(),
						getRemote());
				refSpecs.addAll(config.getPushRefSpecs());
			}
			if (refSpecs.isEmpty()) {
				Ref head = repo.getRef(Constants.HEAD);
				if (head != null && head.isSymbolic())
					refSpecs.add(new RefSpec(head.getLeaf().getName()));
			}

			if (force) {
				for (int i = 0; i < refSpecs.size(); i++)
					refSpecs.set(i, refSpecs.get(i).setForceUpdate(true));
			}

			final List<Transport> transports;
			transports = Transport.openAll(repo, remote, Transport.Operation.PUSH);
			for (final Transport transport : transports) {
				transport.setPushThin(thin);
				if (receivePack != null)
					transport.setOptionReceivePack(receivePack);
				transport.setDryRun(dryRun);
				configure(transport);

				final Collection<RemoteRefUpdate> toPush = transport
						.findRemoteRefUpdatesFor(refSpecs);

				try {
					PushResult result = transport.push(monitor, toPush, out);
					pushResults.add(result);

				} catch (TransportException e) {
					throw new org.eclipse.jgit.api.errors.TransportException(
							e.getMessage(), e);
				} finally {
					transport.close();
				}
			}

		} catch (URISyntaxException e) {
			throw new InvalidRemoteException(MessageFormat.format(
					JGitText.get().invalidRemote, remote));
		} catch (TransportException e) {
			throw new org.eclipse.jgit.api.errors.TransportException(
					e.getMessage(), e);
		} catch (NotSupportedException e) {
			throw new JGitInternalException(
					JGitText.get().exceptionCaughtDuringExecutionOfPushCommand,
					e);
		} catch (IOException e) {
			throw new JGitInternalException(
					JGitText.get().exceptionCaughtDuringExecutionOfPushCommand,
					e);
		}

		return pushResults;

	}

	/**
	 * The remote (uri or name) used for the push operation. If no remote is
	 * set, the default value of <code>Constants.DEFAULT_REMOTE_NAME</code> will
	 * be used.
	 *
	 * @see Constants#DEFAULT_REMOTE_NAME
	 * @param remote
	 * @return {@code this}
	 */
	public PushCommand setRemote(String remote) {
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
	 * The remote executable providing receive-pack service for pack transports.
	 * If no receive-pack is set, the default value of
	 * <code>RemoteConfig.DEFAULT_RECEIVE_PACK</code> will be used.
	 *
	 * @see RemoteConfig#DEFAULT_RECEIVE_PACK
	 * @param receivePack
	 * @return {@code this}
	 */
	public PushCommand setReceivePack(String receivePack) {
		checkCallable();
		this.receivePack = receivePack;
		return this;
	}

	/**
	 * @return the receive-pack used for the remote operation
	 */
	public String getReceivePack() {
		return receivePack;
	}

	/**
	 * @return the timeout used for the push operation
	 */
	public int getTimeout() {
		return timeout;
	}

	/**
	 * @return the progress monitor for the push operation
	 */
	public ProgressMonitor getProgressMonitor() {
		return monitor;
	}

	/**
	 * The progress monitor associated with the push operation. By default, this
	 * is set to <code>NullProgressMonitor</code>
	 *
	 * @see NullProgressMonitor
	 *
	 * @param monitor
	 * @return {@code this}
	 */
	public PushCommand setProgressMonitor(ProgressMonitor monitor) {
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
	 * The ref specs to be used in the push operation
	 *
	 * @param specs
	 * @return {@code this}
	 */
	public PushCommand setRefSpecs(RefSpec... specs) {
		checkCallable();
		this.refSpecs.clear();
		Collections.addAll(refSpecs, specs);
		return this;
	}

	/**
	 * The ref specs to be used in the push operation
	 *
	 * @param specs
	 * @return {@code this}
	 */
	public PushCommand setRefSpecs(List<RefSpec> specs) {
		checkCallable();
		this.refSpecs.clear();
		this.refSpecs.addAll(specs);
		return this;
	}

	/**
	 * Push all branches under refs/heads/*.
	 *
	 * @return {code this}
	 */
	public PushCommand setPushAll() {
		refSpecs.add(Transport.REFSPEC_PUSH_ALL);
		return this;
	}

	/**
	 * Push all tags under refs/tags/*.
	 *
	 * @return {code this}
	 */
	public PushCommand setPushTags() {
		refSpecs.add(Transport.REFSPEC_TAGS);
		return this;
	}

	/**
	 * Add a reference to push.
	 *
	 * @param ref
	 *            the source reference. The remote name will match.
	 * @return {@code this}.
	 */
	public PushCommand add(Ref ref) {
		refSpecs.add(new RefSpec(ref.getLeaf().getName()));
		return this;
	}

	/**
	 * Add a reference to push.
	 *
	 * @param nameOrSpec
	 *            any reference name, or a reference specification.
	 * @return {@code this}.
	 * @throws JGitInternalException
	 *             the reference name cannot be resolved.
	 */
	public PushCommand add(String nameOrSpec) {
		if (0 <= nameOrSpec.indexOf(':')) {
			refSpecs.add(new RefSpec(nameOrSpec));
		} else {
			Ref src;
			try {
				src = repo.getRef(nameOrSpec);
			} catch (IOException e) {
				throw new JGitInternalException(
						JGitText.get().exceptionCaughtDuringExecutionOfPushCommand,
						e);
			}
			if (src != null)
				add(src);
		}
		return this;
	}

	/**
	 * @return the dry run preference for the push operation
	 */
	public boolean isDryRun() {
		return dryRun;
	}

	/**
	 * Sets whether the push operation should be a dry run
	 *
	 * @param dryRun
	 * @return {@code this}
	 */
	public PushCommand setDryRun(boolean dryRun) {
		checkCallable();
		this.dryRun = dryRun;
		return this;
	}

	/**
	 * @return the thin-pack preference for push operation
	 */
	public boolean isThin() {
		return thin;
	}

	/**
	 * Sets the thin-pack preference for push operation.
	 *
	 * Default setting is Transport.DEFAULT_PUSH_THIN
	 *
	 * @param thin
	 * @return {@code this}
	 */
	public PushCommand setThin(boolean thin) {
		checkCallable();
		this.thin = thin;
		return this;
	}

	/**
	 * @return the force preference for push operation
	 */
	public boolean isForce() {
		return force;
	}

	/**
	 * Sets the force preference for push operation.
	 *
	 * @param force
	 * @return {@code this}
	 */
	public PushCommand setForce(boolean force) {
		checkCallable();
		this.force = force;
		return this;
	}

	/**
	 * Sets the output stream to write sideband messages to
	 *
	 * @param out
	 * @return {@code this}
	 * @since 3.0
	 */
	public PushCommand setOutputStream(OutputStream out) {
		this.out = out;
		return this;
	}
}

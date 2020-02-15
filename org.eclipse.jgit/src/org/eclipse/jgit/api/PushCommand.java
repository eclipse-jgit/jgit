/*
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TooLargeObjectInPackException;
import org.eclipse.jgit.errors.TooLargePackException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefLeaseSpec;
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

	private final Map<String, RefLeaseSpec> refLeaseSpecs;

	private ProgressMonitor monitor = NullProgressMonitor.INSTANCE;

	private String receivePack = RemoteConfig.DEFAULT_RECEIVE_PACK;

	private boolean dryRun;
	private boolean atomic;
	private boolean force;
	private boolean thin = Transport.DEFAULT_PUSH_THIN;

	private OutputStream out;

	private List<String> pushOptions;

	/**
	 * <p>
	 * Constructor for PushCommand.
	 * </p>
	 *
	 * @param repo
	 *            the {@link org.eclipse.jgit.lib.Repository}
	 */
	protected PushCommand(Repository repo) {
		super(repo);
		refSpecs = new ArrayList<>(3);
		refLeaseSpecs = new HashMap<>();
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Execute the {@code push} command with all the options and parameters
	 * collected by the setter methods of this class. Each instance of this
	 * class should only be used for one invocation of the command (means: one
	 * call to {@link #call()})
	 */
	@Override
	public Iterable<PushResult> call() throws GitAPIException,
			InvalidRemoteException,
			org.eclipse.jgit.api.errors.TransportException {
		checkCallable();

		ArrayList<PushResult> pushResults = new ArrayList<>(3);

		try {
			if (refSpecs.isEmpty()) {
				RemoteConfig config = new RemoteConfig(repo.getConfig(),
						getRemote());
				refSpecs.addAll(config.getPushRefSpecs());
			}
			if (refSpecs.isEmpty()) {
				Ref head = repo.exactRef(Constants.HEAD);
				if (head != null && head.isSymbolic())
					refSpecs.add(new RefSpec(head.getLeaf().getName()));
			}

			if (force) {
				for (int i = 0; i < refSpecs.size(); i++)
					refSpecs.set(i, refSpecs.get(i).setForceUpdate(true));
			}

			final List<Transport> transports;
			transports = Transport.openAll(repo, remote, Transport.Operation.PUSH);
			for (@SuppressWarnings("resource") // Explicitly closed in finally
					final Transport transport : transports) {
				transport.setPushThin(thin);
				transport.setPushAtomic(atomic);
				if (receivePack != null)
					transport.setOptionReceivePack(receivePack);
				transport.setDryRun(dryRun);
				transport.setPushOptions(pushOptions);
				configure(transport);

				final Collection<RemoteRefUpdate> toPush = transport
						.findRemoteRefUpdatesFor(refSpecs, refLeaseSpecs);

				try {
					PushResult result = transport.push(monitor, toPush, out);
					pushResults.add(result);

				} catch (TooLargePackException e) {
					throw new org.eclipse.jgit.api.errors.TooLargePackException(
							e.getMessage(), e);
				} catch (TooLargeObjectInPackException e) {
					throw new org.eclipse.jgit.api.errors.TooLargeObjectInPackException(
							e.getMessage(), e);
				} catch (TransportException e) {
					throw new org.eclipse.jgit.api.errors.TransportException(
							e.getMessage(), e);
				} finally {
					transport.close();
				}
			}

		} catch (URISyntaxException e) {
			throw new InvalidRemoteException(
					MessageFormat.format(JGitText.get().invalidRemote, remote),
					e);
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
	 *            the remote name
	 * @return {@code this}
	 */
	public PushCommand setRemote(String remote) {
		checkCallable();
		this.remote = remote;
		return this;
	}

	/**
	 * Get remote name
	 *
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
	 *            name of the remote executable providing the receive-pack
	 *            service
	 * @return {@code this}
	 */
	public PushCommand setReceivePack(String receivePack) {
		checkCallable();
		this.receivePack = receivePack;
		return this;
	}

	/**
	 * Get the name of the remote executable providing the receive-pack service
	 *
	 * @return the receive-pack used for the remote operation
	 */
	public String getReceivePack() {
		return receivePack;
	}

	/**
	 * Get timeout used for push operation
	 *
	 * @return the timeout used for the push operation
	 */
	public int getTimeout() {
		return timeout;
	}

	/**
	 * Get the progress monitor
	 *
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
	 * @param monitor
	 *            a {@link org.eclipse.jgit.lib.ProgressMonitor}
	 * @return {@code this}
	 */
	public PushCommand setProgressMonitor(ProgressMonitor monitor) {
		checkCallable();
		if (monitor == null) {
			monitor = NullProgressMonitor.INSTANCE;
		}
		this.monitor = monitor;
		return this;
	}

	/**
	 * Get the <code>RefLeaseSpec</code>s.
	 *
	 * @return the <code>RefLeaseSpec</code>s
	 * @since 4.7
	 */
	public List<RefLeaseSpec> getRefLeaseSpecs() {
		return new ArrayList<>(refLeaseSpecs.values());
	}

	/**
	 * The ref lease specs to be used in the push operation, for a
	 * force-with-lease push operation.
	 *
	 * @param specs
	 *            a {@link org.eclipse.jgit.transport.RefLeaseSpec} object.
	 * @return {@code this}
	 * @since 4.7
	 */
	public PushCommand setRefLeaseSpecs(RefLeaseSpec... specs) {
		return setRefLeaseSpecs(Arrays.asList(specs));
	}

	/**
	 * The ref lease specs to be used in the push operation, for a
	 * force-with-lease push operation.
	 *
	 * @param specs
	 *            list of {@code RefLeaseSpec}s
	 * @return {@code this}
	 * @since 4.7
	 */
	public PushCommand setRefLeaseSpecs(List<RefLeaseSpec> specs) {
		checkCallable();
		this.refLeaseSpecs.clear();
		for (RefLeaseSpec spec : specs) {
			refLeaseSpecs.put(spec.getRef(), spec);
		}
		return this;
	}

	/**
	 * Get {@code RefSpec}s.
	 *
	 * @return the ref specs
	 */
	public List<RefSpec> getRefSpecs() {
		return refSpecs;
	}

	/**
	 * The ref specs to be used in the push operation
	 *
	 * @param specs a {@link org.eclipse.jgit.transport.RefSpec} object.
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
	 *            list of {@link org.eclipse.jgit.transport.RefSpec}s
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
				src = repo.findRef(nameOrSpec);
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
	 * Whether to run the push operation as a dry run
	 *
	 * @return the dry run preference for the push operation
	 */
	public boolean isDryRun() {
		return dryRun;
	}

	/**
	 * Sets whether the push operation should be a dry run
	 *
	 * @param dryRun a boolean.
	 * @return {@code this}
	 */
	public PushCommand setDryRun(boolean dryRun) {
		checkCallable();
		this.dryRun = dryRun;
		return this;
	}

	/**
	 * Get the thin-pack preference
	 *
	 * @return the thin-pack preference for push operation
	 */
	public boolean isThin() {
		return thin;
	}

	/**
	 * Set the thin-pack preference for push operation.
	 *
	 * Default setting is Transport.DEFAULT_PUSH_THIN
	 *
	 * @param thin
	 *            the thin-pack preference value
	 * @return {@code this}
	 */
	public PushCommand setThin(boolean thin) {
		checkCallable();
		this.thin = thin;
		return this;
	}

	/**
	 * Whether this push should be executed atomically (all references updated,
	 * or none)
	 *
	 * @return true if all-or-nothing behavior is requested.
	 * @since 4.2
	 */
	public boolean isAtomic() {
		return atomic;
	}

	/**
	 * Requests atomic push (all references updated, or no updates).
	 *
	 * Default setting is false.
	 *
	 * @param atomic
	 *            whether to run the push atomically
	 * @return {@code this}
	 * @since 4.2
	 */
	public PushCommand setAtomic(boolean atomic) {
		checkCallable();
		this.atomic = atomic;
		return this;
	}

	/**
	 * Whether to push forcefully
	 *
	 * @return the force preference for push operation
	 */
	public boolean isForce() {
		return force;
	}

	/**
	 * Sets the force preference for push operation.
	 *
	 * @param force
	 *            whether to push forcefully
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
	 *            an {@link java.io.OutputStream}
	 * @return {@code this}
	 * @since 3.0
	 */
	public PushCommand setOutputStream(OutputStream out) {
		this.out = out;
		return this;
	}

	/**
	 * Get push options
	 *
	 * @return the option strings associated with the push operation
	 * @since 4.5
	 */
	public List<String> getPushOptions() {
		return pushOptions;
	}

	/**
	 * Set the option strings associated with the push operation.
	 *
	 * @param pushOptions
	 *            a {@link java.util.List} of push option strings
	 * @return {@code this}
	 * @since 4.5
	 */
	public PushCommand setPushOptions(List<String> pushOptions) {
		this.pushOptions = pushOptions;
		return this;
	}
}

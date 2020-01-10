/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op.commands;

import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkInstanceOf;
import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.GitImpl;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;

public class Push {

	private final GitImpl git;
	private final CredentialsProvider credentialsProvider;
	private final Map.Entry<String, String> remote;
	private final boolean force;
	private final Collection<RefSpec> refSpecs;

	public Push(final Git git, final CredentialsProvider credentialsProvider, final Map.Entry<String, String> remote,
			final boolean force, final Collection<RefSpec> refSpecs) {
		this.git = checkInstanceOf("git", git, GitImpl.class);
		this.credentialsProvider = credentialsProvider;
		this.remote = checkNotNull("remote", remote);
		this.force = force;
		this.refSpecs = refSpecs;
	}

	public void execute() throws InvalidRemoteException {
		try {
			final List<RefSpec> specs = new UpdateRemoteConfig(git, remote, refSpecs).execute();
			git._push().setCredentialsProvider(credentialsProvider).setRefSpecs(specs).setRemote(remote.getKey())
					.setForce(force).setPushAll().call();
		} catch (final InvalidRemoteException e) {
			throw e;
		} catch (final Exception ex) {
			throw new RuntimeException(ex);
		}
	}
}

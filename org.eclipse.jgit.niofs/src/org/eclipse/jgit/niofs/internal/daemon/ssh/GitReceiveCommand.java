/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.daemon.ssh;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.niofs.internal.JGitFileSystemProvider;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;

public class GitReceiveCommand extends BaseGitCommand {

	private final ReceivePackFactory<BaseGitCommand> receivePackFactory;

	public GitReceiveCommand(final String command,
			final JGitFileSystemProvider.RepositoryResolverImpl<BaseGitCommand> repositoryResolver,
			final ReceivePackFactory<BaseGitCommand> receivePackFactory, final ExecutorService executorService) {
		super(command, repositoryResolver, executorService);
		this.receivePackFactory = receivePackFactory;
	}

	@Override
	protected String getCommandName() {
		return "git-receive-pack";
	}

	@Override
	protected void execute(final Repository repository, final InputStream in, final OutputStream out,
			final OutputStream err) {
		try {
			final ReceivePack rp = receivePackFactory.create(this, repository);
			rp.receive(in, out, err);
			rp.setPostReceiveHook((rp1, commands) -> {
				new Git(repository).gc();
			});
		} catch (final Exception ignored) {
		}
	}
}

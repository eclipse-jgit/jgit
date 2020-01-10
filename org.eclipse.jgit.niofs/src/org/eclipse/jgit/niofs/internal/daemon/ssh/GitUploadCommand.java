/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
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
import java.util.zip.Deflater;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.niofs.internal.JGitFileSystemProvider;
import org.eclipse.jgit.niofs.internal.daemon.filter.HiddenBranchRefFilter;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.transport.RefFilter;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;

public class GitUploadCommand extends BaseGitCommand {

	private UploadPackFactory<BaseGitCommand> uploadPackFactory;

	public GitUploadCommand(final String command,
			final JGitFileSystemProvider.RepositoryResolverImpl<BaseGitCommand> repositoryResolver,
			final UploadPackFactory uploadPackFactory, final ExecutorService executorService) {
		super(command, repositoryResolver, executorService);
		this.uploadPackFactory = uploadPackFactory;
	}

	@Override
	protected String getCommandName() {
		return "git-upload-pack";
	}

	@Override
	protected void execute(final Repository repository, final InputStream in, final OutputStream out,
			final OutputStream err) {
		try {
			final UploadPack up = uploadPackFactory.create(this, repository);

			final PackConfig config = new PackConfig(repository);
			config.setCompressionLevel(Deflater.BEST_COMPRESSION);
			up.setPackConfig(config);

			if (up.getRefFilter() == RefFilter.DEFAULT) {
				up.setRefFilter(new HiddenBranchRefFilter());
			}

			up.upload(in, out, err);
		} catch (final Exception ignored) {
		}
	}
}

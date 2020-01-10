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

import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotEmpty;
import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.internal.ketch.KetchLeaderCache;
import org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.exceptions.GitException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Fork {

	private static final String DOT_GIT_EXT = ".git";
	private final KetchLeaderCache leaders;
	private Logger logger = LoggerFactory.getLogger(Fork.class);

	private File parentFolder;
	private final String source;
	private final String target;
	private final List<String> branches;
	private CredentialsProvider credentialsProvider;
	private final File hookDir;
	private final boolean sslVerify;

	public Fork(final File parentFolder, final String source, final String target, final List<String> branches,
			final CredentialsProvider credentialsProvider, final KetchLeaderCache leaders, final File hookDir) {

		this(parentFolder, source, target, branches, credentialsProvider, leaders, hookDir,
				JGitFileSystemProviderConfiguration.DEFAULT_GIT_HTTP_SSL_VERIFY);
	}

	public Fork(final File parentFolder, final String source, final String target, final List<String> branches,
			final CredentialsProvider credentialsProvider, final KetchLeaderCache leaders, final File hookDir,
			final boolean sslVerify) {
		this.parentFolder = checkNotNull("parentFolder", parentFolder);
		this.source = checkNotEmpty("source", source);
		this.target = checkNotEmpty("target", target);
		this.branches = branches;
		this.credentialsProvider = checkNotNull("credentialsProvider", credentialsProvider);
		this.leaders = leaders;

		this.hookDir = hookDir;

		this.sslVerify = sslVerify;
	}

	public Git execute() throws IOException {

		if (logger.isDebugEnabled()) {
			logger.debug("Forking repository <{}> to <{}>", source, target);
		}

		final File origin = new File(parentFolder, source + DOT_GIT_EXT);
		final File destination = new File(parentFolder, target + DOT_GIT_EXT);

		if (destination.exists()) {
			String message = String.format("Cannot fork because destination repository <%s> already exists", target);
			logger.error(message);
			throw new GitException(message);
		}

		return Git.clone(destination, origin.toPath().toUri().toString(), false, branches, credentialsProvider, leaders,
				hookDir, sslVerify);
	}
}

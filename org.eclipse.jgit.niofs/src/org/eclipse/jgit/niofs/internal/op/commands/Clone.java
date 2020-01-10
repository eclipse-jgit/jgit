/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op.commands;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jgit.internal.ketch.KetchLeaderCache;
import org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotEmpty;
import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotNull;

public class Clone {

	public static final String REFS_MIRRORED = "+refs/heads/*:refs/remotes/origin/*";
	private final File repoDir;
	private final String origin;
	private final List<String> branches;
	private final CredentialsProvider credentialsProvider;
	private final boolean isMirror;
	private final KetchLeaderCache leaders;
	private final File hookDir;
	private final boolean sslVerify;

	private Logger logger = LoggerFactory.getLogger(Clone.class);

	public Clone(final File directory, final String origin, final boolean isMirror, final List<String> branches,
			final CredentialsProvider credentialsProvider, final KetchLeaderCache leaders, final File hookDir) {
		this(directory, origin, isMirror, branches, credentialsProvider, leaders, hookDir,
				JGitFileSystemProviderConfiguration.DEFAULT_GIT_HTTP_SSL_VERIFY);
	}

	public Clone(final File directory, final String origin, final boolean isMirror, final List<String> branches,
			final CredentialsProvider credentialsProvider, final KetchLeaderCache leaders, final File hookDir,
			final boolean sslVerify) {
		this.repoDir = checkNotNull("directory", directory);
		this.origin = checkNotEmpty("origin", origin);
		this.isMirror = isMirror;
		this.branches = branches;
		this.credentialsProvider = credentialsProvider;
		this.leaders = leaders;
		this.hookDir = hookDir;
		this.sslVerify = sslVerify;
	}

	public Optional<Git> execute() throws IOException {

		if (repoDir.exists()) {
			String message = String.format("Cannot clone because destination repository <%s> already exists",
					repoDir.getAbsolutePath());
			logger.error(message);
			throw new CloneException(message);
		}

		final Git git = Git.createRepository(repoDir, hookDir, sslVerify);

		if (git != null) {
			try {

				final Collection<RefSpec> refSpecList;
				if (isMirror) {
					refSpecList = singletonList(new RefSpec(REFS_MIRRORED));
				} else {
					refSpecList = emptyList();
				}
				final Map.Entry<String, String> remote = new AbstractMap.SimpleEntry<>("origin", origin);
				git.fetch(credentialsProvider, remote, refSpecList);

				git.syncRemote(remote);

				if (git.isKetchEnabled()) {
					git.convertRefTree();
					git.updateLeaders(leaders);
				}

				git.setHeadAsInitialized();

				BranchUtil.deleteUnfilteredBranches(git.getRepository(), branches);

				return Optional.of(git);
			} catch (Exception e) {
				String message = String.format("Error cloning origin <%s>.", origin);
				logger.error(message);
				cleanupDir(git.getRepository().getDirectory());
				throw new CloneException(message, e);
			}
		}

		return Optional.empty();
	}

	private void cleanupDir(final File gitDir) throws IOException {

		try {
			if (System.getProperty("os.name").toLowerCase().contains("windows")) {
				// this operation forces a cache clean freeing any lock -> windows only issue!
				new WindowCacheConfig().install();
			}
			FileUtils.delete(gitDir, FileUtils.RECURSIVE | FileUtils.RETRY);
		} catch (java.io.IOException e) {
			throw new IOException("Failed to remove the git repository.", e);
		}
	}

	public static class CloneException extends RuntimeException {

		public CloneException(final String message) {
			super(message);
		}

		public CloneException(final String message, final Throwable t) {
			super(message, t);
		}
	}
}

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

import static java.nio.file.Files.walkFileTree;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.EnumSet;
import java.util.Optional;

import org.eclipse.jgit.internal.ketch.KetchLeaderCache;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.GitImpl;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

public class CreateRepository {

	private final File repoDir;
	private final File hookDir;
	private final KetchLeaderCache leaders;
	private final boolean sslVerify;

	public CreateRepository(final File repoDir) {
		this(repoDir, null, null, JGitFileSystemProviderConfiguration.DEFAULT_GIT_HTTP_SSL_VERIFY);
	}

	public CreateRepository(final File repoDir, final boolean sslVerify) {
		this(repoDir, null, null, sslVerify);
	}

	public CreateRepository(final File repoDir, final File hookDir) {
		this(repoDir, hookDir, null, JGitFileSystemProviderConfiguration.DEFAULT_GIT_HTTP_SSL_VERIFY);
	}

	public CreateRepository(final File repoDir, final File hookDir, final boolean sslVerify) {
		this(repoDir, hookDir, null, sslVerify);
	}

	public CreateRepository(final File repoDir, final File hookDir, final KetchLeaderCache leaders) {
		this(repoDir, hookDir, leaders, JGitFileSystemProviderConfiguration.DEFAULT_GIT_HTTP_SSL_VERIFY);
	}

	public CreateRepository(final File repoDir, final File hookDir, final KetchLeaderCache leaders,
			final boolean sslVerify) {
		this.repoDir = repoDir;
		this.hookDir = hookDir;
		this.leaders = leaders;
		this.sslVerify = sslVerify;
	}

	public Optional<Git> execute() throws IOException {
		try {
			boolean newRepository = !repoDir.exists();
			final org.eclipse.jgit.api.Git _git = org.eclipse.jgit.api.Git.init().setBare(true).setDirectory(repoDir)
					.call();

			if (leaders != null) {
				new WriteConfiguration(_git.getRepository(), cfg -> {
					cfg.setInt("core", null, "repositoryformatversion", 1);
					cfg.setString("extensions", null, "refsStorage", "reftree");
				}).execute();
			}

			final Repository repo = new FileRepositoryBuilder().setGitDir(repoDir).build();

			final org.eclipse.jgit.api.Git git = new org.eclipse.jgit.api.Git(repo);

			setupSSLVerify(repo);

			if (setupGitHooks(newRepository)) {
				final File repoHookDir = new File(repoDir, "hooks");

				try {
					copyDirectory(hookDir.toPath(), repoHookDir.toPath());
				} catch (final Exception ex) {
					throw new RuntimeException(ex);
				}

				for (final File file : repoHookDir.listFiles()) {
					if (file != null && file.isFile()) {
						file.setExecutable(true);
					}
				}
			}

			return Optional.of(new GitImpl(git, leaders));
		} catch (final Exception ex) {
			throw new IOException(ex);
		}
	}

	/**
	 * Copies a directory taking consideration for file attributes and permissions.
	 *
	 * @param source source directory
	 * @param target target directory
	 * @throws IOException if something fails
	 */
	private void copyDirectory(final Path source, final Path target) throws IOException {
		walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new FileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes sourceBasic) throws IOException {
				Path targetDir = Files.createDirectories(target.resolve(source.relativize(dir)));
				AclFileAttributeView acl = Files.getFileAttributeView(dir, AclFileAttributeView.class);
				if (acl != null) {
					Files.getFileAttributeView(targetDir, AclFileAttributeView.class).setAcl(acl.getAcl());
				}
				DosFileAttributeView dosAttrs = Files.getFileAttributeView(dir, DosFileAttributeView.class);
				if (dosAttrs != null) {
					DosFileAttributes sourceDosAttrs = dosAttrs.readAttributes();
					DosFileAttributeView targetDosAttrs = Files.getFileAttributeView(targetDir,
							DosFileAttributeView.class);
					targetDosAttrs.setArchive(sourceDosAttrs.isArchive());
					targetDosAttrs.setHidden(sourceDosAttrs.isHidden());
					targetDosAttrs.setReadOnly(sourceDosAttrs.isReadOnly());
					targetDosAttrs.setSystem(sourceDosAttrs.isSystem());
				}
				FileOwnerAttributeView ownerAttrs = Files.getFileAttributeView(dir, FileOwnerAttributeView.class);
				if (ownerAttrs != null) {
					FileOwnerAttributeView targetOwner = Files.getFileAttributeView(targetDir,
							FileOwnerAttributeView.class);
					targetOwner.setOwner(ownerAttrs.getOwner());
				}
				PosixFileAttributeView posixAttrs = Files.getFileAttributeView(dir, PosixFileAttributeView.class);
				if (posixAttrs != null) {
					PosixFileAttributes sourcePosix = posixAttrs.readAttributes();
					PosixFileAttributeView targetPosix = Files.getFileAttributeView(targetDir,
							PosixFileAttributeView.class);
					targetPosix.setPermissions(sourcePosix.permissions());
					targetPosix.setGroup(sourcePosix.group());
				}
				UserDefinedFileAttributeView userAttrs = Files.getFileAttributeView(dir,
						UserDefinedFileAttributeView.class);
				if (userAttrs != null) {
					UserDefinedFileAttributeView targetUser = Files.getFileAttributeView(targetDir,
							UserDefinedFileAttributeView.class);
					for (String key : userAttrs.list()) {
						ByteBuffer buffer = ByteBuffer.allocate(userAttrs.size(key));
						userAttrs.read(key, buffer);
						buffer.flip();
						targetUser.write(key, buffer);
					}
				}
				// Must be done last, otherwise last-modified time may be
				// wrong
				BasicFileAttributeView targetBasic = Files.getFileAttributeView(targetDir,
						BasicFileAttributeView.class);
				targetBasic.setTimes(sourceBasic.lastModifiedTime(), sourceBasic.lastAccessTime(),
						sourceBasic.creationTime());
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
				throw e;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
				if (e != null) {
					throw e;
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private void setupSSLVerify(Repository repo) throws java.io.IOException {
		StoredConfig config = repo.getConfig();
		config.setBoolean("http", null, "sslVerify", sslVerify);
		config.save();
	}

	private boolean setupGitHooks(boolean newRepository) {
		return newRepository && hookDir != null;
	}
}

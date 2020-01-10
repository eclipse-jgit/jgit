/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.niofs.internal.op.model.PathInfo;
import org.eclipse.jgit.niofs.internal.op.model.PathType;

/**
 *
 */
public class JGitBasicAttributeView extends AbstractBasicFileAttributeView<JGitPathImpl> {

	private BasicFileAttributes attrs = null;

	public JGitBasicAttributeView(final JGitPathImpl path) {
		super(path);
	}

	@Override
	public BasicFileAttributes readAttributes() throws IOException {
		if (attrs == null) {
			attrs = buildAttrs((JGitFileSystem) path.getFileSystem(), path.getRefTree(), path.getPath());
		}
		return attrs;
	}

	@Override
	public Class<? extends BasicFileAttributeView>[] viewTypes() {
		return new Class[] { BasicFileAttributeView.class, JGitBasicAttributeView.class };
	}

	private BasicFileAttributes buildAttrs(final JGitFileSystem fs, final String branchName, final String path)
			throws NoSuchFileException {
		final PathInfo pathInfo = fs.getGit().getPathInfo(branchName, path);

		if (pathInfo == null || pathInfo.getPathType().equals(PathType.NOT_FOUND)) {
			throw new NoSuchFileException(path);
		}

		final Ref ref = fs.getGit().getRef(branchName);

		return new BasicFileAttributes() {

			private FileTime lastModifiedDate = null;
			private FileTime creationDate = null;

			@Override
			public FileTime lastModifiedTime() {
				if (lastModifiedDate == null) {
					try {
						lastModifiedDate = FileTime
								.fromMillis(fs.getGit().getLastCommit(ref).getCommitterIdent().getWhen().getTime());
					} catch (final Exception e) {
						lastModifiedDate = FileTime.fromMillis(0);
					}
				}
				return lastModifiedDate;
			}

			@Override
			public FileTime lastAccessTime() {
				return lastModifiedTime();
			}

			@Override
			public FileTime creationTime() {
				if (creationDate == null) {
					try {
						creationDate = FileTime
								.fromMillis(fs.getGit().getFirstCommit(ref).getCommitterIdent().getWhen().getTime());
					} catch (final Exception e) {
						creationDate = FileTime.fromMillis(0);
					}
				}
				return creationDate;
			}

			@Override
			public boolean isRegularFile() {
				return pathInfo.getPathType().equals(PathType.FILE);
			}

			@Override
			public boolean isDirectory() {
				return pathInfo.getPathType().equals(PathType.DIRECTORY);
			}

			@Override
			public boolean isSymbolicLink() {
				return false;
			}

			@Override
			public boolean isOther() {
				return false;
			}

			@Override
			public long size() {
				return pathInfo.getSize();
			}

			@Override
			public Object fileKey() {
				return pathInfo.getObjectId() == null ? null : pathInfo.getObjectId().toString();
			}
		};
	}
}

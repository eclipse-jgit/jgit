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
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.niofs.fs.attribute.VersionAttributeView;
import org.eclipse.jgit.niofs.fs.attribute.VersionAttributes;
import org.eclipse.jgit.niofs.fs.attribute.VersionHistory;
import org.eclipse.jgit.niofs.fs.attribute.VersionRecord;
import org.eclipse.jgit.niofs.internal.op.model.CommitHistory;
import org.eclipse.jgit.niofs.internal.op.model.PathInfo;
import org.eclipse.jgit.niofs.internal.op.model.PathType;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 *
 */
public class JGitVersionAttributeViewImpl extends VersionAttributeViewImpl<JGitPathImpl> {

	private VersionAttributes attrs = null;

	public JGitVersionAttributeViewImpl(final JGitPathImpl path) {
		super(path);
	}

	@Override
	public VersionAttributes readAttributes() throws IOException {
		if (attrs == null) {
			attrs = buildAttrs((JGitFileSystem) path.getFileSystem(), path.getRefTree(), path.getPath());
		}
		return attrs;
	}

	@Override
	public Class<? extends BasicFileAttributeView>[] viewTypes() {
		return new Class[] { VersionAttributeView.class, VersionAttributeViewImpl.class,
				JGitVersionAttributeViewImpl.class };
	}

	private VersionAttributes buildAttrs(final JGitFileSystem fs, final String branchName, final String path)
			throws NoSuchFileException {
		final PathInfo pathInfo = fs.getGit().getPathInfo(branchName, path);

		if (pathInfo == null || pathInfo.getPathType().equals(PathType.NOT_FOUND)) {
			throw new NoSuchFileException(path);
		}

		final Ref refId = fs.getGit().getRef(branchName);
		final List<VersionRecord> records = new ArrayList<>();

		if (refId != null) {
			try {
				final CommitHistory history = fs.getGit().listCommits(refId, pathInfo.getPath());
				for (final RevCommit commit : history.getCommits()) {
					final String recordPath = history.trackedFileNameChangeFor(commit.getId());
					records.add(new VersionRecord() {
						@Override
						public String id() {
							return commit.name();
						}

						@Override
						public String author() {
							return commit.getAuthorIdent().getName();
						}

						@Override
						public String email() {
							return commit.getAuthorIdent().getEmailAddress();
						}

						@Override
						public String comment() {
							return commit.getFullMessage();
						}

						@Override
						public Date date() {
							return commit.getAuthorIdent().getWhen();
						}

						@Override
						public String uri() {
							return fs.getPath(commit.name(), recordPath).toUri().toString();
						}
					});
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		Collections.reverse(records);

		return new VersionAttributes() {
			@Override
			public VersionHistory history() {
				return () -> records;
			}

			@Override
			public FileTime lastModifiedTime() {
				if (records.size() > 0) {
					return FileTime.fromMillis(records.get(records.size() - 1).date().getTime());
				}
				return null;
			}

			@Override
			public FileTime lastAccessTime() {
				return lastModifiedTime();
			}

			@Override
			public FileTime creationTime() {
				if (records.size() > 0) {
					return FileTime.fromMillis(records.get(0).date().getTime());
				}
				return null;
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

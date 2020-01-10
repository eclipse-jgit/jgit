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

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.List;

import org.eclipse.jgit.niofs.fs.options.CommentedOption;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.model.CommitInfo;
import org.eclipse.jgit.niofs.internal.security.User;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.UploadPack;

public abstract class JGitFileSystem extends FileSystem
		implements FileSystemStateAware, FileSystemId, LockableFileSystem, Disposable {

	abstract public Git getGit();

	abstract CredentialsProvider getCredential();

	abstract void checkClosed() throws IllegalStateException;

	abstract void publishEvents(Path watchable, List<WatchEvent<?>> elist);

	abstract boolean isOnBatch();

	abstract void setState(String state);

	abstract CommitInfo buildCommitInfo(String defaultMessage, CommentedOption op);

	abstract void setBatchCommitInfo(String defaultMessage, CommentedOption op);

	abstract void setHadCommitOnBatchState(Path path, boolean hadCommitOnBatchState);

	abstract void setHadCommitOnBatchState(boolean value);

	abstract boolean isHadCommitOnBatchState(Path path);

	abstract void setBatchCommitInfo(CommitInfo batchCommitInfo);

	abstract CommitInfo getBatchCommitInfo();

	abstract int incrementAndGetCommitCount();

	abstract void resetCommitCount();

	abstract int getNumberOfCommitsSinceLastGC();

	abstract void addPostponedWatchEvents(List<WatchEvent<?>> postponedWatchEvents);

	abstract List<WatchEvent<?>> getPostponedWatchEvents();

	abstract void clearPostponedWatchEvents();

	abstract boolean hasPostponedEvents();

	abstract public boolean hasBeenInUse();

	abstract void notifyExternalUpdate();

	abstract void notifyPostCommit(int exitCode);

	abstract void checkBranchAccess(ReceiveCommand command, User user);

	abstract void filterBranchAccess(UploadPack uploadPack, User user);

	public abstract String getName();
}

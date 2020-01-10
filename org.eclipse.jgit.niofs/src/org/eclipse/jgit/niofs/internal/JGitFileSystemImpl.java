/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchEvent;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.PatternSyntaxException;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.niofs.fs.FileSystemState;
import org.eclipse.jgit.niofs.fs.options.CommentedOption;
import org.eclipse.jgit.niofs.internal.hook.FileSystemHookExecutionContext;
import org.eclipse.jgit.niofs.internal.hook.FileSystemHooks;
import org.eclipse.jgit.niofs.internal.hook.FileSystemHooksConstants;
import org.eclipse.jgit.niofs.internal.hook.JGitFSHooks;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.model.CommitInfo;
import org.eclipse.jgit.niofs.internal.security.User;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.UploadPack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;
import static org.eclipse.jgit.lib.Repository.shortenRefName;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_NIO_DIR;
import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotEmpty;
import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotNull;

public class JGitFileSystemImpl extends JGitFileSystem {

	private static final Logger LOGGER = LoggerFactory.getLogger(JGitFileSystemImpl.class);
	private static final Set<String> SUPPORTED_ATTR_VIEWS = unmodifiableSet(new HashSet<>(asList("basic", "version")));
	private final JGitFileSystemProvider provider;
	private final Git git;
	private final String toStringContent;
	private boolean isClosed = false;
	private final FileStore fileStore;
	private final String name;
	private final CredentialsProvider credential;
	private final Map<FileSystemHooks, ?> fsHooks;
	private final AtomicInteger numberOfCommitsSinceLastGC = new AtomicInteger(0);
	private FileSystemState state = FileSystemState.NORMAL;
	private CommitInfo batchCommitInfo = null;
	private Map<Path, Boolean> hadCommitOnBatchState = new ConcurrentHashMap<>();
	private JGitFileSystemLock lock;
	private JGitFileSystemsEventsManager fsEventsManager;

	private List<WatchEvent<?>> postponedWatchEvents = Collections.synchronizedList(new ArrayList<>());

	public JGitFileSystemImpl(final JGitFileSystemProvider provider, final Map<String, String> fullHostNames,
			final Git git, final JGitFileSystemLock lock, final String name, final CredentialsProvider credential,
			JGitFileSystemsEventsManager fsEventsManager, Map<FileSystemHooks, ?> fsHooks) {
		this.fsEventsManager = fsEventsManager;
		this.provider = checkNotNull("provider", provider);
		this.git = checkNotNull("git", git);
		this.name = checkNotEmpty("name", name);

		this.lock = checkNotNull("lock", lock);
		this.credential = checkNotNull("credential", credential);
		this.fsHooks = fsHooks;
		this.fileStore = new JGitFileStore(this.git.getRepository());
		if (fullHostNames != null && !fullHostNames.isEmpty()) {
			final StringBuilder sb = new StringBuilder();
			final Iterator<Map.Entry<String, String>> iterator = fullHostNames.entrySet().iterator();
			while (iterator.hasNext()) {
				final Map.Entry<String, String> entry = iterator.next();
				sb.append(entry.getKey()).append("://").append(entry.getValue()).append("/").append(name);
				if (iterator.hasNext()) {
					sb.append("\n");
				}
			}
			toStringContent = sb.toString();
		} else {
			toStringContent = "git://" + name;
		}
	}

	@Override
	public String id() {
		return name;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Git getGit() {
		return git;
	}

	@Override
	public CredentialsProvider getCredential() {
		return credential;
	}

	@Override
	public FileSystemProvider provider() {
		return provider;
	}

	@Override
	public boolean isOpen() {
		return !isClosed;
	}

	@Override
	public boolean isReadOnly() {
		return false;
	}

	@Override
	public String getSeparator() {
		return "/";
	}

	@Override
	public Iterable<Path> getRootDirectories() {
		checkClosed();
		return () -> new Iterator<Path>() {

			Iterator<Ref> branches = null;

			@Override
			public boolean hasNext() {
				if (branches == null) {
					init();
				}
				return branches.hasNext();
			}

			private void init() {
				branches = git.listRefs().iterator();
			}

			@Override
			public Path next() {

				if (branches == null) {
					init();
				}
				try {
					return JGitPathImpl.createRoot(JGitFileSystemImpl.this, "/",
							shortenRefName(branches.next().getName()) + "@" + name, false);
				} catch (NoSuchElementException e) {
					throw new IllegalStateException("The gitnio directory is in an invalid state. "
							+ "If you are an IntelliJ IDEA user, " + "there is a known bug which requires specifying "
							+ "a custom directory for your git repository. "
							+ "You can specify a custom directory using '-D" + GIT_NIO_DIR + "=/tmp/dir'. "
							+ "For more details please see https://issues.jboss.org/browse/UF-275.", e);
				}
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	@Override
	public Iterable<FileStore> getFileStores() {
		checkClosed();
		return () -> new Iterator<FileStore>() {

			private int i = 0;

			@Override
			public boolean hasNext() {
				return i < 1;
			}

			@Override
			public FileStore next() {
				if (i < 1) {
					i++;
					return fileStore;
				} else {
					throw new NoSuchElementException();
				}
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		checkClosed();
		return SUPPORTED_ATTR_VIEWS;
	}

	@Override
	public Path getPath(final String first, final String... more) throws InvalidPathException {
		checkClosed();
		if (first == null || first.trim().isEmpty()) {
			return new JGitFSPath(this);
		}

		if (more == null || more.length == 0) {
			return JGitPathImpl.create(this, first, JGitPathImpl.DEFAULT_REF_TREE + "@" + name, false);
		}

		final StringBuilder sb = new StringBuilder();
		for (final String segment : more) {
			if (segment.length() > 0) {
				if (sb.length() > 0) {
					sb.append(getSeparator());
				}
				sb.append(segment);
			}
		}
		return JGitPathImpl.create(this, sb.toString(), first + "@" + name, false);
	}

	@Override
	public PathMatcher getPathMatcher(final String syntaxAndPattern)
			throws IllegalArgumentException, PatternSyntaxException, UnsupportedOperationException {
		checkClosed();
		checkNotEmpty("syntaxAndPattern", syntaxAndPattern);
		throw new UnsupportedOperationException();
	}

	@Override
	public UserPrincipalLookupService getUserPrincipalLookupService() throws UnsupportedOperationException {
		checkClosed();
		throw new UnsupportedOperationException();
	}

	@Override
	public WatchService newWatchService() throws UnsupportedOperationException, IOException {
		checkClosed();
		return fsEventsManager.newWatchService(name);
	}

	@Override
	public void close() throws IOException {
		if (isClosed) {
			return;
		}
		git.getRepository().close();
		isClosed = true;
		try {
			fsEventsManager.close(name);
		} catch (final Exception ex) {
			LOGGER.error("Error during close of WatchServices [" + toString() + "]", ex);
		} finally {
			provider.onCloseFileSystem(this);
		}
	}

	@Override
	public void checkClosed() throws IllegalStateException {
		if (isClosed) {
			throw new IllegalStateException("FileSystem is closed.");
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			if (o != null && o instanceof JGitFileSystemProxy) {
				o = ((JGitFileSystemProxy) o).getRealJGitFileSystem();
			} else {
				return false;
			}
		}

		JGitFileSystemImpl that = (JGitFileSystemImpl) o;

		if (!name.equals(that.name)) {
			return false;
		}

		return true;
	}

	@Override
	public String toString() {
		return toStringContent;
	}

	@Override
	public int hashCode() {
		int result = name.hashCode();
		return result;
	}

	@Override
	public void publishEvents(final Path watchableRoot, final List<WatchEvent<?>> elist) {
		fsEventsManager.publishEvents(name, watchableRoot, elist);
	}

	@Override
	public void dispose() throws IOException {
		if (!isClosed) {
			close();
		}
		provider.onDisposeFileSystem(this);
	}

	@Override
	public boolean isOnBatch() {
		return state.equals(FileSystemState.BATCH);
	}

	@Override
	public void setState(String state) {
		try {
			this.state = FileSystemState.valueOf(state);
		} catch (final Exception ex) {
			this.state = FileSystemState.NORMAL;
		}
	}

	@Override
	public CommitInfo buildCommitInfo(final String defaultMessage, final CommentedOption op) {
		String sessionId = null;
		String name = null;
		String email = null;
		String message = defaultMessage;
		TimeZone timeZone = null;
		Date when = null;

		if (op != null) {
			sessionId = op.getSessionId();
			name = op.getName();
			email = op.getEmail();
			if (op.getMessage() != null && !op.getMessage().trim().isEmpty()) {
				message = op.getMessage();
			}
			timeZone = op.getTimeZone();
			when = op.getWhen();
		}

		return new CommitInfo(sessionId, name, email, message, timeZone, when);
	}

	@Override
	public void setBatchCommitInfo(final String defaultMessage, final CommentedOption op) {
		this.batchCommitInfo = buildCommitInfo(defaultMessage, op);
	}

	@Override
	public void setHadCommitOnBatchState(final Path path, final boolean hadCommitOnBatchState) {
		final Path root = checkNotNull("path", path).getRoot();
		this.hadCommitOnBatchState.put(root.getRoot(), hadCommitOnBatchState);
	}

	@Override
	public void setHadCommitOnBatchState(final boolean value) {
		for (Map.Entry<Path, Boolean> entry : hadCommitOnBatchState.entrySet()) {
			entry.setValue(value);
		}
	}

	@Override
	public boolean isHadCommitOnBatchState(final Path path) {
		final Path root = checkNotNull("path", path).getRoot();
		return hadCommitOnBatchState.containsKey(root) ? hadCommitOnBatchState.get(root) : false;
	}

	@Override
	public void setBatchCommitInfo(CommitInfo batchCommitInfo) {
		this.batchCommitInfo = batchCommitInfo;
	}

	@Override
	public CommitInfo getBatchCommitInfo() {
		return batchCommitInfo;
	}

	@Override
	public int incrementAndGetCommitCount() {
		return numberOfCommitsSinceLastGC.incrementAndGet();
	}

	@Override
	public void resetCommitCount() {
		numberOfCommitsSinceLastGC.set(0);
	}

	@Override
	public int getNumberOfCommitsSinceLastGC() {
		return numberOfCommitsSinceLastGC.get();
	}

	@Override
	public FileSystemState getState() {
		return state;
	}

	@Override
	public void lock() {
		lock.lock();
	}

	@Override
	public void unlock() {
		lock.unlock();
	}

	public JGitFileSystemLock getLock() {
		return lock;
	}

	@Override
	public void addPostponedWatchEvents(List<WatchEvent<?>> postponedWatchEvents) {
		this.postponedWatchEvents.addAll(postponedWatchEvents);
	}

	@Override
	public List<WatchEvent<?>> getPostponedWatchEvents() {
		return postponedWatchEvents;
	}

	@Override
	public void clearPostponedWatchEvents() {
		this.postponedWatchEvents = Collections.synchronizedList(new ArrayList<>());
	}

	@Override
	public boolean hasPostponedEvents() {
		return !getPostponedWatchEvents().isEmpty();
	}

	@Override
	public boolean hasBeenInUse() {
		return lock.hasBeenInUse();
	}

	@Override
	public void notifyExternalUpdate() {
		Object hook = fsHooks.get(FileSystemHooks.ExternalUpdate);
		if (hook != null) {
			JGitFSHooks.executeFSHooks(hook, FileSystemHooks.ExternalUpdate, new FileSystemHookExecutionContext(name));
		}
	}

	@Override
	public void notifyPostCommit(int exitCode) {
		Object hook = fsHooks.get(FileSystemHooks.PostCommit);
		if (hook != null) {
			FileSystemHookExecutionContext ctx = new FileSystemHookExecutionContext(name);
			ctx.addParam(FileSystemHooksConstants.POST_COMMIT_EXIT_CODE, exitCode);

			JGitFSHooks.executeFSHooks(hook, FileSystemHooks.ExternalUpdate, ctx);
		}
	}

	@Override
	public void checkBranchAccess(final ReceiveCommand command, final User user) {
		Object hook = fsHooks.get(FileSystemHooks.BranchAccessCheck);
		if (hook != null) {
			FileSystemHookExecutionContext ctx = new FileSystemHookExecutionContext(name);
			ctx.addParam(FileSystemHooksConstants.RECEIVE_COMMAND, command);
			ctx.addParam(FileSystemHooksConstants.USER, user);

			JGitFSHooks.executeFSHooks(hook, FileSystemHooks.BranchAccessCheck, ctx);
		}
	}

	@Override
	public void filterBranchAccess(final UploadPack uploadPack, final User user) {
		Object hook = fsHooks.get(FileSystemHooks.BranchAccessFilter);
		if (hook != null) {
			FileSystemHookExecutionContext ctx = new FileSystemHookExecutionContext(name);
			ctx.addParam(FileSystemHooksConstants.UPLOAD_PACK, uploadPack);
			ctx.addParam(FileSystemHooksConstants.USER, user);

			JGitFSHooks.executeFSHooks(hook, FileSystemHooks.BranchAccessFilter, ctx);
		}
	}
}

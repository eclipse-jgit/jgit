/*
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Snapshotting write-through cache of a {@link RefDirectory}.
 * <p>
 * This is intended to be short-term write-through snapshot based cache used in
 * a request scope to avoid re-reading packed-refs on each read and to avoid
 * refreshing paths to a loose ref that has already been refreshed.
 * <p>
 * Only use this class when concurrent writes from other requests (not using the
 * same instance of SnapshottingRefDirectory) generally need not be visible to
 * the current request. The exception to this is when such writes would cause
 * writes from this snapshot to fail due to their base ref value being
 * outdated.
 */
class SnapshottingRefDirectory extends RefDirectory {
	final RefDirectory refDb;
	private final Set<File> refreshedLooseRefDirs = ConcurrentHashMap.newKeySet();

	private volatile boolean isValid;

	/**
	 * Create a snapshotting write-through cache of a {@link RefDirectory}.
	 *
	 * @param refDb
	 * 		a reference to the ref database
	 */
	SnapshottingRefDirectory(RefDirectory refDb) {
		super(refDb);
		this.refDb = refDb;
	}

	/**
	 * Lazily initializes and returns a PackedRefList snapshot.
	 * <p>
	 * A newer snapshot will be returned when a ref update is performed using
	 * this {@link SnapshottingRefDirectory}.
	 */
	@Override
	PackedRefList getPackedRefs() throws IOException {
		if (!isValid) {
			synchronized (this) {
				if (!isValid) {
					refreshSnapshot();
				}
			}
		}
		return packedRefs.get();
	}

	@Override
	void refreshPathToLooseRef(Path refPath) {
		for (int i = 1; i < refPath.getNameCount(); i++) {
			File dir = fileFor(refPath.subpath(0, i).toString());
			if (!refreshedLooseRefDirs.contains(dir)) {
				try (InputStream stream = Files.newInputStream(dir.toPath())) {
					// open the dir to refresh attributes (on some NFS clients)
				} catch (IOException e) {
					break; // loose ref may not exist
				} finally {
					refreshedLooseRefDirs.add(dir);
				}
			}
		}
	}

	@Override
	void delete(RefDirectoryUpdate update) throws IOException {
		refreshSnapshot();
		super.delete(update);
	}

	@Override
	public RefDirectoryUpdate newUpdate(String name, boolean detach)
			throws IOException {
		refreshSnapshot();
		return super.newUpdate(name, detach);
	}

	@Override
	public PackedBatchRefUpdate newBatchUpdate() {
		return new SnapshotPackedBatchRefUpdate(this);
	}

	@Override
	public PackedBatchRefUpdate newBatchUpdate(boolean shouldLockLooseRefs) {
		return new SnapshotPackedBatchRefUpdate(this, shouldLockLooseRefs);
	}

	@Override
	RefDirectoryUpdate newTemporaryUpdate() throws IOException {
		refreshSnapshot();
		return super.newTemporaryUpdate();
	}

	@Override
	RefDirectoryUpdate createRefDirectoryUpdate(Ref ref) {
		return new SnapshotRefDirectoryUpdate(this, ref);
	}

	@Override
	RefDirectoryRename createRefDirectoryRename(RefDirectoryUpdate from,
			RefDirectoryUpdate to) {
		return new SnapshotRefDirectoryRename(from, to);
	}

	synchronized void invalidateSnapshot() {
		refreshedLooseRefDirs.clear();
		isValid = false;
	}

	/**
	 * Refresh our snapshot by calling the underlying RefDirectory's
	 * getPackedRefs().
	 * <p>
	 * Update the in-memory copy of the underlying RefDirectory's packed-refs to
	 * avoid the overhead of re-reading packed-refs on each new snapshot as the
	 * packed-refs of the underlying RefDirectory may not get updated if most
	 * threads use this snapshot.
	 *
	 * @throws IOException
	 *             if an IO error occurred
	 */
	private synchronized void refreshSnapshot() throws IOException {
		compareAndSetPackedRefs(packedRefs.get(), refDb.getPackedRefs());
		isValid = true;
	}

	@FunctionalInterface
	private interface SupplierThrowsException<R, E extends Exception> {
		R call() throws E;
	}

	@FunctionalInterface
	private interface FunctionThrowsException<A, R, E extends Exception> {
		R apply(A a) throws E;
	}

	@FunctionalInterface
	private interface TriConsumerThrowsException<A1, A2, A3, E extends Exception> {
		void accept(A1 a1, A2 a2, A3 a3) throws E;
	}

	private static <T> T invalidateSnapshotOnError(
			SupplierThrowsException<T, IOException> f, SnapshottingRefDirectory refDb)
			throws IOException {
		return invalidateSnapshotOnError(a -> f.call(), null, refDb);
	}

	private static <A, R> R invalidateSnapshotOnError(
			FunctionThrowsException<A, R, IOException> f, A a,
			SnapshottingRefDirectory refDb) throws IOException {
		try {
			return f.apply(a);
		} catch (IOException e) {
			refDb.invalidateSnapshot();
			throw e;
		}
	}

	private static <A1, A2, A3> void invalidateSnapshotOnError(
			TriConsumerThrowsException<A1, A2, A3, IOException> f, A1 a1, A2 a2,
			A3 a3, SnapshottingRefDirectory refDb) throws IOException {
		try {
			f.accept(a1, a2, a3);
		} catch (IOException e) {
			refDb.invalidateSnapshot();
			throw e;
		}
	}

	private static class SnapshotRefDirectoryUpdate extends RefDirectoryUpdate {
		SnapshotRefDirectoryUpdate(RefDirectory r, Ref ref) {
			super(r, ref);
		}

		@Override
		public Result forceUpdate() throws IOException {
			return invalidateSnapshotOnError(super::forceUpdate,
					getRefDatabase());
		}

		@Override
		public Result update() throws IOException {
			return invalidateSnapshotOnError(super::update, getRefDatabase());
		}

		@Override
		public Result update(RevWalk walk) throws IOException {
			return invalidateSnapshotOnError(super::update, walk,
					getRefDatabase());
		}

		@Override
		public Result delete() throws IOException {
			return invalidateSnapshotOnError(super::delete, getRefDatabase());
		}

		@Override
		public Result delete(RevWalk walk) throws IOException {
			return invalidateSnapshotOnError(super::delete, walk,
					getRefDatabase());
		}

		@Override
		public Result link(String target) throws IOException {
			return invalidateSnapshotOnError(super::link, target,
					getRefDatabase());
		}

		/**
		 * Invalidate the SnapshottingRefDirectory snapshot after locking the
		 * ref.
		 * <p>
		 * Doing this after locking the ref ensures that the upcoming write is
		 * not based on a cached value.
		 *
		 * @param name
		 *            the name of the reference.
		 */
		@Override
		protected void doAfterLocking(String name) {
			getRefDatabase().invalidateSnapshot();
		}

		@Override
		public SnapshottingRefDirectory getRefDatabase() {
			return (SnapshottingRefDirectory) super.getRefDatabase();
		}
	}

	private static class SnapshotRefDirectoryRename extends RefDirectoryRename {
		SnapshotRefDirectoryRename(RefDirectoryUpdate src,
				RefDirectoryUpdate dst) {
			super(src, dst);
		}

		@Override
		public RefUpdate.Result rename() throws IOException {
			return invalidateSnapshotOnError(super::rename, getRefDirectory());
		}

		@Override
		public SnapshottingRefDirectory getRefDirectory() {
			return (SnapshottingRefDirectory) super.getRefDirectory();
		}
	}

	private static class SnapshotPackedBatchRefUpdate
			extends PackedBatchRefUpdate {
		SnapshotPackedBatchRefUpdate(RefDirectory refDb) {
			super(refDb);
		}

		SnapshotPackedBatchRefUpdate(RefDirectory refDb,
				boolean shouldLockLooseRefs) {
			super(refDb, shouldLockLooseRefs);
		}

		@Override
		public void execute(RevWalk walk, ProgressMonitor monitor,
				List<String> options) throws IOException {
			invalidateSnapshotOnError(super::execute,
					walk, monitor, options, getRefDatabase());
		}

		@Override
		public void execute(RevWalk walk, ProgressMonitor monitor)
				throws IOException {
			invalidateSnapshotOnError((rw, m, a3) -> super.execute(rw, m), walk,
					monitor, null, getRefDatabase());
		}

		@Override
		public SnapshottingRefDirectory getRefDatabase() {
			return (SnapshottingRefDirectory) super.getRefDatabase();
		}
	}
}

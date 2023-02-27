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
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.List;

/**
 * Snapshot of a traditional file system based {@link RefDatabase}.
 * <p>
 * This can be used in a request scope to avoid re-reading packed-refs on each
 * read. A future improvement could also snapshot loose refs.
 */
class RefDirectoryWritableSnapshot extends RefDirectory {
	final RefDirectory refDb;

	private volatile boolean isPackedRefsSnapshot;

	/**
	 * Create a snapshot of file system based {@link RefDatabase}.
	 */
	RefDirectoryWritableSnapshot(RefDirectory refDb) {
		super(refDb);
		this.refDb = refDb;
	}

	/**
	 * Lazily initializes and returns a PackedRefList snapshot.
	 * <p>
	 * A newer snapshot will be returned when a ref update is performed using
	 * this {@link RefDirectoryWritableSnapshot}.
	 */
	@Override
	PackedRefList getPackedRefs() throws IOException {
		if (!isPackedRefsSnapshot) {
			synchronized (this) {
				if (!isPackedRefsSnapshot) {
					refreshSnapshot();
				}
			}
		}
		return packedRefs.get();
	}

	/** {@inheritDoc} */
	@Override
	void delete(RefDirectoryUpdate update) throws IOException {
		refreshSnapshot();
		super.delete(update);
	}

	/** {@inheritDoc} */
	@Override
	public RefDirectoryUpdate newUpdate(String name, boolean detach)
			throws IOException {
		refreshSnapshot();
		return super.newUpdate(name, detach);
	}

	/** {@inheritDoc} */
	@Override
	public PackedBatchRefUpdate newBatchUpdate() {
		return new SnapshotPackedBatchRefUpdate(this);
	}

	/** {@inheritDoc} */
	@Override
	public PackedBatchRefUpdate newBatchUpdate(boolean shouldLockLooseRefs) {
		return new SnapshotPackedBatchRefUpdate(this, shouldLockLooseRefs);
	}

	/** {@inheritDoc} */
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
		isPackedRefsSnapshot = false;
	}

	/**
	 * Refresh our snapshot by calling the outer class' getPackedRefs().
	 * <p>
	 * Update the in-memory copy of the outer class' packed-refs to avoid the
	 * overhead of re-reading packed-refs on each new snapshot as outer class'
	 * packed-refs may not get updated if most threads use a snapshot.
	 */
	private synchronized void refreshSnapshot() throws IOException {
		compareAndSetPackedRefs(packedRefs.get(), refDb.getPackedRefs());
		isPackedRefsSnapshot = true;
	}

	private static class SnapshotRefDirectoryUpdate extends RefDirectoryUpdate {
		SnapshotRefDirectoryUpdate(RefDirectory r, Ref ref) {
			super(r, ref);
		}

		@Override
		public Result forceUpdate() throws IOException {
			try {
				return super.forceUpdate();
			} catch (Exception e) {
				((RefDirectoryWritableSnapshot) getRefDatabase()).invalidateSnapshot();
				throw e;
			}
		}

		@Override
		public Result update() throws IOException {
			try {
				return super.update();
			} catch (Exception e) {
				((RefDirectoryWritableSnapshot) getRefDatabase()).invalidateSnapshot();
				throw e;
			}
		}

		@Override
		public Result update(RevWalk walk) throws IOException {
			try {
				return super.update(walk);
			} catch (Exception e) {
				((RefDirectoryWritableSnapshot) getRefDatabase()).invalidateSnapshot();
				throw e;
			}
		}

		@Override
		public Result delete() throws IOException {
			try {
				return super.delete();
			} catch (Exception e) {
				((RefDirectoryWritableSnapshot) getRefDatabase()).invalidateSnapshot();
				throw e;
			}
		}

		@Override
		public Result delete(RevWalk walk) throws IOException {
			try {
				return super.delete(walk);
			} catch (Exception e) {
				((RefDirectoryWritableSnapshot) getRefDatabase()).invalidateSnapshot();
				throw e;
			}
		}

		@Override
		public Result link(String target) throws IOException {
			try {
				return super.link(target);
			} catch (Exception e) {
				((RefDirectoryWritableSnapshot) getRefDatabase()).invalidateSnapshot();
				throw e;
			}
		}
	}

	private static class SnapshotRefDirectoryRename extends RefDirectoryRename {

		SnapshotRefDirectoryRename(RefDirectoryUpdate src,
				RefDirectoryUpdate dst) {
			super(src, dst);
		}

		@Override
		public RefUpdate.Result rename() throws IOException {
			try {
				return super.rename();
			} catch (Exception e) {
				((RefDirectoryWritableSnapshot) getRefDatabase()).invalidateSnapshot();
				throw e;
			}
		}
	}

	private static class SnapshotPackedBatchRefUpdate
			extends PackedBatchRefUpdate {

		SnapshotPackedBatchRefUpdate(RefDirectory refdb) {
			super(refdb);
		}

		SnapshotPackedBatchRefUpdate(RefDirectory refdb,
				boolean shouldLockLooseRefs) {
			super(refdb, shouldLockLooseRefs);
		}

		@Override
		public void execute(RevWalk walk, ProgressMonitor monitor,
				List<String> options) throws IOException {
			try {
				super.execute(walk, monitor, options);
			} catch (Exception e) {
				((RefDirectoryWritableSnapshot) getRefDatabase()).invalidateSnapshot();
				throw e;
			}
		}

		@Override
		public void execute(RevWalk walk, ProgressMonitor monitor)
				throws IOException {
			try {
				super.execute(walk, monitor);
			} catch (Exception e) {
				((RefDirectoryWritableSnapshot) getRefDatabase()).invalidateSnapshot();
				throw e;
			}
		}
	}
}

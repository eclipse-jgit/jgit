package org.eclipse.jgit.internal.storage.reftable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * Operations on {@link MergedReftable} that is common to various reftable-using
 * subclasses of {@link RefDatabase}. See
 * {@link org.eclipse.jgit.internal.storage.dfs.DfsReftableDatabase} for an
 * example.
 */
public abstract class ReftableDatabase {
	// Protects mergedTables.
	private final ReentrantLock lock = new ReentrantLock(true);

	private Reftable mergedTables;

	/**
	 * ReftableDatabase lazily initializes its merged reftable on the first read after
	 * construction or clearCache() call. This function should always instantiate a new
	 * MergedReftable based on the list of reftables specified by the underlying storage.
	 *
	 * @return the ReftableStack for this instance
	 * @throws IOException
	 *             on I/O problems.
	 */
	abstract protected MergedReftable openMergedReftable() throws IOException;

	/**
	 * @return the next available logical timestamp for an additional reftable
	 *         in the stack.
	 * @throws java.io.IOException
	 *             on I/O problems.
	 */
	public long nextUpdateIndex() throws IOException {
		lock.lock();
		try {
			return reader().maxUpdateIndex() + 1;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * @return a ReflogReader for the given ref
	 * @param refname
	 *            the name of the ref.
	 * @throws IOException
	 *             on I/O problems
	 */
	public ReflogReader getReflogReader(String refname) throws IOException {
		lock.lock();
		try {
			return new ReftableReflogReader(lock, reader(), refname);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * @return a ReceiveCommand for the change from oldRef to newRef
	 * @param oldRef
	 *            a ref
	 * @param newRef
	 *            a ref
	 */
	public static ReceiveCommand toCommand(Ref oldRef, Ref newRef) {
		ObjectId oldId = toId(oldRef);
		ObjectId newId = toId(newRef);
		String name = oldRef != null ? oldRef.getName() : newRef.getName();

		if (oldRef != null && oldRef.isSymbolic()) {
			if (newRef != null) {
				if (newRef.isSymbolic()) {
					return ReceiveCommand.link(oldRef.getTarget().getName(),
							newRef.getTarget().getName(), name);
				} else {
					// This should pass in oldId for compat with
					// RefDirectoryUpdate
					return ReceiveCommand.unlink(oldRef.getTarget().getName(),
							newId, name);
				}
			} else {
				return ReceiveCommand.unlink(oldRef.getTarget().getName(),
						ObjectId.zeroId(), name);
			}
		}

		if (newRef != null && newRef.isSymbolic()) {
			if (oldRef != null) {
				if (oldRef.isSymbolic()) {
					return ReceiveCommand.link(oldRef.getTarget().getName(),
							newRef.getTarget().getName(), name);
				} else {
					return ReceiveCommand.link(oldId,
							newRef.getTarget().getName(), name);
				}
			} else {
				return ReceiveCommand.link(ObjectId.zeroId(),
						newRef.getTarget().getName(), name);
			}
		}

		return new ReceiveCommand(oldId, newId, name);
	}

	private static ObjectId toId(Ref ref) {
		if (ref != null) {
			ObjectId id = ref.getObjectId();
			if (id != null) {
				return id;
			}
		}
		return ObjectId.zeroId();
	}

	/**
	 * @return the lock protecting underlying ReftableReaders against concurrent
	 *         reads.
	 */
	public ReentrantLock getLock() {
		return lock;
	}

	/**
	 * @return the merged reftable that is implemented by the stack of
	 *         reftables. Return value must be accessed under lock.
	 * @throws IOException
	 *             on I/O problems
	 */
	private Reftable reader() throws IOException {
		assert lock.isLocked();
		if (mergedTables == null) {
			mergedTables = openMergedReftable();
		}
		return mergedTables;
	}

	/**
	 * @return whether the given refName would be illegal in a repository that
	 *         uses loose refs.
	 * @param refName
	 *            the name to check
	 * @param added
	 *            a sorted set of refs we pretend have been added to the
	 *            database.
	 * @param deleted
	 *            a set of refs we pretend have been removed from the database.
	 * @throws IOException
	 *             on I/O problems
	 */
	public boolean isNameConflicting(String refName, TreeSet<String> added,
			Set<String> deleted) throws IOException {
		lock.lock();
		try {
			Reftable table = reader();

			// Cannot be nested within an existing reference.
			int lastSlash = refName.lastIndexOf('/');
			while (0 < lastSlash) {
				String prefix = refName.substring(0, lastSlash);
				if (!deleted.contains(prefix)
						&& (table.hasRef(prefix) || added.contains(prefix))) {
					return true;
				}
				lastSlash = refName.lastIndexOf('/', lastSlash - 1);
			}

			// Cannot be the container of an existing reference.
			String prefix = refName + '/';
			RefCursor c = table.seekRefsWithPrefix(prefix);
			while (c.next()) {
				if (!deleted.contains(c.getRef().getName())) {
					return true;
				}
			}

			String it = added.ceiling(refName + '/');
			if (it != null && it.startsWith(prefix)) {
				return true;
			}
			return false;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Read a single reference.
	 * <p>
	 * This method expects an unshortened reference name and does not search
	 * using the standard search path.
	 *
	 * @param name
	 *            the unabbreviated name of the reference.
	 * @return the reference (if it exists); else {@code null}.
	 * @throws java.io.IOException
	 *             the reference space cannot be accessed.
	 */
	@Nullable
	public Ref exactRef(String name) throws IOException {
		lock.lock();
		try {
			Reftable table = reader();
			Ref ref = table.exactRef(name);
			if (ref != null && ref.isSymbolic()) {
				return table.resolve(ref);
			}
			return ref;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Returns refs whose names start with a given prefix.
	 *
	 * @param prefix
	 *            string that names of refs should start with; may be empty (to
	 *            return all refs).
	 * @return immutable list of refs whose names start with {@code prefix}.
	 * @throws java.io.IOException
	 *             the reference space cannot be accessed.
	 */
	public List<Ref> getRefsByPrefix(String prefix) throws IOException {
		List<Ref> all = new ArrayList<>();
		lock.lock();
		try {
			Reftable table = reader();
			try (RefCursor rc = RefDatabase.ALL.equals(prefix) ? table.allRefs()
					: table.seekRefsWithPrefix(prefix)) {
				while (rc.next()) {
					Ref ref = table.resolve(rc.getRef());
					if (ref != null && ref.getObjectId() != null) {
						all.add(ref);
					}
				}
			}
		} finally {
			lock.unlock();
		}

		return Collections.unmodifiableList(all);
	}

	/**
	 * Returns all refs that resolve directly to the given {@link ObjectId}.
	 * Includes peeled {@linkObjectId}s.
	 *
	 * @param id
	 *            {@link ObjectId} to resolve
	 * @return a {@link Set} of {@link Ref}s whose tips point to the provided
	 *         id.
	 * @throws java.io.IOException
	 *             on I/O errors.
	 */
	public Set<Ref> getTipsWithSha1(ObjectId id) throws IOException {
		lock.lock();
		try {
			RefCursor cursor = reader().byObjectId(id);
			Set<Ref> refs = new HashSet<>();
			while (cursor.next()) {
				refs.add(cursor.getRef());
			}
			return refs;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Drops all data that might be cached in memory.
	 */
	public void clearCache() {
		lock.lock();
		try {
			mergedTables = null;
		} finally {
			lock.unlock();
		}
	}
}

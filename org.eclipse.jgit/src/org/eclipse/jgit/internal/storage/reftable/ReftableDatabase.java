package org.eclipse.jgit.internal.storage.reftable;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.dfs.DfsReftableDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.RefList;
import org.eclipse.jgit.util.RefMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Holds the implementation of a RefDatabase based on Reftable. This does not
 * derive from RefDatabase to allow classes to inherit from other types of
 * RefDatabase.
 */
public abstract class ReftableDatabase {
	// Protects mergedTables.
	private final ReentrantLock lock = new ReentrantLock(true);

	private Reftable mergedTables;

	private ReftableStack stack;

	// XXX rename. newStack() is a better name.
	/**
	 * @return the ReftableStack for this instance
	 * @throws IOException
	 *             on I/O problems.
	 */
	abstract protected ReftableStack getStack() throws IOException;

	/**
	 * @return a possibly cached ReftableStack instance
	 * @throws IOException
	 *             on I/O problems
	 */
	public ReftableStack stack() throws IOException {
		lock.lock();
		try {
			if (stack == null) {
				stack = getStack();
			}

			return stack;
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
		return new ReftableReflogReader(lock, reader(), refname);
	}

	/**
	 * @return a ReceiveCommand for the change from oldRef to newRef
	 * @param oldRef
	 *            a ref
	 * @param newRef
	 *            a ref
	 * @throws IOException
	 *             on I/O problems
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
	 *         reftables.
	 */
	public Reftable reader() throws IOException {
		lock.lock();
		try {
			if (mergedTables == null) {
				mergedTables = new MergedReftable(stack().readers());
			}
			return mergedTables;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * @return whether the given refName would be illegal in a repository that
	 *         uses loose refs.
	 * @param refName the name to check
	 * @param added a sorted set of refs we pretend have been added to the database.
	 * @param deleted a set of refs we pretend have been removed from the database.
	 */
	public boolean isNameConflicting(String refName,
									 TreeSet<String> added,
									 Set<String> deleted) throws IOException {
		lock.lock();
		try {
			Reftable table = reader();

			// Cannot be nested within an existing reference.
			int lastSlash = refName.lastIndexOf('/');
			while (0 < lastSlash) {
				String prefix = refName.substring(0, lastSlash);
				if (!deleted.contains(prefix) && (table.hasRef(prefix) || added.contains(prefix))) {
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
	 * {@see Repository#exactRef}.
	 */
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
	 * {@see Repository#getRefs}.
	 */
	public Map<String, Ref> getRefs(String prefix) throws IOException {
		RefList.Builder<Ref> all = new RefList.Builder<>();
		lock.lock();
		try {
			Reftable table = reader();
			try (RefCursor rc = DfsReftableDatabase.ALL.equals(prefix)
					? table.allRefs()
					: (prefix.endsWith("/") ? table.seekRefsWithPrefix(prefix) //$NON-NLS-1$
							: table.seekRef(prefix))) {
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

		RefList<Ref> none = RefList.emptyList();
		return new RefMap(prefix, all.toRefList(), none, none);
	}

	/**
	 * {@see Repository#getRefsByPrefix}.
	 */
	public List<Ref> getRefsByPrefix(String prefix) throws IOException {
		List<Ref> all = new ArrayList<>();
		lock.lock();
		try {
			Reftable table = reader();
			try (RefCursor rc = DfsReftableDatabase.ALL.equals(prefix)
					? table.allRefs()
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
	 * {@see Repository#getTipsWithSha1}
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
			if (stack != null) {
				try {
					stack.close();
				} catch (Exception e) {

				}
				stack = null;
			}
			mergedTables = null;
		} finally {
			lock.unlock();
		}
	}
}

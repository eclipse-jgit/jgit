package org.eclipse.jgit.internal.storage.reftable;

import org.eclipse.jgit.internal.storage.dfs.DfsReftableDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.util.RefList;
import org.eclipse.jgit.util.RefMap;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class ReftableDatabase {
    private final ThrowingSupplier<Reftable> reftableSupplier;

    // Protects mergedTables.
    private final ReentrantLock lock = new ReentrantLock(true);
    private Reftable mergedTables;

    public ReentrantLock getLock() {
        return lock;
    }

    public ReftableDatabase(ThrowingSupplier<Reftable> supplier) {
        this.reftableSupplier = supplier;
    }

    public Reftable reader() throws IOException {
        lock.lock();
        try {
            if (mergedTables == null) {
                mergedTables = reftableSupplier.get();
            }
            return mergedTables;
        } finally {
            lock.unlock();
        }
    }

    public boolean isNameConflicting(String refName) throws IOException {

        lock.lock();
        try {
            Reftable table = reader();

            // Cannot be nested within an existing reference.
            int lastSlash = refName.lastIndexOf('/');
            while (0 < lastSlash) {
                if (table.hasRef(refName.substring(0, lastSlash))) {
                    return true;
                }
                lastSlash = refName.lastIndexOf('/', lastSlash - 1);
            }

            // Cannot be the container of an existing reference.
            return table.hasRefsWithPrefix(refName + '/');
        } finally {
            lock.unlock();
        }
    }

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

    public Map<String, Ref> getRefs(String prefix) throws IOException {
        RefList.Builder<Ref> all = new RefList.Builder<Ref>();
        lock.lock();
        try {
            Reftable table = reader();
            try (RefCursor rc = DfsReftableDatabase.ALL.equals(prefix) ? table.allRefs()
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

    public List<Ref> getRefsByPrefix(String prefix) throws IOException {
        List<Ref> all = new ArrayList<Ref>();
        lock.lock();
        try {
            Reftable table = reader();
            try (RefCursor rc = DfsReftableDatabase.ALL.equals(prefix) ? table.allRefs()
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

    public void clearCache() {
        mergedTables = null;
    }

    public interface ThrowingSupplier <T> {
        T get() throws IOException;
    }
}

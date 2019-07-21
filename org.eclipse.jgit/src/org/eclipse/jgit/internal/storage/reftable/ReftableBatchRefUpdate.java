package org.eclipse.jgit.internal.storage.reftable;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.locks.Lock;

import static org.eclipse.jgit.lib.Ref.Storage.NEW;
import static org.eclipse.jgit.lib.Ref.Storage.PACKED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.*;
import static org.eclipse.jgit.transport.ReceiveCommand.Type.UPDATE_NONFASTFORWARD;

public abstract class ReftableBatchRefUpdate extends BatchRefUpdate {
    private final Lock lock;
    private final RefDatabase refdb;
    private final Repository repository;
    private final CheckedGetReftables stack;

    @FunctionalInterface
    public interface CheckedGetReftables {
        List<Reftable> get() throws IOException;
    }

    protected ReftableBatchRefUpdate(RefDatabase refdb, Lock lock,
                                     Repository repository,
                                     CheckedGetReftables stack) {
        super(refdb);
        this.refdb = refdb;
        this.lock = lock;
        this.repository = repository;
        this.stack = stack;
    }

    protected static List<Ref> toNewRefs(RevWalk rw, List<ReceiveCommand> pending)
            throws IOException {
        List<Ref> refs = new ArrayList<>(pending.size());
        for (ReceiveCommand cmd : pending) {
            String name = cmd.getRefName();
            ObjectId newId = cmd.getNewId();
            String newSymref = cmd.getNewSymref();
            if (AnyObjectId.equals(ObjectId.zeroId(), newId)
                    && newSymref == null) {
                refs.add(new ObjectIdRef.Unpeeled(NEW, name, null));
                continue;
            } else if (newSymref != null) {
                refs.add(new SymbolicRef(name,
                        new ObjectIdRef.Unpeeled(NEW, newSymref, null)));
                continue;
            }

            RevObject obj = rw.parseAny(newId);
            RevObject peel = null;
            if (obj instanceof RevTag) {
                peel = rw.peel(obj);
            }
            if (peel != null) {
                refs.add(new ObjectIdRef.PeeledTag(PACKED, name, newId,
                        peel.copy()));
            } else {
                refs.add(new ObjectIdRef.PeeledNonTag(PACKED, name, newId));
            }
        }
        return refs;
    }

    /** {@inheritDoc} */
    @Override
    public void execute(RevWalk rw, ProgressMonitor pm, List<String> options) {
        List<ReceiveCommand> pending = getPending();
        if (pending.isEmpty()) {
            return;
        }
        if (options != null) {
            setPushOptions(options);
        }
        try {
            if (!checkObjectExistence(rw, pending)) {
                return;
            }
            if (!checkNonFastForwards(rw, pending)) {
                return;
            }

            lock.lock();
            try {
                if (!checkExpected(pending)) {
                    return;
                }
                if (!checkConflicting(pending)) {
                    return;
                }
                if (!blockUntilTimestamps(MAX_WAIT)) {
                    return;
                }
                applyUpdates(rw, pending);
                for (ReceiveCommand cmd : pending) {
                    cmd.setResult(OK);
                }
            } finally {
                lock.unlock();
            }
        } catch (IOException e) {
            pending.get(0).setResult(LOCK_FAILURE, "io error"); //$NON-NLS-1$
            ReceiveCommand.abort(pending);
        }
    }

    private static boolean matchOld(ReceiveCommand cmd, @Nullable Ref ref) {
        if (ref == null) {
            return AnyObjectId.equals(ObjectId.zeroId(), cmd.getOldId())
                    && cmd.getOldSymref() == null;
        } else if (ref.isSymbolic()) {
            return ref.getTarget().getName().equals(cmd.getOldSymref());
        }
        ObjectId id = ref.getObjectId();
        if (id == null) {
            id = ObjectId.zeroId();
        }
        return cmd.getOldId().equals(id);
    }

    protected abstract void applyUpdates(RevWalk rw, List<ReceiveCommand> pending) throws  IOException;

    private List<ReceiveCommand> getPending() {
        return ReceiveCommand.filter(getCommands(), NOT_ATTEMPTED);
    }

    private boolean checkObjectExistence(RevWalk rw,
                                         List<ReceiveCommand> pending) throws IOException {
        for (ReceiveCommand cmd : pending) {
            try {
                if (!cmd.getNewId().equals(ObjectId.zeroId())) {
                    rw.parseAny(cmd.getNewId());
                }
            } catch (MissingObjectException e) {
                // ReceiveCommand#setResult(Result) converts REJECTED to
                // REJECTED_NONFASTFORWARD, even though that result is also
                // used for a missing object. Eagerly handle this case so we
                // can set the right result.
                cmd.setResult(REJECTED_MISSING_OBJECT);
                ReceiveCommand.abort(pending);
                return false;
            }
        }
        return true;
    }

    private boolean checkNonFastForwards(RevWalk rw, List<ReceiveCommand> pending) throws IOException {
        if (isAllowNonFastForwards()) {
            return true;
        }
        for (ReceiveCommand cmd : pending) {
            cmd.updateType(rw);
            if (cmd.getType() == UPDATE_NONFASTFORWARD) {
                cmd.setResult(REJECTED_NONFASTFORWARD);
                ReceiveCommand.abort(pending);
                return false;
            }
        }
        return true;
    }

    private boolean checkConflicting(List<ReceiveCommand> pending)
            throws IOException {
        Set<String> names = new HashSet<>();
        for (ReceiveCommand cmd : pending) {
            names.add(cmd.getRefName());
        }

        boolean ok = true;
        for (ReceiveCommand cmd : pending) {
            String name = cmd.getRefName();
            if (refdb.isNameConflicting(name)) {
                cmd.setResult(LOCK_FAILURE);
                ok = false;
            } else {
                int s = name.lastIndexOf('/');
                while (0 < s) {
                    if (names.contains(name.substring(0, s))) {
                        cmd.setResult(LOCK_FAILURE);
                        ok = false;
                        break;
                    }
                    s = name.lastIndexOf('/', s - 1);
                }
            }
        }
        if (!ok && isAtomic()) {
            ReceiveCommand.abort(pending);
            return false;
        }
        return ok;
    }

    private boolean checkExpected(List<ReceiveCommand> pending)
            throws IOException {
        for (ReceiveCommand cmd : pending) {
            if (!matchOld(cmd, refdb.exactRef(cmd.getRefName()))) {
                cmd.setResult(LOCK_FAILURE);
                if (isAtomic()) {
                    ReceiveCommand.abort(pending);
                    return false;
                }
            }
        }
        return true;
    }

    protected ReftableWriter.Stats write(OutputStream os, ReftableConfig cfg,
                                         long updateIndex, List<Ref> newRefs, List<ReceiveCommand> pending)
            throws IOException {
        ReftableWriter writer = new ReftableWriter(cfg)
                .setMinUpdateIndex(updateIndex).setMaxUpdateIndex(updateIndex)
                .begin(os).sortAndWriteRefs(newRefs);
        if (!isRefLogDisabled()) {
            writeLog(writer, updateIndex, pending);
        }
        writer.finish();
        return writer.getStats();
    }

    private void writeLog(ReftableWriter writer, long updateIndex,
List<ReceiveCommand> pending) throws IOException {
        Map<String, ReceiveCommand> cmds = new HashMap<>();
        List<String> byName = new ArrayList<>(pending.size());
        for (ReceiveCommand cmd : pending) {
            cmds.put(cmd.getRefName(), cmd);
            byName.add(cmd.getRefName());
        }
        Collections.sort(byName);

        PersonIdent ident = getRefLogIdent();
        if (ident == null) {
            ident = new PersonIdent(repository);
        }
        for (String name : byName) {
            ReceiveCommand cmd = cmds.get(name);
            if (isRefLogDisabled(cmd)) {
                continue;
            }
            String msg = getRefLogMessage(cmd);
            if (isRefLogIncludingResult(cmd)) {
                String strResult = toResultString(cmd);
                if (strResult != null) {
                    msg = msg.isEmpty() ? strResult : msg + ": " + strResult; //$NON-NLS-1$
                }
            }
            writer.writeLog(name, updateIndex, ident, cmd.getOldId(),
                    cmd.getNewId(), msg);
        }
    }

    private String toResultString(ReceiveCommand cmd) {
        switch (cmd.getType()) {
        case CREATE:
            return ReflogEntry.PREFIX_CREATED;
        case UPDATE:
            // Match the behavior of a single RefUpdate. In that case, setting
            // the force bit completely bypasses the potentially expensive
            // isMergedInto check, by design, so the reflog message may be
            // inaccurate.
            //
            // Similarly, this class bypasses the isMergedInto checks when the
            // force bit is set, meaning we can't actually distinguish between
            // UPDATE and UPDATE_NONFASTFORWARD when isAllowNonFastForwards()
            // returns true.
            return isAllowNonFastForwards() ? ReflogEntry.PREFIX_FORCED_UPDATE
                    : ReflogEntry.PREFIX_FAST_FORWARD;
        case UPDATE_NONFASTFORWARD:
            return ReflogEntry.PREFIX_FORCED_UPDATE;
        default:
            return null;
        }
    }

    protected long nextUpdateIndex() throws IOException {
        long updateIndex = 0;
        for (Reftable r : stack.get()) {
            if (r instanceof ReftableReader) {
                updateIndex = Math.max(updateIndex,
                        ((ReftableReader) r).maxUpdateIndex());
            }
        }
        return updateIndex + 1;
    }
}

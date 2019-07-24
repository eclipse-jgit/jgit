/*
 * Copyright (C) 2017, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.internal.storage.reftable;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import static org.eclipse.jgit.lib.Ref.Storage.NEW;
import static org.eclipse.jgit.lib.Ref.Storage.PACKED;

import static org.eclipse.jgit.transport.ReceiveCommand.Result.LOCK_FAILURE;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.NOT_ATTEMPTED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.OK;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_MISSING_OBJECT;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.LOCK_FAILURE;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.NOT_ATTEMPTED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.OK;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_NONFASTFORWARD;
import static org.eclipse.jgit.transport.ReceiveCommand.Type.UPDATE_NONFASTFORWARD;

/**
 * {@link org.eclipse.jgit.lib.BatchRefUpdate} for Reftable based RefDatabase.
 */
public abstract class ReftableBatchRefUpdate extends BatchRefUpdate {
    private final Lock lock;
    private final RefDatabase refDb;
    private final Repository repository;

    /**
     * Initialize.
     *
     * @param refdb
     *   The RefDatabase
     * @param lock
     *   A lock protecting the refdatabase's state
     * @param repository
     *   The repository on which this update will run
     */
    protected ReftableBatchRefUpdate(RefDatabase refdb, Lock lock,
                                     Repository repository) {
        super(refdb);
        this.refDb = refdb;
        this.lock = lock;
        this.repository = repository;
    }

    /** Extracts and peels the refs out of the ReceiveCommands */
    private static List<Ref> toNewRefs(RevWalk rw, List<ReceiveCommand> pending)
            throws IOException {
        List<Ref> refs = new ArrayList<>(pending.size());
        for (ReceiveCommand cmd : pending) {
            String name = cmd.getRefName();
            ObjectId newId = cmd.getNewId();
            String newSymref = cmd.getNewSymref();
            if (AnyObjectId.isEqual(ObjectId.zeroId(), newId)
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

    /**
     * Returns a snapshot on top of which a new reftable should be created.
     */
    abstract protected ReftableStack getStack() throws IOException;

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

                List<Ref> newRefs = toNewRefs(rw, pending);
                applyUpdates(newRefs, pending);
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
            return AnyObjectId.isEqual(ObjectId.zeroId(), cmd.getOldId())
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

    /**
     * Implements the storage-specific part of the update.
     *
     * @param newRefs
     *   the new refs to create
     * @param pending
     *   the pending receive commands to be executed
     * @throws IOException
     *   if any of the writes fail.
     */
    protected abstract void applyUpdates(List<Ref> newRefs, List<ReceiveCommand> pending)
            throws IOException;

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
            if (refDb.isNameConflicting(name)) {
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
            if (!matchOld(cmd, refDb.exactRef(cmd.getRefName()))) {
                cmd.setResult(LOCK_FAILURE);
                if (isAtomic()) {
                    ReceiveCommand.abort(pending);
                    return false;
                }
            }
        }
        return true;
    }

    protected ReftableWriter.Stats write(ReftableWriter writer, OutputStream os,
                                         List<Ref> newRefs, List<ReceiveCommand> pending)
            throws IOException {
        long updateIndex = getStack().nextUpdateIndex();
        writer.setMinUpdateIndex(updateIndex).setMaxUpdateIndex(updateIndex)
                .begin(os)
                .sortAndWriteRefs(newRefs);
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
}

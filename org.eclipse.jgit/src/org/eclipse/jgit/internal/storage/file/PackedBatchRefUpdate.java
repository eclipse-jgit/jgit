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

package org.eclipse.jgit.internal.storage.file;

import static java.util.stream.Collectors.toList;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.LOCK_FAILURE;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.NOT_ATTEMPTED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_NONFASTFORWARD;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.file.RefDirectory.PackedRefList;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.RefList;

/**
 * Implementation of {@link BatchRefUpdate} that uses the {@code packed-refs}
 * file to support atomically updating multiple refs.
 * <p>
 * The algorithm is designed to be compatible with traditional single ref
 * updates operating on single refs only. Regardless of success or failure, the
 * results are atomic: from the perspective of any reader, either all updates in
 * the batch will be visible, or none will. In the case of process failure
 * during any of the following steps, removal of stale lock files is always
 * safe, and will never result in an inconsistent state, although the update may
 * or may not have been applied.
 * <p>
 * The algorithm is:
 * <ol>
 * <li>Pack loose refs involved in the transaction using the normal pack-refs
 * operation. This ensures that creating lock files in the following step
 * succeeds even if a batch contains both a delete of {@code refs/x} (loose) and
 * a create of {@code refs/x/y}.</li>
 * <li>Create locks for all loose refs involved in the transaction, even if they
 * are not currently loose.</li>
 * <li>Pack loose refs again, this time while holding all lock files (see {@link
 * RefDirectory#pack(Map)}), without deleting them afterwards. This covers a
 * potential race where new loose refs were created after the initial packing
 * step. If no new loose refs were created during this race, this step does not
 * modify any files on disk. Keep the merged state in memory.</li>
 * <li>Update the in-memory packed refs with the commands in the batch, possibly
 * failing the whole batch if any old ref values do not match.</li>
 * <li>If the update succeeds, lock {@code packed-refs} and commit by atomically
 * renaming the lock file.</li>
 * <li>Delete loose ref lock files.</li>
 * </ol>
 *
 * Because the packed-refs file format is a sorted list, this algorithm is
 * linear in the total number of refs, regardless of the batch size. This can be
 * a significant slowdown on repositories with large numbers of refs; callers
 * that prefer speed over atomicity should use {@code setAtomic(false)}. As an
 * optimization, an update containing a single ref update does not use the
 * packed-refs protocol.
 */
class PackedBatchRefUpdate extends BatchRefUpdate {
	private RefDirectory refdb;

	PackedBatchRefUpdate(RefDirectory refdb) {
		super(refdb);
		this.refdb = refdb;
	}

	@Override
	public void execute(RevWalk walk, ProgressMonitor monitor,
			List<String> options) throws IOException {
		if (!isAtomic()) {
			// Use default one-by-one implementation.
			super.execute(walk, monitor, options);
			return;
		}
		List<ReceiveCommand> pending =
				ReceiveCommand.filter(getCommands(), NOT_ATTEMPTED);
		if (pending.isEmpty()) {
			return;
		}
		if (pending.size() == 1) {
			// Single-ref updates are always atomic, no need for packed-refs.
			super.execute(walk, monitor, options);
			return;
		}

		// Required implementation details copied from super.execute.
		if (!blockUntilTimestamps(MAX_WAIT)) {
			return;
		}
		if (options != null) {
			setPushOptions(options);
		}
		// End required implementation details.

		// Check for conflicting names before attempting to acquire locks, since
		// lockfile creation may fail on file/directory conflicts.
		if (!checkConflictingNames(pending)) {
			return;
		}

		if (!checkObjectExistence(walk, pending)) {
			return;
		}

		if (!checkNonFastForwards(walk, pending)) {
			return;
		}

		// Pack refs normally, so we can create lock files even in the case where
		// refs/x is deleted and refs/x/y is created in this batch.
		refdb.pack(
				pending.stream().map(ReceiveCommand::getRefName).collect(toList()));

		Map<String, LockFile> locks = new HashMap<>();
		try {
			if (!lockLooseRefs(pending, locks)) {
				return;
			}
			PackedRefList oldPackedList = refdb.pack(locks);
			RefList<Ref> newRefs = applyUpdates(walk, oldPackedList, pending);
			if (newRefs == null) {
				return;
			}
			LockFile packedRefsLock = new LockFile(refdb.packedRefsFile);
			try {
				packedRefsLock.lock();
				refdb.commitPackedRefs(packedRefsLock, newRefs, oldPackedList);
			} finally {
				packedRefsLock.unlock();
			}
		} finally {
			locks.values().forEach(LockFile::unlock);
		}

		refdb.fireRefsChanged();
		pending.forEach(c -> c.setResult(ReceiveCommand.Result.OK));
	}

	private boolean checkConflictingNames(List<ReceiveCommand> commands)
			throws IOException {
		Set<String> takenNames = new HashSet<>();
		Set<String> takenPrefixes = new HashSet<>();
		Set<String> deletes = new HashSet<>();
		for (ReceiveCommand cmd : commands) {
			if (cmd.getType() != ReceiveCommand.Type.DELETE) {
				takenNames.add(cmd.getRefName());
				addPrefixesTo(cmd.getRefName(), takenPrefixes);
			} else {
				deletes.add(cmd.getRefName());
			}
		}
		Set<String> initialRefs = refdb.getRefs(RefDatabase.ALL).keySet();
		for (String name : initialRefs) {
			if (!deletes.contains(name)) {
				takenNames.add(name);
				addPrefixesTo(name, takenPrefixes);
			}
		}

		for (ReceiveCommand cmd : commands) {
			if (cmd.getType() != ReceiveCommand.Type.DELETE &&
					takenPrefixes.contains(cmd.getRefName())) {
				// This ref is a prefix of some other ref. This check doesn't apply when
				// this command is a delete, because if the ref is deleted nobody will
				// ever be creating a loose ref with that name.
				lockFailure(cmd, commands);
				return false;
			}
			for (String prefix : getPrefixes(cmd.getRefName())) {
				if (takenNames.contains(prefix)) {
					// A prefix of this ref is already a refname. This check does apply
					// when this command is a delete, because we would need to create the
					// refname as a directory in order to create a lockfile for the
					// to-be-deleted ref.
					lockFailure(cmd, commands);
					return false;
				}
			}
		}
		return true;
	}

	private boolean checkObjectExistence(RevWalk walk,
			List<ReceiveCommand> commands) throws IOException {
		for (ReceiveCommand cmd : commands) {
			try {
				if (!cmd.getNewId().equals(ObjectId.zeroId())) {
					walk.parseAny(cmd.getNewId());
				}
			} catch (MissingObjectException e) {
				// ReceiveCommand#setResult(Result) converts REJECTED to
				// REJECTED_NONFASTFORWARD, even though that result is also used for a
				// missing object. Eagerly handle this case so we can set the right
				// result.
				reject(cmd, ReceiveCommand.Result.REJECTED_MISSING_OBJECT, commands);
				return false;
			}
		}
		return true;
	}

	private boolean checkNonFastForwards(RevWalk walk,
			List<ReceiveCommand> commands) throws IOException {
		if (isAllowNonFastForwards()) {
			return true;
		}
		for (ReceiveCommand cmd : commands) {
			cmd.updateType(walk);
			if (cmd.getType() == ReceiveCommand.Type.UPDATE_NONFASTFORWARD) {
				reject(cmd, REJECTED_NONFASTFORWARD, commands);
				return false;
			}
		}
		return true;
	}

	private boolean lockLooseRefs(List<ReceiveCommand> commands,
			Map<String, LockFile> locks) throws IOException {
		for (ReceiveCommand c : commands) {
			LockFile lock = new LockFile(refdb.fileFor(c.getRefName()));
			if (!lock.lock()) {
				lockFailure(c, commands);
				return false;
			}
			locks.put(c.getRefName(), lock);
		}
		return true;
	}

	private static RefList<Ref> applyUpdates(RevWalk walk, RefList<Ref> refs,
			List<ReceiveCommand> commands) throws IOException {
		int nDeletes = 0;
		List<ReceiveCommand> adds = new ArrayList<>(commands.size());
		for (ReceiveCommand c : commands) {
			if (c.getType() == ReceiveCommand.Type.CREATE) {
				adds.add(c);
			} else if (c.getType() == ReceiveCommand.Type.DELETE) {
				nDeletes++;
			}
		}
		int addIdx = 0;

		// Construct a new RefList by linearly scanning the old list, and merging in
		// any updates.
		Map<String, ReceiveCommand> byName = byName(commands);
		RefList.Builder<Ref> b =
				new RefList.Builder<>(refs.size() - nDeletes + adds.size());
		for (Ref ref : refs) {
			String name = ref.getName();
			ReceiveCommand cmd = byName.remove(name);
			if (cmd == null) {
				b.add(ref);
				continue;
			}
			if (!cmd.getOldId().equals(ref.getObjectId())) {
				lockFailure(cmd, commands);
				return null;
			}

			// Consume any adds between the last and current ref.
			while (addIdx < adds.size()) {
				ReceiveCommand currAdd = adds.get(addIdx);
				if (currAdd.getRefName().compareTo(name) < 0) {
					b.add(peeledRef(walk, currAdd));
					byName.remove(currAdd.getRefName());
				} else {
					break;
				}
				addIdx++;
			}

			if (cmd.getType() != ReceiveCommand.Type.DELETE) {
				b.add(peeledRef(walk, cmd));
			}
		}

		// All remaining adds are valid, since the refs didn't exist.
		while (addIdx < adds.size()) {
			ReceiveCommand cmd = adds.get(addIdx++);
			byName.remove(cmd.getRefName());
			b.add(peeledRef(walk, cmd));
		}

		// Any remaining updates/deletes do not correspond to any existing refs, so
		// they are lock failures.
		if (!byName.isEmpty()) {
			lockFailure(byName.values().iterator().next(), commands);
			return null;
		}

		return b.toRefList();
	}

	private static Map<String, ReceiveCommand> byName(
			List<ReceiveCommand> commands) {
		Map<String, ReceiveCommand> ret = new LinkedHashMap<>();
		for (ReceiveCommand cmd : commands) {
			ret.put(cmd.getRefName(), cmd);
		}
		return ret;
	}

	private static Ref peeledRef(RevWalk walk, ReceiveCommand cmd)
			throws IOException {
		ObjectId newId = cmd.getNewId().copy();
		RevObject obj = walk.parseAny(newId);
		if (obj instanceof RevTag) {
			return new ObjectIdRef.PeeledTag(
					Ref.Storage.PACKED, cmd.getRefName(), newId, walk.peel(obj).copy());
		}
		return new ObjectIdRef.PeeledNonTag(
				Ref.Storage.PACKED, cmd.getRefName(), newId);
	}

	private static void lockFailure(ReceiveCommand cmd,
			List<ReceiveCommand> commands) {
		reject(cmd, LOCK_FAILURE, commands);
	}

	private static void reject(ReceiveCommand cmd, ReceiveCommand.Result result,
			List<ReceiveCommand> commands) {
		cmd.setResult(result);
		for (ReceiveCommand c2 : commands) {
			if (c2.getResult() == ReceiveCommand.Result.OK) {
				// Undo OK status so ReceiveCommand#abort aborts it. Assumes this method
				// is always called before committing any updates to disk.
				c2.setResult(ReceiveCommand.Result.NOT_ATTEMPTED);
			}
		}
		ReceiveCommand.abort(commands);
	}
}

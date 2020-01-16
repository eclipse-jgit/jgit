/*
 * Copyright (C) 2019 Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import static org.eclipse.jgit.lib.Ref.UNDEFINED_UPDATE_INDEX;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;
import static org.eclipse.jgit.lib.Ref.Storage.PACKED;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.events.RefsChangedEvent;
import org.eclipse.jgit.internal.storage.reftable.MergedReftable;
import org.eclipse.jgit.internal.storage.reftable.ReftableBatchRefUpdate;
import org.eclipse.jgit.internal.storage.reftable.ReftableDatabase;
import org.eclipse.jgit.internal.storage.reftable.ReftableWriter;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.RefList;
import org.eclipse.jgit.util.RefMap;

/**
 * Implements RefDatabase using reftable for storage.
 *
 * This class is threadsafe.
 */
public class FileReftableDatabase extends RefDatabase {
	private final ReftableDatabase reftableDatabase;

	private final FileRepository fileRepository;

	private final FileReftableStack reftableStack;

	FileReftableDatabase(FileRepository repo, File refstackName) throws IOException {
		this.fileRepository = repo;
		this.reftableStack = new FileReftableStack(refstackName,
			new File(fileRepository.getDirectory(), Constants.REFTABLE),
			() -> fileRepository.fireEvent(new RefsChangedEvent()),
			() -> fileRepository.getConfig());
		this.reftableDatabase = new ReftableDatabase() {

			@Override
			public MergedReftable openMergedReftable() throws IOException {
				return reftableStack.getMergedReftable();
			}
		};
	}

	ReflogReader getReflogReader(String refname) throws IOException {
		return reftableDatabase.getReflogReader(refname);
	}

	/**
	 * @param repoDir
	 * @return whether the given repo uses reftable for refdb storage.
	 */
	public static boolean isReftable(File repoDir) {
		return new File(repoDir, "refs").isFile() //$NON-NLS-1$
				&& new File(repoDir, Constants.REFTABLE).isDirectory();
	}

	/** {@inheritDoc} */
	@Override
	public boolean hasFastTipsWithSha1() throws IOException {
		return reftableDatabase.hasFastTipsWithSha1();
	}

	/**
	 * Runs a full compaction for GC purposes.
	 * @throws IOException on I/O errors
	 */
	public void compactFully() throws IOException {
		reftableDatabase.getLock().lock();
		try {
			reftableStack.compactFully();
		} finally {
			reftableDatabase.getLock().unlock();
		}
	}

	private ReentrantLock getLock() {
		return reftableDatabase.getLock();
	}

	/** {@inheritDoc} */
	@Override
	public boolean performsAtomicTransactions() {
		return true;
	}

	/** {@inheritDoc} */
	@NonNull
	@Override
	public BatchRefUpdate newBatchUpdate() {
		return new FileReftableBatchRefUpdate(this, fileRepository);
	}

	/** {@inheritDoc} */
	@Override
	public RefUpdate newUpdate(String refName, boolean detach)
			throws IOException {
		boolean detachingSymbolicRef = false;
		Ref ref = exactRef(refName);

		if (ref == null) {
			ref = new ObjectIdRef.Unpeeled(NEW, refName, null);
		} else {
			detachingSymbolicRef = detach && ref.isSymbolic();
		}

		RefUpdate update = new FileReftableRefUpdate(ref);
		if (detachingSymbolicRef) {
			update.setDetachingSymbolicRef();
		}
		return update;
	}

	/** {@inheritDoc} */
	@Override
	public Ref exactRef(String name) throws IOException {
		return reftableDatabase.exactRef(name);
	}

	/** {@inheritDoc} */
	@Override
	public List<Ref> getRefs() throws IOException {
		return super.getRefs();
	}

	/** {@inheritDoc} */
	@Override
	public Map<String, Ref> getRefs(String prefix) throws IOException {
		List<Ref> refs = reftableDatabase.getRefsByPrefix(prefix);
		RefList.Builder<Ref> builder = new RefList.Builder<>(refs.size());
		for (Ref r : refs) {
			builder.add(r);
		}
		return new RefMap(prefix, builder.toRefList(), RefList.emptyList(),
				RefList.emptyList());
	}

	/** {@inheritDoc} */
	@Override
	public List<Ref> getAdditionalRefs() throws IOException {
		return Collections.emptyList();
	}

	/** {@inheritDoc} */
	@Override
	public Ref peel(Ref ref) throws IOException {
		Ref oldLeaf = ref.getLeaf();
		if (oldLeaf.isPeeled() || oldLeaf.getObjectId() == null) {
			return ref;
		}
		return recreate(ref, doPeel(oldLeaf), hasVersioning());

	}

	private Ref doPeel(Ref leaf) throws IOException {
		try (RevWalk rw = new RevWalk(fileRepository)) {
			RevObject obj = rw.parseAny(leaf.getObjectId());
			if (obj instanceof RevTag) {
				return new ObjectIdRef.PeeledTag(leaf.getStorage(),
						leaf.getName(), leaf.getObjectId(), rw.peel(obj).copy(),
						hasVersioning() ? leaf.getUpdateIndex()
								: UNDEFINED_UPDATE_INDEX);
			}
			return new ObjectIdRef.PeeledNonTag(leaf.getStorage(),
					leaf.getName(), leaf.getObjectId(),
					hasVersioning() ? leaf.getUpdateIndex()
							: UNDEFINED_UPDATE_INDEX);

		}
	}

	private static Ref recreate(Ref old, Ref leaf, boolean hasVersioning) {
		if (old.isSymbolic()) {
			Ref dst = recreate(old.getTarget(), leaf, hasVersioning);
			return new SymbolicRef(old.getName(), dst,
					hasVersioning ? old.getUpdateIndex()
							: UNDEFINED_UPDATE_INDEX);
		}
		return leaf;
	}

	private class FileRefRename extends RefRename {
		FileRefRename(RefUpdate src, RefUpdate dst) {
			super(src, dst);
		}

		void writeRename(ReftableWriter w) throws IOException {
			long idx = reftableDatabase.nextUpdateIndex();
			w.setMinUpdateIndex(idx).setMaxUpdateIndex(idx).begin();
			List<Ref> refs = new ArrayList<>(3);

			Ref dest = destination.getRef();
			Ref head = exactRef(Constants.HEAD);
			if (head != null && head.isSymbolic()
					&& head.getLeaf().getName().equals(source.getName())) {
				head = new SymbolicRef(Constants.HEAD, dest, idx);
				refs.add(head);
			}

			ObjectId objId = source.getRef().getObjectId();

			// XXX should we check if the source is a Tag vs. NonTag?
			refs.add(new ObjectIdRef.PeeledNonTag(Ref.Storage.NEW,
					destination.getName(), objId));
			refs.add(new ObjectIdRef.Unpeeled(Ref.Storage.NEW, source.getName(),
					null));

			w.sortAndWriteRefs(refs);
			PersonIdent who = destination.getRefLogIdent();
			if (who == null) {
				who = new PersonIdent(fileRepository);
			}

			if (!destination.getRefLogMessage().isEmpty()) {
				List<String> refnames = refs.stream().map(r -> r.getName())
						.collect(Collectors.toList());
				Collections.sort(refnames);
				for (String s : refnames) {
					ObjectId old = (Constants.HEAD.equals(s)
							|| s.equals(source.getName())) ? objId
									: ObjectId.zeroId();
					ObjectId newId = (Constants.HEAD.equals(s)
							|| s.equals(destination.getName())) ? objId
									: ObjectId.zeroId();

					w.writeLog(s, idx, who, old, newId,
							destination.getRefLogMessage());
				}
			}
		}

		@Override
		protected RefUpdate.Result doRename() throws IOException {
			Ref src = exactRef(source.getName());
			if (exactRef(destination.getName()) != null || src == null
					|| !source.getOldObjectId().equals(src.getObjectId())) {
				return RefUpdate.Result.LOCK_FAILURE;
			}

			if (src.isSymbolic()) {
				// We could support this, but this is easier and compatible.
				return RefUpdate.Result.IO_FAILURE;
			}

			if (!addReftable(this::writeRename)) {
				return RefUpdate.Result.LOCK_FAILURE;
			}

			return RefUpdate.Result.RENAMED;
		}
	}

	/** {@inheritDoc} */
	@Override
	public RefRename newRename(String fromName, String toName)
			throws IOException {
		RefUpdate src = newUpdate(fromName, true);
		RefUpdate dst = newUpdate(toName, true);
		return new FileRefRename(src, dst);
	}

	/** {@inheritDoc} */
	@Override
	public boolean isNameConflicting(String name) throws IOException {
		return reftableDatabase.isNameConflicting(name, new TreeSet<>(),
				new HashSet<>());
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		reftableStack.close();
	}

	/** {@inheritDoc} */
	@Override
	public void create() throws IOException {
		FileUtils.mkdir(
				new File(fileRepository.getDirectory(), Constants.REFTABLE),
				true);
	}

	private boolean addReftable(FileReftableStack.Writer w) throws IOException {
		if (!reftableStack.addReftable(w)) {
			reftableStack.reload();
			reftableDatabase.clearCache();
			return false;
		}
		reftableDatabase.clearCache();

		return true;
	}

	private class FileReftableBatchRefUpdate extends ReftableBatchRefUpdate {
		FileReftableBatchRefUpdate(FileReftableDatabase db,
				Repository repository) {
			super(db, db.reftableDatabase, db.getLock(), repository);
		}

		@Override
		protected void applyUpdates(List<Ref> newRefs,
				List<ReceiveCommand> pending) throws IOException {
			if (!addReftable(rw -> write(rw, newRefs, pending))) {
				for (ReceiveCommand c : pending) {
					if (c.getResult() == ReceiveCommand.Result.NOT_ATTEMPTED) {
						c.setResult(RefUpdate.Result.LOCK_FAILURE);
					}
				}
			}
		}
	}

	private class FileReftableRefUpdate extends RefUpdate {
		FileReftableRefUpdate(Ref ref) {
			super(ref);
		}

		@Override
		protected RefDatabase getRefDatabase() {
			return FileReftableDatabase.this;
		}

		@Override
		protected Repository getRepository() {
			return FileReftableDatabase.this.fileRepository;
		}

		@Override
		protected void unlock() {
			// nop.
		}

		private RevWalk rw;

		private Ref dstRef;

		@Override
		public Result update(RevWalk walk) throws IOException {
			try {
				rw = walk;
				return super.update(walk);
			} finally {
				rw = null;
			}
		}

		@Override
		protected boolean tryLock(boolean deref) throws IOException {
			dstRef = getRef();
			if (deref) {
				dstRef = dstRef.getLeaf();
			}

			Ref derefed = exactRef(dstRef.getName());
			if (derefed != null) {
				setOldObjectId(derefed.getObjectId());
			}

			return true;
		}

		void writeUpdate(ReftableWriter w) throws IOException {
			Ref newRef = null;
			if (rw != null && !ObjectId.zeroId().equals(getNewObjectId())) {
				RevObject obj = rw.parseAny(getNewObjectId());
				if (obj instanceof RevTag) {
					newRef = new ObjectIdRef.PeeledTag(Ref.Storage.PACKED,
							dstRef.getName(), getNewObjectId(),
							rw.peel(obj).copy());
				}
			}
			if (newRef == null) {
				newRef = new ObjectIdRef.PeeledNonTag(Ref.Storage.PACKED,
						dstRef.getName(), getNewObjectId());
			}

			long idx = reftableDatabase.nextUpdateIndex();
			w.setMinUpdateIndex(idx).setMaxUpdateIndex(idx).begin()
					.writeRef(newRef);

			ObjectId oldId = getOldObjectId();
			if (oldId == null) {
				oldId = ObjectId.zeroId();
			}
			w.writeLog(dstRef.getName(), idx, getRefLogIdent(), oldId,
					getNewObjectId(), getRefLogMessage());
		}

		@Override
		public PersonIdent getRefLogIdent() {
			PersonIdent who = super.getRefLogIdent();
			if (who == null) {
				who = new PersonIdent(getRepository());
			}
			return who;
		}

		void writeDelete(ReftableWriter w) throws IOException {
			Ref newRef = new ObjectIdRef.Unpeeled(Ref.Storage.NEW,
					dstRef.getName(), null);
			long idx = reftableDatabase.nextUpdateIndex();
			w.setMinUpdateIndex(idx).setMaxUpdateIndex(idx).begin()
					.writeRef(newRef);

			ObjectId oldId = ObjectId.zeroId();
			Ref old = exactRef(dstRef.getName());
			if (old != null) {
				old = old.getLeaf();
				if (old.getObjectId() != null) {
					oldId = old.getObjectId();
				}
			}

			w.writeLog(dstRef.getName(), idx, getRefLogIdent(), oldId,
					ObjectId.zeroId(), getRefLogMessage());
		}

		@Override
		protected Result doUpdate(Result desiredResult) throws IOException {
			if (isRefLogIncludingResult()) {
				setRefLogMessage(
						getRefLogMessage() + ": " + desiredResult.toString(), //$NON-NLS-1$
						false);
			}

			if (!addReftable(this::writeUpdate)) {
				return Result.LOCK_FAILURE;
			}

			return desiredResult;
		}

		@Override
		protected Result doDelete(Result desiredResult) throws IOException {

			if (isRefLogIncludingResult()) {
				setRefLogMessage(
						getRefLogMessage() + ": " + desiredResult.toString(), //$NON-NLS-1$
						false);
			}

			if (!addReftable(this::writeDelete)) {
				return Result.LOCK_FAILURE;
			}

			return desiredResult;
		}

		void writeLink(ReftableWriter w) throws IOException {
			long idx = reftableDatabase.nextUpdateIndex();
			w.setMinUpdateIndex(idx).setMaxUpdateIndex(idx).begin()
					.writeRef(dstRef);

			ObjectId beforeId = ObjectId.zeroId();
			Ref before = exactRef(dstRef.getName());
			if (before != null) {
				before = before.getLeaf();
				if (before.getObjectId() != null) {
					beforeId = before.getObjectId();
				}
			}

			Ref after = dstRef.getLeaf();
			ObjectId afterId = ObjectId.zeroId();
			if (after.getObjectId() != null) {
				afterId = after.getObjectId();
			}

			w.writeLog(dstRef.getName(), idx, getRefLogIdent(), beforeId,
					afterId, getRefLogMessage());
		}

		@Override
		protected Result doLink(String target) throws IOException {
			if (isRefLogIncludingResult()) {
				setRefLogMessage(
						getRefLogMessage() + ": " + Result.FORCED.toString(), //$NON-NLS-1$
						false);
			}

			boolean exists = exactRef(getName()) != null;
			dstRef = new SymbolicRef(getName(),
					new ObjectIdRef.Unpeeled(Ref.Storage.NEW, target, null),
					reftableDatabase.nextUpdateIndex());

			if (!addReftable(this::writeLink)) {
				return Result.LOCK_FAILURE;
			}
			// XXX unclear if we should support FORCED here. Baseclass says
			// NEW is OK ?
			return exists ? Result.FORCED : Result.NEW;
		}
	}

	private static void writeConvertTable(Repository repo, ReftableWriter w,
			boolean writeLogs) throws IOException {
		int size = 0;
		List<Ref> refs = repo.getRefDatabase().getRefs();
		if (writeLogs) {
			for (Ref r : refs) {
				ReflogReader rlr = repo.getReflogReader(r.getName());
				if (rlr != null) {
					size = Math.max(rlr.getReverseEntries().size(), size);
				}
			}
		}
		// We must use 1 here, nextUpdateIndex() on the empty stack is 1.
		w.setMinUpdateIndex(1).setMaxUpdateIndex(size + 1).begin();

		// The spec says to write the logs in the first table, and put refs in a
		// separate table, but this complicates the compaction (when we can we drop
		// deletions? Can we compact the .log table and the .ref table together?)
		try (RevWalk rw = new RevWalk(repo)) {
			List<Ref> toWrite = new ArrayList<>(refs.size());
			for (Ref r : refs) {
				toWrite.add(refForWrite(rw, r));
			}
			w.sortAndWriteRefs(toWrite);
		}

		if (writeLogs) {
			for (Ref r : refs) {
				long idx = size;
				ReflogReader reader = repo.getReflogReader(r.getName());
				if (reader == null) {
					continue;
				}
				for (ReflogEntry e : reader.getReverseEntries()) {
					w.writeLog(r.getName(), idx, e.getWho(), e.getOldId(),
							e.getNewId(), e.getComment());
					idx--;
				}
			}
		}
	}

	private static Ref refForWrite(RevWalk rw, Ref r) throws IOException {
		if (r.isSymbolic()) {
			return new SymbolicRef(r.getName(), new ObjectIdRef.Unpeeled(NEW,
					r.getTarget().getName(), null));
		}
		ObjectId newId = r.getObjectId();
		RevObject obj = rw.parseAny(newId);
		RevObject peel = null;
		if (obj instanceof RevTag) {
			peel = rw.peel(obj);
		}
		if (peel != null) {
			return new ObjectIdRef.PeeledTag(PACKED, r.getName(), newId,
					peel.copy());
		}
		return new ObjectIdRef.PeeledNonTag(PACKED, r.getName(), newId);
	}

	/**
	 * @param repo
	 *            the repository
	 * @param refstackName
	 *            the filename for the stack
	 * @param writeLogs
	 *            whether to write reflogs
	 * @return a reftable based RefDB from an existing repository.
	 * @throws IOException
	 *             on IO error
	 */
	public static FileReftableDatabase convertFrom(FileRepository repo,
			File refstackName, boolean writeLogs) throws IOException {
		FileReftableDatabase newDb = null;
		try {
			File reftableDir = new File(repo.getDirectory(), Constants.REFTABLE);
			if (!reftableDir.isDirectory()) {
				reftableDir.mkdir();
			}

			try (FileReftableStack stack = new FileReftableStack(refstackName,
					reftableDir, null, () -> repo.getConfig())) {
				stack.addReftable(rw -> writeConvertTable(repo, rw, writeLogs));
			}
			refstackName = null;
		} finally {
			if (refstackName != null) {
				refstackName.delete();
			}
		}
		return newDb;
	}
}

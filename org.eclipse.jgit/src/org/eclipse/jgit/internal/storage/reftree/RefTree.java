/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.reftree;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.Constants.R_REFS;
import static org.eclipse.jgit.lib.Constants.encode;
import static org.eclipse.jgit.lib.FileMode.GITLINK;
import static org.eclipse.jgit.lib.FileMode.SYMLINK;
import static org.eclipse.jgit.lib.FileMode.TYPE_GITLINK;
import static org.eclipse.jgit.lib.FileMode.TYPE_SYMLINK;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;
import static org.eclipse.jgit.lib.Ref.Storage.PACKED;
import static org.eclipse.jgit.lib.RefDatabase.MAX_SYMBOLIC_REF_DEPTH;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.LOCK_FAILURE;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.DeletePath;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.DirCacheNameConflictException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Tree of references in the reference graph.
 * <p>
 * The root corresponds to the {@code "refs/"} subdirectory, for example the
 * default reference {@code "refs/heads/master"} is stored at path
 * {@code "heads/master"} in a {@code RefTree}.
 * <p>
 * Normal references are stored as {@link org.eclipse.jgit.lib.FileMode#GITLINK}
 * tree entries. The ObjectId in the tree entry is the ObjectId the reference
 * refers to.
 * <p>
 * Symbolic references are stored as
 * {@link org.eclipse.jgit.lib.FileMode#SYMLINK} entries, with the blob storing
 * the name of the target reference.
 * <p>
 * Annotated tags also store the peeled object using a {@code GITLINK} entry
 * with the suffix <code>" ^"</code> (space carrot), for example
 * {@code "tags/v1.0"} stores the annotated tag object, while
 * <code>"tags/v1.0 ^"</code> stores the commit the tag annotates.
 * <p>
 * {@code HEAD} is a special case and stored as {@code "..HEAD"}.
 */
public class RefTree {
	/** Suffix applied to GITLINK to indicate its the peeled value of a tag. */
	public static final String PEELED_SUFFIX = " ^"; //$NON-NLS-1$
	static final String ROOT_DOTDOT = ".."; //$NON-NLS-1$

	/**
	 * Create an empty reference tree.
	 *
	 * @return a new empty reference tree.
	 */
	public static RefTree newEmptyTree() {
		return new RefTree(DirCache.newInCore());
	}

	/**
	 * Load a reference tree.
	 *
	 * @param reader
	 *            reader to scan the reference tree with.
	 * @param tree
	 *            the tree to read.
	 * @return the ref tree read from the commit.
	 * @throws java.io.IOException
	 *             the repository cannot be accessed through the reader.
	 * @throws org.eclipse.jgit.errors.CorruptObjectException
	 *             a tree object is corrupt and cannot be read.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             a tree object wasn't actually a tree.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             a reference tree object doesn't exist.
	 */
	public static RefTree read(ObjectReader reader, RevTree tree)
			throws MissingObjectException, IncorrectObjectTypeException,
			CorruptObjectException, IOException {
		return new RefTree(DirCache.read(reader, tree));
	}

	private DirCache contents;
	private Map<ObjectId, String> pendingBlobs;

	private RefTree(DirCache dc) {
		this.contents = dc;
	}

	/**
	 * Read one reference.
	 * <p>
	 * References are always returned peeled
	 * ({@link org.eclipse.jgit.lib.Ref#isPeeled()} is true). If the reference
	 * points to an annotated tag, the returned reference will be peeled and
	 * contain {@link org.eclipse.jgit.lib.Ref#getPeeledObjectId()}.
	 * <p>
	 * If the reference is a symbolic reference and the chain depth is less than
	 * {@link org.eclipse.jgit.lib.RefDatabase#MAX_SYMBOLIC_REF_DEPTH} the
	 * returned reference is resolved. If the chain depth is longer, the
	 * symbolic reference is returned without resolving.
	 *
	 * @param reader
	 *            to access objects necessary to read the requested reference.
	 * @param name
	 *            name of the reference to read.
	 * @return the reference; null if it does not exist.
	 * @throws java.io.IOException
	 *             cannot read a symbolic reference target.
	 */
	@Nullable
	public Ref exactRef(ObjectReader reader, String name) throws IOException {
		Ref r = readRef(reader, name);
		if (r == null) {
			return null;
		} else if (r.isSymbolic()) {
			return resolve(reader, r, 0);
		}

		DirCacheEntry p = contents.getEntry(peeledPath(name));
		if (p != null && p.getRawMode() == TYPE_GITLINK) {
			return new ObjectIdRef.PeeledTag(PACKED, r.getName(),
					r.getObjectId(), p.getObjectId());
		}
		return r;
	}

	private Ref readRef(ObjectReader reader, String name) throws IOException {
		DirCacheEntry e = contents.getEntry(refPath(name));
		return e != null ? toRef(reader, e, name) : null;
	}

	private Ref toRef(ObjectReader reader, DirCacheEntry e, String name)
			throws IOException {
		int mode = e.getRawMode();
		if (mode == TYPE_GITLINK) {
			ObjectId id = e.getObjectId();
			return new ObjectIdRef.PeeledNonTag(PACKED, name, id);
		}

		if (mode == TYPE_SYMLINK) {
			ObjectId id = e.getObjectId();
			String n = pendingBlobs != null ? pendingBlobs.get(id) : null;
			if (n == null) {
				byte[] bin = reader.open(id, OBJ_BLOB).getCachedBytes();
				n = RawParseUtils.decode(bin);
			}
			Ref dst = new ObjectIdRef.Unpeeled(NEW, n, null);
			return new SymbolicRef(name, dst);
		}

		return null; // garbage file or something; not a reference.
	}

	private Ref resolve(ObjectReader reader, Ref ref, int depth)
			throws IOException {
		if (ref.isSymbolic() && depth < MAX_SYMBOLIC_REF_DEPTH) {
			Ref r = readRef(reader, ref.getTarget().getName());
			if (r == null) {
				return ref;
			}
			Ref dst = resolve(reader, r, depth + 1);
			return new SymbolicRef(ref.getName(), dst);
		}
		return ref;
	}

	/**
	 * Attempt a batch of commands against this RefTree.
	 * <p>
	 * The batch is applied atomically, either all commands apply at once, or
	 * they all reject and the RefTree is left unmodified.
	 * <p>
	 * On success (when this method returns {@code true}) the command results
	 * are left as-is (probably {@code NOT_ATTEMPTED}). Result fields are set
	 * only when this method returns {@code false} to indicate failure.
	 *
	 * @param cmdList
	 *            to apply. All commands should still have result NOT_ATTEMPTED.
	 * @return true if the commands applied; false if they were rejected.
	 */
	public boolean apply(Collection<Command> cmdList) {
		try {
			DirCacheEditor ed = contents.editor();
			for (Command cmd : cmdList) {
				if (!isValidRef(cmd)) {
					cmd.setResult(REJECTED_OTHER_REASON,
							JGitText.get().funnyRefname);
					Command.abort(cmdList, null);
					return false;
				}
				apply(ed, cmd);
			}
			ed.finish();
			return true;
		} catch (DirCacheNameConflictException e) {
			String r1 = refName(e.getPath1());
			String r2 = refName(e.getPath2());
			for (Command cmd : cmdList) {
				if (r1.equals(cmd.getRefName())
						|| r2.equals(cmd.getRefName())) {
					cmd.setResult(LOCK_FAILURE);
					break;
				}
			}
			Command.abort(cmdList, null);
			return false;
		} catch (LockFailureException e) {
			Command.abort(cmdList, null);
			return false;
		}
	}

	private static boolean isValidRef(Command cmd) {
		String n = cmd.getRefName();
		return HEAD.equals(n) || Repository.isValidRefName(n);
	}

	private void apply(DirCacheEditor ed, Command cmd) {
		String path = refPath(cmd.getRefName());
		Ref oldRef = cmd.getOldRef();
		final Ref newRef = cmd.getNewRef();

		if (newRef == null) {
			checkRef(contents.getEntry(path), cmd);
			ed.add(new DeletePath(path));
			cleanupPeeledRef(ed, oldRef);
			return;
		}

		if (newRef.isSymbolic()) {
			final String dst = newRef.getTarget().getName();
			ed.add(new PathEdit(path) {
				@Override
				public void apply(DirCacheEntry ent) {
					checkRef(ent, cmd);
					ObjectId id = Command.symref(dst);
					ent.setFileMode(SYMLINK);
					ent.setObjectId(id);
					if (pendingBlobs == null) {
						pendingBlobs = new HashMap<>(4);
					}
					pendingBlobs.put(id, dst);
				}
			}.setReplace(false));
			cleanupPeeledRef(ed, oldRef);
			return;
		}

		ed.add(new PathEdit(path) {
			@Override
			public void apply(DirCacheEntry ent) {
				checkRef(ent, cmd);
				ent.setFileMode(GITLINK);
				ent.setObjectId(newRef.getObjectId());
			}
		}.setReplace(false));

		if (newRef.getPeeledObjectId() != null) {
			ed.add(new PathEdit(peeledPath(newRef.getName())) {
				@Override
				public void apply(DirCacheEntry ent) {
					ent.setFileMode(GITLINK);
					ent.setObjectId(newRef.getPeeledObjectId());
				}
			}.setReplace(false));
		} else {
			cleanupPeeledRef(ed, oldRef);
		}
	}

	private static void checkRef(@Nullable DirCacheEntry ent, Command cmd) {
		if (!cmd.checkRef(ent)) {
			cmd.setResult(LOCK_FAILURE);
			throw new LockFailureException();
		}
	}

	private static void cleanupPeeledRef(DirCacheEditor ed, Ref ref) {
		if (ref != null && !ref.isSymbolic()
				&& (!ref.isPeeled() || ref.getPeeledObjectId() != null)) {
			ed.add(new DeletePath(peeledPath(ref.getName())));
		}
	}

	/**
	 * Convert a path name in a RefTree to the reference name known by Git.
	 *
	 * @param path
	 *            name read from the RefTree structure, for example
	 *            {@code "heads/master"}.
	 * @return reference name for the path, {@code "refs/heads/master"}.
	 */
	public static String refName(String path) {
		if (path.startsWith(ROOT_DOTDOT)) {
			return path.substring(2);
		}
		return R_REFS + path;
	}

	static String refPath(String name) {
		if (name.startsWith(R_REFS)) {
			return name.substring(R_REFS.length());
		}
		return ROOT_DOTDOT + name;
	}

	private static String peeledPath(String name) {
		return refPath(name) + PEELED_SUFFIX;
	}

	/**
	 * Write this reference tree.
	 *
	 * @param inserter
	 *            inserter to use when writing trees to the object database.
	 *            Caller is responsible for flushing the inserter before trying
	 *            to read the objects, or exposing them through a reference.
	 * @return the top level tree.
	 * @throws java.io.IOException
	 *             a tree could not be written.
	 */
	public ObjectId writeTree(ObjectInserter inserter) throws IOException {
		if (pendingBlobs != null) {
			for (String s : pendingBlobs.values()) {
				inserter.insert(OBJ_BLOB, encode(s));
			}
			pendingBlobs = null;
		}
		return contents.writeTree(inserter);
	}

	/**
	 * Create a deep copy of this RefTree.
	 *
	 * @return a deep copy of this RefTree.
	 */
	public RefTree copy() {
		RefTree r = new RefTree(DirCache.newInCore());
		DirCacheBuilder b = r.contents.builder();
		for (int i = 0; i < contents.getEntryCount(); i++) {
			b.add(new DirCacheEntry(contents.getEntry(i)));
		}
		b.finish();
		if (pendingBlobs != null) {
			r.pendingBlobs = new HashMap<>(pendingBlobs);
		}
		return r;
	}

	private static class LockFailureException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
}

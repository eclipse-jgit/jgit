/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2006-2010, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.lib;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.FS;

/**
 * Represents a Git repository.
 * <p>
 * A repository holds all objects and refs used for managing source code (could
 * by any type of file, but source code is what SCM's are typically used for).
 * <p>
 * This class is thread-safe.
 */
public abstract class Repository {
	static private final List<RepositoryListener> allListeners = new Vector<RepositoryListener>(); // thread safe

	/** Initialize a new repository instance. */
	protected Repository() {
		// Empty constructor, defined protected to require subclassing.
	}

	/**
	 * Create a new Git repository.
	 * <p>
	 * Repository with working tree is created using this method. This method is
	 * the same as {@code create(false)}.
	 *
	 * @throws IOException
	 * @see #create(boolean)
	 */
	public void create() throws IOException {
		create(false);
	}

	/**
	 * Create a new Git repository initializing the necessary files and
	 * directories.
	 *
	 * @param bare
	 *            if true, a bare repository is created.
	 *
	 * @throws IOException
	 *             in case of IO problem
	 */
	public abstract void create(boolean bare) throws IOException;

	/**
	 * @return GIT_DIR
	 */
	public abstract File getDirectory();

	/**
	 * @return the directory containing the objects owned by this repository.
	 */
	public abstract File getObjectsDirectory();

	/**
	 * @return the object database which stores this repository's data.
	 */
	public abstract ObjectDatabase getObjectDatabase();

	/** @return a new inserter to create objects in {@link #getObjectDatabase()} */
	public ObjectInserter newObjectInserter() {
		return getObjectDatabase().newInserter();
	}

	/** @return a new inserter to create objects in {@link #getObjectDatabase()} */
	public ObjectReader newObjectReader() {
		return getObjectDatabase().newReader();
	}

	/** @return the reference database which stores the reference namespace. */
	public abstract RefDatabase getRefDatabase();

	/**
	 * @return the configuration of this repository
	 */
	public abstract RepositoryConfig getConfig();

	/**
	 * @return the used file system abstraction
	 */
	public abstract FS getFS();

	/**
	 * Construct a filename where the loose object having a specified SHA-1
	 * should be stored. If the object is stored in a shared repository the path
	 * to the alternative repo will be returned. If the object is not yet store
	 * a usable path in this repo will be returned. It is assumed that callers
	 * will look for objects in a pack first.
	 *
	 * @param objectId
	 * @return suggested file name
	 */
	public abstract File toFile(AnyObjectId objectId);

	/**
	 * @param objectId
	 * @return true if the specified object is stored in this repo or any of the
	 *         known shared repositories.
	 */
	public boolean hasObject(AnyObjectId objectId) {
		try {
			return getObjectDatabase().hasObject(objectId);
		} catch (IOException e) {
			// Legacy API, assume error means "no"
			return false;
		}
	}

	/**
	 * @param id
	 *            SHA-1 of an object.
	 *
	 * @return a {@link ObjectLoader} for accessing the data of the named
	 *         object, or null if the object does not exist.
	 * @throws IOException
	 */
	public ObjectLoader openObject(final AnyObjectId id)
			throws IOException {
		try {
			return getObjectDatabase().openObject(id);
		} catch (MissingObjectException notFound) {
			// Legacy API, return null
			return null;
		}
	}

	/**
	 * @param curs
	 *            temporary working space associated with the calling thread.
	 * @param id
	 *            SHA-1 of an object.
	 *
	 * @return a {@link ObjectLoader} for accessing the data of the named
	 *         object, or null if the object does not exist.
	 * @throws IOException
	 * @deprecated Use {code newObjectReader().open(id)}.
	 */
	@Deprecated
	public ObjectLoader openObject(ObjectReader curs, AnyObjectId id)
			throws IOException {
		try {
			return curs.openObject(id);
		} catch (MissingObjectException notFound) {
			return null;
		}
	}

	/**
	 * @param id
	 *            SHA'1 of a blob
	 * @return an {@link ObjectLoader} for accessing the data of a named blob
	 * @throws IOException
	 */
	public ObjectLoader openBlob(final ObjectId id) throws IOException {
		return openObject(id);
	}

	/**
	 * @param id
	 *            SHA'1 of a tree
	 * @return an {@link ObjectLoader} for accessing the data of a named tree
	 * @throws IOException
	 */
	public ObjectLoader openTree(final ObjectId id) throws IOException {
		return openObject(id);
	}

	/**
	 * Access a Commit object using a symbolic reference. This reference may
	 * be a SHA-1 or ref in combination with a number of symbols translating
	 * from one ref or SHA1-1 to another, such as HEAD^ etc.
	 *
	 * @param revstr a reference to a git commit object
	 * @return a Commit named by the specified string
	 * @throws IOException for I/O error or unexpected object type.
	 *
	 * @see #resolve(String)
	 */
	public Commit mapCommit(final String revstr) throws IOException {
		final ObjectId id = resolve(revstr);
		return id != null ? mapCommit(id) : null;
	}

	/**
	 * Access any type of Git object by id and
	 *
	 * @param id
	 *            SHA-1 of object to read
	 * @param refName optional, only relevant for simple tags
	 * @return The Git object if found or null
	 * @throws IOException
	 */
	public Object mapObject(final ObjectId id, final String refName) throws IOException {
		final ObjectLoader or = openObject(id);
		if (or == null)
			return null;
		final byte[] raw = or.getBytes();
		switch (or.getType()) {
		case Constants.OBJ_TREE:
			return makeTree(id, raw);

		case Constants.OBJ_COMMIT:
			return makeCommit(id, raw);

		case Constants.OBJ_TAG:
			return makeTag(id, refName, raw);

		case Constants.OBJ_BLOB:
			return raw;

		default:
			throw new IncorrectObjectTypeException(id,
				JGitText.get().incorrectObjectType_COMMITnorTREEnorBLOBnorTAG);
		}
	}

	/**
	 * Access a Commit by SHA'1 id.
	 * @param id
	 * @return Commit or null
	 * @throws IOException for I/O error or unexpected object type.
	 */
	public Commit mapCommit(final ObjectId id) throws IOException {
		final ObjectLoader or = openObject(id);
		if (or == null)
			return null;
		final byte[] raw = or.getBytes();
		if (Constants.OBJ_COMMIT == or.getType())
			return new Commit(this, id, raw);
		throw new IncorrectObjectTypeException(id, Constants.TYPE_COMMIT);
	}

	private Commit makeCommit(final ObjectId id, final byte[] raw) {
		Commit ret = new Commit(this, id, raw);
		return ret;
	}

	/**
	 * Access a Tree object using a symbolic reference. This reference may
	 * be a SHA-1 or ref in combination with a number of symbols translating
	 * from one ref or SHA1-1 to another, such as HEAD^{tree} etc.
	 *
	 * @param revstr a reference to a git commit object
	 * @return a Tree named by the specified string
	 * @throws IOException
	 *
	 * @see #resolve(String)
	 */
	public Tree mapTree(final String revstr) throws IOException {
		final ObjectId id = resolve(revstr);
		return id != null ? mapTree(id) : null;
	}

	/**
	 * Access a Tree by SHA'1 id.
	 * @param id
	 * @return Tree or null
	 * @throws IOException for I/O error or unexpected object type.
	 */
	public Tree mapTree(final ObjectId id) throws IOException {
		final ObjectLoader or = openObject(id);
		if (or == null)
			return null;
		final byte[] raw = or.getBytes();
		switch (or.getType()) {
		case Constants.OBJ_TREE:
			return new Tree(this, id, raw);

		case Constants.OBJ_COMMIT:
			return mapTree(ObjectId.fromString(raw, 5));

		default:
			throw new IncorrectObjectTypeException(id, Constants.TYPE_TREE);
		}
	}

	private Tree makeTree(final ObjectId id, final byte[] raw) throws IOException {
		Tree ret = new Tree(this, id, raw);
		return ret;
	}

	private Tag makeTag(final ObjectId id, final String refName, final byte[] raw) {
		Tag ret = new Tag(this, id, refName, raw);
		return ret;
	}

	/**
	 * Access a tag by symbolic name.
	 *
	 * @param revstr
	 * @return a Tag or null
	 * @throws IOException on I/O error or unexpected type
	 */
	public  Tag mapTag(String revstr) throws IOException {
		final ObjectId id = resolve(revstr);
		return id != null ? mapTag(revstr, id) : null;
	}

	/**
	 * Access a Tag by SHA'1 id
	 * @param refName
	 * @param id
	 * @return Commit or null
	 * @throws IOException for I/O error or unexpected object type.
	 */
	public Tag mapTag(final String refName, final ObjectId id) throws IOException {
		final ObjectLoader or = openObject(id);
		if (or == null)
			return null;
		final byte[] raw = or.getBytes();
		if (Constants.OBJ_TAG == or.getType())
			return new Tag(this, id, refName, raw);
		return new Tag(this, id, refName, null);
	}

	/**
	 * Create a command to update, create or delete a ref in this repository.
	 *
	 * @param ref
	 *            name of the ref the caller wants to modify.
	 * @return an update command. The caller must finish populating this command
	 *         and then invoke one of the update methods to actually make a
	 *         change.
	 * @throws IOException
	 *             a symbolic ref was passed in and could not be resolved back
	 *             to the base ref, as the symbolic ref could not be read.
	 */
	public RefUpdate updateRef(final String ref) throws IOException {
		return updateRef(ref, false);
	}

	/**
	 * Create a command to update, create or delete a ref in this repository.
	 *
	 * @param ref
	 *            name of the ref the caller wants to modify.
	 * @param detach
	 *            true to create a detached head
	 * @return an update command. The caller must finish populating this command
	 *         and then invoke one of the update methods to actually make a
	 *         change.
	 * @throws IOException
	 *             a symbolic ref was passed in and could not be resolved back
	 *             to the base ref, as the symbolic ref could not be read.
	 */
	public RefUpdate updateRef(final String ref, final boolean detach) throws IOException {
		return getRefDatabase().newUpdate(ref, detach);
	}

	/**
	 * Create a command to rename a ref in this repository
	 *
	 * @param fromRef
	 *            name of ref to rename from
	 * @param toRef
	 *            name of ref to rename to
	 * @return an update command that knows how to rename a branch to another.
	 * @throws IOException
	 *             the rename could not be performed.
	 *
	 */
	public RefRename renameRef(final String fromRef, final String toRef) throws IOException {
		return getRefDatabase().newRename(fromRef, toRef);
	}

	/**
	 * Parse a git revision string and return an object id.
	 *
	 * Currently supported is combinations of these.
	 * <ul>
	 * <li>SHA-1 - a SHA-1</li>
	 * <li>refs/... - a ref name</li>
	 * <li>ref^n - nth parent reference</li>
	 * <li>ref~n - distance via parent reference</li>
	 * <li>ref@{n} - nth version of ref</li>
	 * <li>ref^{tree} - tree references by ref</li>
	 * <li>ref^{commit} - commit references by ref</li>
	 * </ul>
	 *
	 * Not supported is:
	 * <ul>
	 * <li>timestamps in reflogs, ref@{full or relative timestamp}</li>
	 * <li>abbreviated SHA-1's</li>
	 * </ul>
	 *
	 * @param revstr
	 *            A git object references expression
	 * @return an ObjectId or null if revstr can't be resolved to any ObjectId
	 * @throws IOException
	 *             on serious errors
	 */
	public ObjectId resolve(final String revstr) throws IOException {
		char[] rev = revstr.toCharArray();
		RevObject ref = null;
		RevWalk rw = new RevWalk(this);
		for (int i = 0; i < rev.length; ++i) {
			switch (rev[i]) {
			case '^':
				if (ref == null) {
					ref = parseSimple(rw, new String(rev, 0, i));
					if (ref == null)
						return null;
				}
				if (i + 1 < rev.length) {
					switch (rev[i + 1]) {
					case '0':
					case '1':
					case '2':
					case '3':
					case '4':
					case '5':
					case '6':
					case '7':
					case '8':
					case '9':
						int j;
						ref = rw.parseCommit(ref);
						for (j = i + 1; j < rev.length; ++j) {
							if (!Character.isDigit(rev[j]))
								break;
						}
						String parentnum = new String(rev, i + 1, j - i - 1);
						int pnum;
						try {
							pnum = Integer.parseInt(parentnum);
						} catch (NumberFormatException e) {
							throw new RevisionSyntaxException(
									JGitText.get().invalidCommitParentNumber,
									revstr);
						}
						if (pnum != 0) {
							RevCommit commit = (RevCommit) ref;
							if (pnum > commit.getParentCount())
								ref = null;
							else
								ref = commit.getParent(pnum - 1);
						}
						i = j - 1;
						break;
					case '{':
						int k;
						String item = null;
						for (k = i + 2; k < rev.length; ++k) {
							if (rev[k] == '}') {
								item = new String(rev, i + 2, k - i - 2);
								break;
							}
						}
						i = k;
						if (item != null)
							if (item.equals("tree")) {
								ref = rw.parseTree(ref);
							} else if (item.equals("commit")) {
								ref = rw.parseCommit(ref);
							} else if (item.equals("blob")) {
								ref = rw.parseAny(ref);
								ref = peelTag(rw, ref);
								if (!(ref instanceof RevBlob))
									throw new IncorrectObjectTypeException(ref,
											Constants.TYPE_BLOB);
							} else if (item.equals("")) {
								ref = rw.parseAny(ref);
								ref = peelTag(rw, ref);
							} else
								throw new RevisionSyntaxException(revstr);
						else
							throw new RevisionSyntaxException(revstr);
						break;
					default:
						ref = rw.parseAny(ref);
						if (ref instanceof RevCommit) {
							RevCommit commit = ((RevCommit) ref);
							if (commit.getParentCount() == 0)
								ref = null;
							else
								ref = commit.getParent(0);
						} else
							throw new IncorrectObjectTypeException(ref,
									Constants.TYPE_COMMIT);

					}
				} else {
					ref = rw.parseAny(ref);
					ref = peelTag(rw, ref);
					if (ref instanceof RevCommit) {
						RevCommit commit = ((RevCommit) ref);
						if (commit.getParentCount() == 0)
							ref = null;
						else
							ref = commit.getParent(0);
					} else
						throw new IncorrectObjectTypeException(ref,
								Constants.TYPE_COMMIT);
				}
				break;
			case '~':
				if (ref == null) {
					ref = parseSimple(rw, new String(rev, 0, i));
					if (ref == null)
						return null;
				}
				ref = peelTag(rw, ref);
				if (!(ref instanceof RevCommit))
					throw new IncorrectObjectTypeException(ref,
							Constants.TYPE_COMMIT);
				int l;
				for (l = i + 1; l < rev.length; ++l) {
					if (!Character.isDigit(rev[l]))
						break;
				}
				String distnum = new String(rev, i + 1, l - i - 1);
				int dist;
				try {
					dist = Integer.parseInt(distnum);
				} catch (NumberFormatException e) {
					throw new RevisionSyntaxException(
							JGitText.get().invalidAncestryLength, revstr);
				}
				while (dist > 0) {
					RevCommit commit = (RevCommit) ref;
					if (commit.getParentCount() == 0) {
						ref = null;
						break;
					}
					commit = commit.getParent(0);
					rw.parseHeaders(commit);
					ref = commit;
					--dist;
				}
				i = l - 1;
				break;
			case '@':
				int m;
				String time = null;
				for (m = i + 2; m < rev.length; ++m) {
					if (rev[m] == '}') {
						time = new String(rev, i + 2, m - i - 2);
						break;
					}
				}
				if (time != null)
					throw new RevisionSyntaxException(
							JGitText.get().reflogsNotYetSupportedByRevisionParser,
							revstr);
				i = m - 1;
				break;
			default:
				if (ref != null)
					throw new RevisionSyntaxException(revstr);
			}
		}
		return ref != null ? ref.copy() : resolveSimple(revstr);
	}

	private RevObject parseSimple(RevWalk rw, String revstr) throws IOException {
		ObjectId id = resolveSimple(revstr);
		return id != null ? rw.parseAny(id) : null;
	}

	private ObjectId resolveSimple(final String revstr) throws IOException {
		if (ObjectId.isId(revstr))
			return ObjectId.fromString(revstr);
		final Ref r = getRefDatabase().getRef(revstr);
		return r != null ? r.getObjectId() : null;
	}

	private RevObject peelTag(RevWalk rw, RevObject ref)
			throws MissingObjectException, IOException {
		while (ref instanceof RevTag)
			ref = rw.parseAny(((RevTag) ref).getObject());
		return ref;
	}

	/** Increment the use counter by one, requiring a matched {@link #close()}. */
	public abstract void incrementOpen();

	/**
	 * Close all resources used by this repository
	 */
	public abstract void close();

	/**
	 * Add a single existing pack to the list of available pack files.
	 *
	 * @param pack
	 *            path of the pack file to open.
	 * @param idx
	 *            path of the corresponding index file.
	 * @throws IOException
	 *             index file could not be opened, read, or is not recognized as
	 *             a Git pack file index.
	 */
	public abstract void openPack(File pack, File idx) throws IOException;

	/**
	 * Get the name of the reference that {@code HEAD} points to.
	 * <p>
	 * This is essentially the same as doing:
	 *
	 * <pre>
	 * return getRef(Constants.HEAD).getTarget().getName()
	 * </pre>
	 *
	 * Except when HEAD is detached, in which case this method returns the
	 * current ObjectId in hexadecimal string format.
	 *
	 * @return name of current branch (for example {@code refs/heads/master}) or
	 *         an ObjectId in hex format if the current branch is detached.
	 * @throws IOException
	 */
	public String getFullBranch() throws IOException {
		Ref head = getRef(Constants.HEAD);
		if (head == null)
			return null;
		if (head.isSymbolic())
			return head.getTarget().getName();
		if (head.getObjectId() != null)
			return head.getObjectId().name();
		return null;
	}

	/**
	 * Get the short name of the current branch that {@code HEAD} points to.
	 * <p>
	 * This is essentially the same as {@link #getFullBranch()}, except the
	 * leading prefix {@code refs/heads/} is removed from the reference before
	 * it is returned to the caller.
	 *
	 * @return name of current branch (for example {@code master}), or an
	 *         ObjectId in hex format if the current branch is detached.
	 * @throws IOException
	 */
	public String getBranch() throws IOException {
		String name = getFullBranch();
		if (name != null)
			return shortenRefName(name);
		return name;
	}

	/**
	 * Objects known to exist but not expressed by {@link #getAllRefs()}.
	 * <p>
	 * When a repository borrows objects from another repository, it can
	 * advertise that it safely has that other repository's references, without
	 * exposing any other details about the other repository.  This may help
	 * a client trying to push changes avoid pushing more than it needs to.
	 *
	 * @return unmodifiable collection of other known objects.
	 */
	public Set<ObjectId> getAdditionalHaves() {
		return Collections.emptySet();
	}

	/**
	 * Get a ref by name.
	 *
	 * @param name
	 *            the name of the ref to lookup. May be a short-hand form, e.g.
	 *            "master" which is is automatically expanded to
	 *            "refs/heads/master" if "refs/heads/master" already exists.
	 * @return the Ref with the given name, or null if it does not exist
	 * @throws IOException
	 */
	public Ref getRef(final String name) throws IOException {
		return getRefDatabase().getRef(name);
	}

	/**
	 * @return mutable map of all known refs (heads, tags, remotes).
	 */
	public Map<String, Ref> getAllRefs() {
		try {
			return getRefDatabase().getRefs(RefDatabase.ALL);
		} catch (IOException e) {
			return new HashMap<String, Ref>();
		}
	}

	/**
	 * @return mutable map of all tags; key is short tag name ("v1.0") and value
	 *         of the entry contains the ref with the full tag name
	 *         ("refs/tags/v1.0").
	 */
	public Map<String, Ref> getTags() {
		try {
			return getRefDatabase().getRefs(Constants.R_TAGS);
		} catch (IOException e) {
			return new HashMap<String, Ref>();
		}
	}

	/**
	 * Peel a possibly unpeeled reference to an annotated tag.
	 * <p>
	 * If the ref cannot be peeled (as it does not refer to an annotated tag)
	 * the peeled id stays null, but {@link Ref#isPeeled()} will be true.
	 *
	 * @param ref
	 *            The ref to peel
	 * @return <code>ref</code> if <code>ref.isPeeled()</code> is true; else a
	 *         new Ref object representing the same data as Ref, but isPeeled()
	 *         will be true and getPeeledObjectId will contain the peeled object
	 *         (or null).
	 */
	public Ref peel(final Ref ref) {
		try {
			return getRefDatabase().peel(ref);
		} catch (IOException e) {
			// Historical accident; if the reference cannot be peeled due
			// to some sort of repository access problem we claim that the
			// same as if the reference was not an annotated tag.
			return ref;
		}
	}

	/**
	 * @return a map with all objects referenced by a peeled ref.
	 */
	public Map<AnyObjectId, Set<Ref>> getAllRefsByPeeledObjectId() {
		Map<String, Ref> allRefs = getAllRefs();
		Map<AnyObjectId, Set<Ref>> ret = new HashMap<AnyObjectId, Set<Ref>>(allRefs.size());
		for (Ref ref : allRefs.values()) {
			ref = peel(ref);
			AnyObjectId target = ref.getPeeledObjectId();
			if (target == null)
				target = ref.getObjectId();
			// We assume most Sets here are singletons
			Set<Ref> oset = ret.put(target, Collections.singleton(ref));
			if (oset != null) {
				// that was not the case (rare)
				if (oset.size() == 1) {
					// Was a read-only singleton, we must copy to a new Set
					oset = new HashSet<Ref>(oset);
				}
				ret.put(target, oset);
				oset.add(ref);
			}
		}
		return ret;
	}

	/**
	 * @return a representation of the index associated with this
	 *         {@link Repository}
	 * @throws IOException
	 *             if the index can not be read
	 * @throws IllegalStateException
	 *             if this is bare (see {@link #isBare()})
	 */
	public abstract GitIndex getIndex() throws IOException,
			IllegalStateException;

	/**
	 * @return the index file location
	 * @throws IllegalStateException
	 *             if this is bare (see {@link #isBare()})
	 */
	public abstract File getIndexFile() throws IllegalStateException;

	static byte[] gitInternalSlash(byte[] bytes) {
		if (File.separatorChar == '/')
			return bytes;
		for (int i=0; i<bytes.length; ++i)
			if (bytes[i] == File.separatorChar)
				bytes[i] = '/';
		return bytes;
	}

	/**
	 * @return an important state
	 */
	public abstract RepositoryState getRepositoryState();

	/**
	 * Check validity of a ref name. It must not contain character that has
	 * a special meaning in a Git object reference expression. Some other
	 * dangerous characters are also excluded.
	 *
	 * For portability reasons '\' is excluded
	 *
	 * @param refName
	 *
	 * @return true if refName is a valid ref name
	 */
	public static boolean isValidRefName(final String refName) {
		final int len = refName.length();
		if (len == 0)
			return false;
		if (refName.endsWith(LockFile.SUFFIX))
			return false;

		int components = 1;
		char p = '\0';
		for (int i = 0; i < len; i++) {
			final char c = refName.charAt(i);
			if (c <= ' ')
				return false;
			switch (c) {
			case '.':
				switch (p) {
				case '\0': case '/': case '.':
					return false;
				}
				if (i == len -1)
					return false;
				break;
			case '/':
				if (i == 0 || i == len - 1)
					return false;
				components++;
				break;
			case '{':
				if (p == '@')
					return false;
				break;
			case '~': case '^': case ':':
			case '?': case '[': case '*':
			case '\\':
				return false;
			}
			p = c;
		}
		return components > 1;
	}

	/**
	 * Strip work dir and return normalized repository path.
	 *
	 * @param workDir Work dir
	 * @param file File whose path shall be stripped of its workdir
	 * @return normalized repository relative path or the empty
	 *         string if the file is not relative to the work directory.
	 */
	public static String stripWorkDir(File workDir, File file) {
		final String filePath = file.getPath();
		final String workDirPath = workDir.getPath();

		if (filePath.length() <= workDirPath.length() ||
		    filePath.charAt(workDirPath.length()) != File.separatorChar ||
		    !filePath.startsWith(workDirPath)) {
			File absWd = workDir.isAbsolute() ? workDir : workDir.getAbsoluteFile();
			File absFile = file.isAbsolute() ? file : file.getAbsoluteFile();
			if (absWd == workDir && absFile == file)
				return "";
			return stripWorkDir(absWd, absFile);
		}

		String relName = filePath.substring(workDirPath.length() + 1);
		if (File.separatorChar != '/')
			relName = relName.replace(File.separatorChar, '/');
		return relName;
	}

	/**
	 * @return the "bare"-ness of this Repository
	 */
	public abstract boolean isBare();

	/**
	 * @return the workdir file, i.e. where the files are checked out
	 * @throws IllegalStateException
	 *             if the repository is "bare"
	 */
	public abstract File getWorkDir() throws IllegalStateException;

	/**
	 * Override default workdir
	 *
	 * @param workTree
	 *            the work tree directory
	 */
	public abstract void setWorkDir(File workTree);

	/**
	 * Register a {@link RepositoryListener} which will be notified
	 * when ref changes are detected.
	 *
	 * @param l
	 */
	public abstract void addRepositoryChangedListener(RepositoryListener l);

	/**
	 * Remove a registered {@link RepositoryListener}
	 * @param l
	 */
	public abstract void removeRepositoryChangedListener(RepositoryListener l);

	/**
	 * Register a global {@link RepositoryListener} which will be notified
	 * when a ref changes in any repository are detected.
	 *
	 * @param l
	 */
	public static void addAnyRepositoryChangedListener(final RepositoryListener l) {
		allListeners.add(l);
	}

	/**
	 * Remove a globally registered {@link RepositoryListener}
	 * @param l
	 */
	public static void removeAnyRepositoryChangedListener(final RepositoryListener l) {
		allListeners.remove(l);
	}

	/** @return a mutable copy of the known global listeners. */
	protected List<RepositoryListener> getAnyRepositoryChangedListeners() {
		synchronized (allListeners) {
			return new ArrayList<RepositoryListener>(allListeners);
		}
	}

	abstract void fireRefsChanged();

	abstract void fireIndexChanged();

	/**
	 * Force a scan for changed refs.
	 *
	 * @throws IOException
	 */
	public abstract void scanForRepoChanges() throws IOException;

	/**
	 * @param refName
	 *
	 * @return a more user friendly ref name
	 */
	public String shortenRefName(String refName) {
		if (refName.startsWith(Constants.R_HEADS))
			return refName.substring(Constants.R_HEADS.length());
		if (refName.startsWith(Constants.R_TAGS))
			return refName.substring(Constants.R_TAGS.length());
		if (refName.startsWith(Constants.R_REMOTES))
			return refName.substring(Constants.R_REMOTES.length());
		return refName;
	}

	/**
	 * @param refName
	 * @return a {@link ReflogReader} for the supplied refname, or null if the
	 *         named ref does not exist.
	 * @throws IOException
	 *             the ref could not be accessed.
	 */
	public abstract ReflogReader getReflogReader(String refName)
			throws IOException;

	/**
	 * Return the information stored in the file $GIT_DIR/MERGE_MSG. In this
	 * file operations triggering a merge will store a template for the commit
	 * message of the merge commit.
	 *
	 * @return a String containing the content of the MERGE_MSG file or
	 *         {@code null} if this file doesn't exist
	 * @throws IOException
	 */
	public abstract String readMergeCommitMsg() throws IOException;

	/**
	 * Return the information stored in the file $GIT_DIR/MERGE_HEAD. In this
	 * file operations triggering a merge will store the IDs of all heads which
	 * should be merged together with HEAD.
	 *
	 * @return a list of {@link Commit}s which IDs are listed in the MERGE_HEAD
	 *         file or {@code null} if this file doesn't exist. Also if the file
	 *         exists but is empty {@code null} will be returned
	 * @throws IOException
	 */
	public abstract List<ObjectId> readMergeHeads() throws IOException;
}

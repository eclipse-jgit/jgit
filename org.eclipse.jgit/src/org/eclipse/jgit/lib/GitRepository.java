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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Represents a Git repository.
 * <p>
 * A repository holds all objects and refs used for managing source code (could
 * by any type of file, but source code is what SCM's are typically used for).
 * <p>
 * This class is thread-safe.
 */
public abstract class GitRepository {
	private final AtomicInteger useCnt = new AtomicInteger(1);

	/** Initialize a partial repository, to be further configured. */
	protected GitRepository() {
		// Do nothing.
	}

	/** Increment the use counter by one, requiring a matched {@link #close()}. */
	public void incrementOpen() {
		useCnt.incrementAndGet();
	}

	/** Close all resources used by this repository. */
	public void close() {
		if (useCnt.decrementAndGet() == 0) {
			getObjectDatabase().close();
			getRefDatabase().close();
		}
	}

	/** @return the object database which stores this repository's data. */
	public abstract ObjectDatabase getObjectDatabase();

	/** @return a new inserter to create objects in {@link #getObjectDatabase()} */
	public ObjectInserter newObjectInserter() {
		return getObjectDatabase().newInserter();
	}

	/** @return the reference database which stores the reference namespace. */
	public abstract RefDatabase getRefDatabase();

	/** @return the configuration of this repository. */
	public abstract Config getConfig();

	/**
	 * @param objectId
	 * @return true if the specified object is stored in this repository or any
	 *         of the known shared repositories.
	 */
	public boolean hasObject(final AnyObjectId objectId) {
		return getObjectDatabase().hasObject(objectId);
	}

	/**
	 * @param id
	 *            SHA-1 of an object.
	 *
	 * @return a {@link ObjectLoader} for accessing the data of the named
	 *         object, or null if the object does not exist.
	 * @throws IOException
	 */
	public ObjectLoader openObject(final AnyObjectId id) throws IOException {
		final WindowCursor wc = new WindowCursor();
		try {
			return openObject(wc, id);
		} finally {
			wc.release();
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
	 */
	public ObjectLoader openObject(final WindowCursor curs, final AnyObjectId id)
			throws IOException {
		return getObjectDatabase().openObject(curs, id);
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
	public RefUpdate updateRef(final String ref, final boolean detach)
			throws IOException {
		return getRefDatabase().newUpdate(ref, detach);
	}

	/**
	 * Create a command to rename a ref in this repository.
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
	public RefRename renameRef(final String fromRef, final String toRef)
			throws IOException {
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
	 * Get all references known to this repository.
	 *
	 * @return mutable map of all known refs (heads, tags, remotes).
	 * @throws IOException
	 *             the reference database cannot be read.
	 */
	public Map<String, Ref> getAllRefs() throws IOException {
		return getRefDatabase().getRefs(RefDatabase.ALL);
	}

	/**
	 * Get all tags known to this repository.
	 * <p>
	 * This is a subset of {@link #getAllRefs()}, containing only those names
	 * that begin with {@code refs/tags/}.
	 *
	 * @return mutable map of all tags; key is short tag name ("v1.0") and value
	 *         of the entry contains the ref with the full tag name
	 *         ("refs/tags/v1.0").
	 * @throws IOException
	 *             the reference database cannot be read.
	 */
	public Map<String, Ref> getTags() throws IOException {
		return getRefDatabase().getRefs(Constants.R_TAGS);
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
	 * @throws IOException
	 *             the reference cannot be resolved, as the store cannot be
	 *             read.
	 */
	public Ref peel(final Ref ref) throws IOException {
		return getRefDatabase().peel(ref);
	}

	/**
	 * @return a map with all objects referenced by a peeled ref.
	 * @throws IOException
	 *             the reference database cannot be read.
	 */
	public Map<ObjectId, Set<Ref>> getAllRefsByPeeledObjectId()
			throws IOException {
		Map<ObjectId, Set<Ref>> ret = new HashMap<ObjectId, Set<Ref>>();
		for (Ref ref : getAllRefs().values()) {
			ref = peel(ref);
			ObjectId target = ref.getPeeledObjectId();
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
}

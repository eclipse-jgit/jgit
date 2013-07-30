/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2006-2010, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2012, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2012, Daniel Megert <daniel_megert@ch.ibm.com>
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.events.IndexChangedEvent;
import org.eclipse.jgit.events.IndexChangedListener;
import org.eclipse.jgit.events.ListenerList;
import org.eclipse.jgit.events.RepositoryEvent;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.io.SafeBufferedOutputStream;

/**
 * Represents a Git repository.
 * <p>
 * A repository holds all objects and refs used for managing source code (could
 * be any type of file, but source code is what SCM's are typically used for).
 * <p>
 * This class is thread-safe.
 */
public abstract class Repository {
	private static final ListenerList globalListeners = new ListenerList();

	/** @return the global listener list observing all events in this JVM. */
	public static ListenerList getGlobalListenerList() {
		return globalListeners;
	}

	private final AtomicInteger useCnt = new AtomicInteger(1);

	/** Metadata directory holding the repository's critical files. */
	private final File gitDir;

	/** File abstraction used to resolve paths. */
	private final FS fs;

	private final ListenerList myListeners = new ListenerList();

	/** If not bare, the top level directory of the working files. */
	private final File workTree;

	/** If not bare, the index file caching the working file states. */
	private final File indexFile;

	/**
	 * Initialize a new repository instance.
	 *
	 * @param options
	 *            options to configure the repository.
	 */
	protected Repository(final BaseRepositoryBuilder options) {
		gitDir = options.getGitDir();
		fs = options.getFS();
		workTree = options.getWorkTree();
		indexFile = options.getIndexFile();
	}

	/** @return listeners observing only events on this repository. */
	public ListenerList getListenerList() {
		return myListeners;
	}

	/**
	 * Fire an event to all registered listeners.
	 * <p>
	 * The source repository of the event is automatically set to this
	 * repository, before the event is delivered to any listeners.
	 *
	 * @param event
	 *            the event to deliver.
	 */
	public void fireEvent(RepositoryEvent<?> event) {
		event.setRepository(this);
		myListeners.dispatch(event);
		globalListeners.dispatch(event);
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
	 *            if true, a bare repository (a repository without a working
	 *            directory) is created.
	 * @throws IOException
	 *             in case of IO problem
	 */
	public abstract void create(boolean bare) throws IOException;

	/** @return local metadata directory; null if repository isn't local. */
	public File getDirectory() {
		return gitDir;
	}

	/**
	 * @return the object database which stores this repository's data.
	 */
	public abstract ObjectDatabase getObjectDatabase();

	/** @return a new inserter to create objects in {@link #getObjectDatabase()} */
	public ObjectInserter newObjectInserter() {
		return getObjectDatabase().newInserter();
	}

	/** @return a new reader to read objects from {@link #getObjectDatabase()} */
	public ObjectReader newObjectReader() {
		return getObjectDatabase().newReader();
	}

	/** @return the reference database which stores the reference namespace. */
	public abstract RefDatabase getRefDatabase();

	/**
	 * @return the configuration of this repository
	 */
	public abstract StoredConfig getConfig();

	/**
	 * @return the used file system abstraction
	 */
	public FS getFS() {
		return fs;
	}

	/**
	 * @param objectId
	 * @return true if the specified object is stored in this repo or any of the
	 *         known shared repositories.
	 */
	public boolean hasObject(AnyObjectId objectId) {
		try {
			return getObjectDatabase().has(objectId);
		} catch (IOException e) {
			// Legacy API, assume error means "no"
			return false;
		}
	}

	/**
	 * Open an object from this repository.
	 * <p>
	 * This is a one-shot call interface which may be faster than allocating a
	 * {@link #newObjectReader()} to perform the lookup.
	 *
	 * @param objectId
	 *            identity of the object to open.
	 * @return a {@link ObjectLoader} for accessing the object.
	 * @throws MissingObjectException
	 *             the object does not exist.
	 * @throws IOException
	 *             the object store cannot be accessed.
	 */
	public ObjectLoader open(final AnyObjectId objectId)
			throws MissingObjectException, IOException {
		return getObjectDatabase().open(objectId);
	}

	/**
	 * Open an object from this repository.
	 * <p>
	 * This is a one-shot call interface which may be faster than allocating a
	 * {@link #newObjectReader()} to perform the lookup.
	 *
	 * @param objectId
	 *            identity of the object to open.
	 * @param typeHint
	 *            hint about the type of object being requested;
	 *            {@link ObjectReader#OBJ_ANY} if the object type is not known,
	 *            or does not matter to the caller.
	 * @return a {@link ObjectLoader} for accessing the object.
	 * @throws MissingObjectException
	 *             the object does not exist.
	 * @throws IncorrectObjectTypeException
	 *             typeHint was not OBJ_ANY, and the object's actual type does
	 *             not match typeHint.
	 * @throws IOException
	 *             the object store cannot be accessed.
	 */
	public ObjectLoader open(AnyObjectId objectId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		return getObjectDatabase().open(objectId, typeHint);
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
	 * Combinations of these operators are supported:
	 * <ul>
	 * <li><b>HEAD</b>, <b>MERGE_HEAD</b>, <b>FETCH_HEAD</b></li>
	 * <li><b>SHA-1</b>: a complete or abbreviated SHA-1</li>
	 * <li><b>refs/...</b>: a complete reference name</li>
	 * <li><b>short-name</b>: a short reference name under {@code refs/heads},
	 * {@code refs/tags}, or {@code refs/remotes} namespace</li>
	 * <li><b>tag-NN-gABBREV</b>: output from describe, parsed by treating
	 * {@code ABBREV} as an abbreviated SHA-1.</li>
	 * <li><i>id</i><b>^</b>: first parent of commit <i>id</i>, this is the same
	 * as {@code id^1}</li>
	 * <li><i>id</i><b>^0</b>: ensure <i>id</i> is a commit</li>
	 * <li><i>id</i><b>^n</b>: n-th parent of commit <i>id</i></li>
	 * <li><i>id</i><b>~n</b>: n-th historical ancestor of <i>id</i>, by first
	 * parent. {@code id~3} is equivalent to {@code id^1^1^1} or {@code id^^^}.</li>
	 * <li><i>id</i><b>:path</b>: Lookup path under tree named by <i>id</i></li>
	 * <li><i>id</i><b>^{commit}</b>: ensure <i>id</i> is a commit</li>
	 * <li><i>id</i><b>^{tree}</b>: ensure <i>id</i> is a tree</li>
	 * <li><i>id</i><b>^{tag}</b>: ensure <i>id</i> is a tag</li>
	 * <li><i>id</i><b>^{blob}</b>: ensure <i>id</i> is a blob</li>
	 * </ul>
	 *
	 * <p>
	 * The following operators are specified by Git conventions, but are not
	 * supported by this method:
	 * <ul>
	 * <li><b>ref@{n}</b>: n-th version of ref as given by its reflog</li>
	 * <li><b>ref@{time}</b>: value of ref at the designated time</li>
	 * </ul>
	 *
	 * @param revstr
	 *            A git object references expression
	 * @return an ObjectId or null if revstr can't be resolved to any ObjectId
	 * @throws AmbiguousObjectException
	 *             {@code revstr} contains an abbreviated ObjectId and this
	 *             repository contains more than one object which match to the
	 *             input abbreviation.
	 * @throws IncorrectObjectTypeException
	 *             the id parsed does not meet the type required to finish
	 *             applying the operators in the expression.
	 * @throws RevisionSyntaxException
	 *             the expression is not supported by this implementation, or
	 *             does not meet the standard syntax.
	 * @throws IOException
	 *             on serious errors
	 */
	public ObjectId resolve(final String revstr)
			throws AmbiguousObjectException, IncorrectObjectTypeException,
			RevisionSyntaxException, IOException {
		RevWalk rw = new RevWalk(this);
		try {
			Object resolved = resolve(rw, revstr);
			if (resolved instanceof String) {
				return getRef((String) resolved).getLeaf().getObjectId();
			} else {
				return (ObjectId) resolved;
			}
		} finally {
			rw.release();
		}
	}

	/**
	 * Simplify an expression, but unlike {@link #resolve(String)} it will not
	 * resolve a branch passed or resulting from the expression, such as @{-}.
	 * Thus this method can be used to process an expression to a method that
	 * expects a branch or revision id.
	 *
	 * @param revstr
	 * @return object id or ref name from resolved expression
	 * @throws AmbiguousObjectException
	 * @throws IOException
	 */
	public String simplify(final String revstr)
			throws AmbiguousObjectException, IOException {
		RevWalk rw = new RevWalk(this);
		try {
			Object resolved = resolve(rw, revstr);
			if (resolved != null)
				if (resolved instanceof String)
					return (String) resolved;
				else
					return ((AnyObjectId) resolved).getName();
			return null;
		} finally {
			rw.release();
		}
	}

	private Object resolve(final RevWalk rw, final String revstr)
			throws IOException {
		char[] revChars = revstr.toCharArray();
		RevObject rev = null;
		String name = null;
		int done = 0;
		for (int i = 0; i < revChars.length; ++i) {
			switch (revChars[i]) {
			case '^':
				if (rev == null) {
					if (name == null)
						if (done == 0)
							name = new String(revChars, done, i);
						else {
							done = i + 1;
							break;
						}
					rev = parseSimple(rw, name);
					name = null;
					if (rev == null)
						return null;
				}
				if (i + 1 < revChars.length) {
					switch (revChars[i + 1]) {
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
						rev = rw.parseCommit(rev);
						for (j = i + 1; j < revChars.length; ++j) {
							if (!Character.isDigit(revChars[j]))
								break;
						}
						String parentnum = new String(revChars, i + 1, j - i
								- 1);
						int pnum;
						try {
							pnum = Integer.parseInt(parentnum);
						} catch (NumberFormatException e) {
							throw new RevisionSyntaxException(
									JGitText.get().invalidCommitParentNumber,
									revstr);
						}
						if (pnum != 0) {
							RevCommit commit = (RevCommit) rev;
							if (pnum > commit.getParentCount())
								rev = null;
							else
								rev = commit.getParent(pnum - 1);
						}
						i = j - 1;
						done = j;
						break;
					case '{':
						int k;
						String item = null;
						for (k = i + 2; k < revChars.length; ++k) {
							if (revChars[k] == '}') {
								item = new String(revChars, i + 2, k - i - 2);
								break;
							}
						}
						i = k;
						if (item != null)
							if (item.equals("tree")) { //$NON-NLS-1$
								rev = rw.parseTree(rev);
							} else if (item.equals("commit")) { //$NON-NLS-1$
								rev = rw.parseCommit(rev);
							} else if (item.equals("blob")) { //$NON-NLS-1$
								rev = rw.peel(rev);
								if (!(rev instanceof RevBlob))
									throw new IncorrectObjectTypeException(rev,
											Constants.TYPE_BLOB);
							} else if (item.equals("")) { //$NON-NLS-1$
								rev = rw.peel(rev);
							} else
								throw new RevisionSyntaxException(revstr);
						else
							throw new RevisionSyntaxException(revstr);
						done = k;
						break;
					default:
						rev = rw.peel(rev);
						if (rev instanceof RevCommit) {
							RevCommit commit = ((RevCommit) rev);
							if (commit.getParentCount() == 0)
								rev = null;
							else
								rev = commit.getParent(0);
						} else
							throw new IncorrectObjectTypeException(rev,
									Constants.TYPE_COMMIT);
					}
				} else {
					rev = rw.peel(rev);
					if (rev instanceof RevCommit) {
						RevCommit commit = ((RevCommit) rev);
						if (commit.getParentCount() == 0)
							rev = null;
						else
							rev = commit.getParent(0);
					} else
						throw new IncorrectObjectTypeException(rev,
								Constants.TYPE_COMMIT);
				}
				done = i + 1;
				break;
			case '~':
				if (rev == null) {
					if (name == null)
						if (done == 0)
							name = new String(revChars, done, i);
						else {
							done = i + 1;
							break;
						}
					rev = parseSimple(rw, name);
					name = null;
					if (rev == null)
						return null;
				}
				rev = rw.peel(rev);
				if (!(rev instanceof RevCommit))
					throw new IncorrectObjectTypeException(rev,
							Constants.TYPE_COMMIT);
				int l;
				for (l = i + 1; l < revChars.length; ++l) {
					if (!Character.isDigit(revChars[l]))
						break;
				}
				int dist;
				if (l - i > 1) {
					String distnum = new String(revChars, i + 1, l - i - 1);
					try {
						dist = Integer.parseInt(distnum);
					} catch (NumberFormatException e) {
						throw new RevisionSyntaxException(
								JGitText.get().invalidAncestryLength, revstr);
					}
				} else
					dist = 1;
				while (dist > 0) {
					RevCommit commit = (RevCommit) rev;
					if (commit.getParentCount() == 0) {
						rev = null;
						break;
					}
					commit = commit.getParent(0);
					rw.parseHeaders(commit);
					rev = commit;
					--dist;
				}
				i = l - 1;
				done = l;
				break;
			case '@':
				if (rev != null)
					throw new RevisionSyntaxException(revstr);
				if (i + 1 < revChars.length && revChars[i + 1] != '{')
					continue;
				int m;
				String time = null;
				for (m = i + 2; m < revChars.length; ++m) {
					if (revChars[m] == '}') {
						time = new String(revChars, i + 2, m - i - 2);
						break;
					}
				}
				if (time != null) {
					if (time.equals("upstream")) { //$NON-NLS-1$
						if (name == null)
							name = new String(revChars, done, i);
						if (name.equals("")) //$NON-NLS-1$
							// Currently checked out branch, HEAD if
							// detached
							name = Constants.HEAD;
						if (!Repository.isValidRefName("x/" + name)) //$NON-NLS-1$
							throw new RevisionSyntaxException(revstr);
						Ref ref = getRef(name);
						name = null;
						if (ref == null)
							return null;
						if (ref.isSymbolic())
							ref = ref.getLeaf();
						name = ref.getName();

						RemoteConfig remoteConfig;
						try {
							remoteConfig = new RemoteConfig(getConfig(),
									"origin"); //$NON-NLS-1$
						} catch (URISyntaxException e) {
							throw new RevisionSyntaxException(revstr);
						}
						String remoteBranchName = getConfig()
								.getString(
										ConfigConstants.CONFIG_BRANCH_SECTION,
								Repository.shortenRefName(ref.getName()),
										ConfigConstants.CONFIG_KEY_MERGE);
						List<RefSpec> fetchRefSpecs = remoteConfig
								.getFetchRefSpecs();
						for (RefSpec refSpec : fetchRefSpecs) {
							if (refSpec.matchSource(remoteBranchName)) {
								RefSpec expandFromSource = refSpec
										.expandFromSource(remoteBranchName);
								name = expandFromSource.getDestination();
								break;
							}
						}
						if (name == null)
							throw new RevisionSyntaxException(revstr);
					} else if (time.matches("^-\\d+$")) { //$NON-NLS-1$
						if (name != null)
							throw new RevisionSyntaxException(revstr);
						else {
							String previousCheckout = resolveReflogCheckout(-Integer
									.parseInt(time));
							if (ObjectId.isId(previousCheckout))
								rev = parseSimple(rw, previousCheckout);
							else
								name = previousCheckout;
						}
					} else {
						if (name == null)
							name = new String(revChars, done, i);
						if (name.equals("")) //$NON-NLS-1$
							name = Constants.HEAD;
						if (!Repository.isValidRefName("x/" + name)) //$NON-NLS-1$
							throw new RevisionSyntaxException(revstr);
						Ref ref = getRef(name);
						name = null;
						if (ref == null)
							return null;
						// @{n} means current branch, not HEAD@{1} unless
						// detached
						if (ref.isSymbolic())
							ref = ref.getLeaf();
						rev = resolveReflog(rw, ref, time);
					}
					i = m;
				} else
					throw new RevisionSyntaxException(revstr);
				break;
			case ':': {
				RevTree tree;
				if (rev == null) {
					if (name == null)
						name = new String(revChars, done, i);
					if (name.equals("")) //$NON-NLS-1$
						name = Constants.HEAD;
					rev = parseSimple(rw, name);
					name = null;
				}
				if (rev == null)
					return null;
				tree = rw.parseTree(rev);
				if (i == revChars.length - 1)
					return tree.copy();

				TreeWalk tw = TreeWalk.forPath(rw.getObjectReader(),
						new String(revChars, i + 1, revChars.length - i - 1),
						tree);
				return tw != null ? tw.getObjectId(0) : null;
			}
			default:
				if (rev != null)
					throw new RevisionSyntaxException(revstr);
			}
		}
		if (rev != null)
			return rev.copy();
		if (name != null)
			return name;
		if (done == revstr.length())
			return null;
		name = revstr.substring(done);
		if (!Repository.isValidRefName("x/" + name)) //$NON-NLS-1$
			throw new RevisionSyntaxException(revstr);
		if (getRef(name) != null)
			return name;
		return resolveSimple(name);
	}

	private static boolean isHex(char c) {
		return ('0' <= c && c <= '9') //
				|| ('a' <= c && c <= 'f') //
				|| ('A' <= c && c <= 'F');
	}

	private static boolean isAllHex(String str, int ptr) {
		while (ptr < str.length()) {
			if (!isHex(str.charAt(ptr++)))
				return false;
		}
		return true;
	}

	private RevObject parseSimple(RevWalk rw, String revstr) throws IOException {
		ObjectId id = resolveSimple(revstr);
		return id != null ? rw.parseAny(id) : null;
	}

	private ObjectId resolveSimple(final String revstr) throws IOException {
		if (ObjectId.isId(revstr))
			return ObjectId.fromString(revstr);

		if (Repository.isValidRefName("x/" + revstr)) { //$NON-NLS-1$
			Ref r = getRefDatabase().getRef(revstr);
			if (r != null)
				return r.getObjectId();
		}

		if (AbbreviatedObjectId.isId(revstr))
			return resolveAbbreviation(revstr);

		int dashg = revstr.indexOf("-g"); //$NON-NLS-1$
		if ((dashg + 5) < revstr.length() && 0 <= dashg
				&& isHex(revstr.charAt(dashg + 2))
				&& isHex(revstr.charAt(dashg + 3))
				&& isAllHex(revstr, dashg + 4)) {
			// Possibly output from git describe?
			String s = revstr.substring(dashg + 2);
			if (AbbreviatedObjectId.isId(s))
				return resolveAbbreviation(s);
		}

		return null;
	}

	private String resolveReflogCheckout(int checkoutNo)
			throws IOException {
		List<ReflogEntry> reflogEntries = getReflogReader(Constants.HEAD)
				.getReverseEntries();
		for (ReflogEntry entry : reflogEntries) {
			CheckoutEntry checkout = entry.parseCheckout();
			if (checkout != null)
				if (checkoutNo-- == 1)
					return checkout.getFromBranch();
		}
		return null;
	}

	private RevCommit resolveReflog(RevWalk rw, Ref ref, String time)
			throws IOException {
		int number;
		try {
			number = Integer.parseInt(time);
		} catch (NumberFormatException nfe) {
			throw new RevisionSyntaxException(MessageFormat.format(
					JGitText.get().invalidReflogRevision, time));
		}
		assert number >= 0;
		ReflogReader reader = getReflogReader(ref.getName());
		ReflogEntry entry = reader.getReverseEntry(number);
		if (entry == null)
			throw new RevisionSyntaxException(MessageFormat.format(
					JGitText.get().reflogEntryNotFound,
					Integer.valueOf(number), ref.getName()));

		return rw.parseCommit(entry.getNewId());
	}

	private ObjectId resolveAbbreviation(final String revstr) throws IOException,
			AmbiguousObjectException {
		AbbreviatedObjectId id = AbbreviatedObjectId.fromString(revstr);
		ObjectReader reader = newObjectReader();
		try {
			Collection<ObjectId> matches = reader.resolve(id);
			if (matches.size() == 0)
				return null;
			else if (matches.size() == 1)
				return matches.iterator().next();
			else
				throw new AmbiguousObjectException(id, matches);
		} finally {
			reader.release();
		}
	}

	/** Increment the use counter by one, requiring a matched {@link #close()}. */
	public void incrementOpen() {
		useCnt.incrementAndGet();
	}

	/** Decrement the use count, and maybe close resources. */
	public void close() {
		if (useCnt.decrementAndGet() == 0) {
			doClose();
		}
	}

	/**
	 * Invoked when the use count drops to zero during {@link #close()}.
	 * <p>
	 * The default implementation closes the object and ref databases.
	 */
	protected void doClose() {
		getObjectDatabase().close();
		getRefDatabase().close();
	}

	@SuppressWarnings("nls")
	public String toString() {
		String desc;
		if (getDirectory() != null)
			desc = getDirectory().getPath();
		else
			desc = getClass().getSimpleName() + "-" //$NON-NLS-1$
					+ System.identityHashCode(this);
		return "Repository[" + desc + "]"; //$NON-NLS-1$
	}

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
	 * @return the index file location
	 * @throws NoWorkTreeException
	 *             if this is bare, which implies it has no working directory.
	 *             See {@link #isBare()}.
	 */
	public File getIndexFile() throws NoWorkTreeException {
		if (isBare())
			throw new NoWorkTreeException();
		return indexFile;
	}

	/**
	 * Create a new in-core index representation and read an index from disk.
	 * <p>
	 * The new index will be read before it is returned to the caller. Read
	 * failures are reported as exceptions and therefore prevent the method from
	 * returning a partially populated index.
	 *
	 * @return a cache representing the contents of the specified index file (if
	 *         it exists) or an empty cache if the file does not exist.
	 * @throws NoWorkTreeException
	 *             if this is bare, which implies it has no working directory.
	 *             See {@link #isBare()}.
	 * @throws IOException
	 *             the index file is present but could not be read.
	 * @throws CorruptObjectException
	 *             the index file is using a format or extension that this
	 *             library does not support.
	 */
	public DirCache readDirCache() throws NoWorkTreeException,
			CorruptObjectException, IOException {
		return DirCache.read(this);
	}

	/**
	 * Create a new in-core index representation, lock it, and read from disk.
	 * <p>
	 * The new index will be locked and then read before it is returned to the
	 * caller. Read failures are reported as exceptions and therefore prevent
	 * the method from returning a partially populated index.
	 *
	 * @return a cache representing the contents of the specified index file (if
	 *         it exists) or an empty cache if the file does not exist.
	 * @throws NoWorkTreeException
	 *             if this is bare, which implies it has no working directory.
	 *             See {@link #isBare()}.
	 * @throws IOException
	 *             the index file is present but could not be read, or the lock
	 *             could not be obtained.
	 * @throws CorruptObjectException
	 *             the index file is using a format or extension that this
	 *             library does not support.
	 */
	public DirCache lockDirCache() throws NoWorkTreeException,
			CorruptObjectException, IOException {
		// we want DirCache to inform us so that we can inform registered
		// listeners about index changes
		IndexChangedListener l = new IndexChangedListener() {

			public void onIndexChanged(IndexChangedEvent event) {
				notifyIndexChanged();
			}
		};
		return DirCache.lock(this, l);
	}

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
	public RepositoryState getRepositoryState() {
		if (isBare() || getDirectory() == null)
			return RepositoryState.BARE;

		// Pre Git-1.6 logic
		if (new File(getWorkTree(), ".dotest").exists()) //$NON-NLS-1$
			return RepositoryState.REBASING;
		if (new File(getDirectory(), ".dotest-merge").exists()) //$NON-NLS-1$
			return RepositoryState.REBASING_INTERACTIVE;

		// From 1.6 onwards
		if (new File(getDirectory(),"rebase-apply/rebasing").exists()) //$NON-NLS-1$
			return RepositoryState.REBASING_REBASING;
		if (new File(getDirectory(),"rebase-apply/applying").exists()) //$NON-NLS-1$
			return RepositoryState.APPLY;
		if (new File(getDirectory(),"rebase-apply").exists()) //$NON-NLS-1$
			return RepositoryState.REBASING;

		if (new File(getDirectory(),"rebase-merge/interactive").exists()) //$NON-NLS-1$
			return RepositoryState.REBASING_INTERACTIVE;
		if (new File(getDirectory(),"rebase-merge").exists()) //$NON-NLS-1$
			return RepositoryState.REBASING_MERGE;

		// Both versions
		if (new File(getDirectory(), Constants.MERGE_HEAD).exists()) {
			// we are merging - now check whether we have unmerged paths
			try {
				if (!readDirCache().hasUnmergedPaths()) {
					// no unmerged paths -> return the MERGING_RESOLVED state
					return RepositoryState.MERGING_RESOLVED;
				}
			} catch (IOException e) {
				// Can't decide whether unmerged paths exists. Return
				// MERGING state to be on the safe side (in state MERGING
				// you are not allow to do anything)
			}
			return RepositoryState.MERGING;
		}

		if (new File(getDirectory(), "BISECT_LOG").exists()) //$NON-NLS-1$
			return RepositoryState.BISECTING;

		if (new File(getDirectory(), Constants.CHERRY_PICK_HEAD).exists()) {
			try {
				if (!readDirCache().hasUnmergedPaths()) {
					// no unmerged paths
					return RepositoryState.CHERRY_PICKING_RESOLVED;
				}
			} catch (IOException e) {
				// fall through to CHERRY_PICKING
			}

			return RepositoryState.CHERRY_PICKING;
		}

		if (new File(getDirectory(), Constants.REVERT_HEAD).exists()) {
			try {
				if (!readDirCache().hasUnmergedPaths()) {
					// no unmerged paths
					return RepositoryState.REVERTING_RESOLVED;
				}
			} catch (IOException e) {
				// fall through to REVERTING
			}

			return RepositoryState.REVERTING;
		}

		return RepositoryState.SAFE;
	}

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
		if (refName.endsWith(".lock")) //$NON-NLS-1$
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
				if (p == '/')
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
			case '\u007F':
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
				return ""; //$NON-NLS-1$
			return stripWorkDir(absWd, absFile);
		}

		String relName = filePath.substring(workDirPath.length() + 1);
		if (File.separatorChar != '/')
			relName = relName.replace(File.separatorChar, '/');
		return relName;
	}

	/**
	 * @return true if this is bare, which implies it has no working directory.
	 */
	public boolean isBare() {
		return workTree == null;
	}

	/**
	 * @return the root directory of the working tree, where files are checked
	 *         out for viewing and editing.
	 * @throws NoWorkTreeException
	 *             if this is bare, which implies it has no working directory.
	 *             See {@link #isBare()}.
	 */
	public File getWorkTree() throws NoWorkTreeException {
		if (isBare())
			throw new NoWorkTreeException();
		return workTree;
	}

	/**
	 * Force a scan for changed refs.
	 *
	 * @throws IOException
	 */
	public abstract void scanForRepoChanges() throws IOException;

	/**
	 * Notify that the index changed
	 */
	public abstract void notifyIndexChanged();

	/**
	 * @param refName
	 *
	 * @return a more user friendly ref name
	 */
	public static String shortenRefName(String refName) {
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
	 * @since 3.0
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
	 * @throws NoWorkTreeException
	 *             if this is bare, which implies it has no working directory.
	 *             See {@link #isBare()}.
	 */
	public String readMergeCommitMsg() throws IOException, NoWorkTreeException {
		return readCommitMsgFile(Constants.MERGE_MSG);
	}

	/**
	 * Write new content to the file $GIT_DIR/MERGE_MSG. In this file operations
	 * triggering a merge will store a template for the commit message of the
	 * merge commit. If <code>null</code> is specified as message the file will
	 * be deleted.
	 *
	 * @param msg
	 *            the message which should be written or <code>null</code> to
	 *            delete the file
	 *
	 * @throws IOException
	 */
	public void writeMergeCommitMsg(String msg) throws IOException {
		File mergeMsgFile = new File(gitDir, Constants.MERGE_MSG);
		writeCommitMsg(mergeMsgFile, msg);
	}

	/**
	 * Return the information stored in the file $GIT_DIR/MERGE_HEAD. In this
	 * file operations triggering a merge will store the IDs of all heads which
	 * should be merged together with HEAD.
	 *
	 * @return a list of commits which IDs are listed in the MERGE_HEAD file or
	 *         {@code null} if this file doesn't exist. Also if the file exists
	 *         but is empty {@code null} will be returned
	 * @throws IOException
	 * @throws NoWorkTreeException
	 *             if this is bare, which implies it has no working directory.
	 *             See {@link #isBare()}.
	 */
	public List<ObjectId> readMergeHeads() throws IOException, NoWorkTreeException {
		if (isBare() || getDirectory() == null)
			throw new NoWorkTreeException();

		byte[] raw = readGitDirectoryFile(Constants.MERGE_HEAD);
		if (raw == null)
			return null;

		LinkedList<ObjectId> heads = new LinkedList<ObjectId>();
		for (int p = 0; p < raw.length;) {
			heads.add(ObjectId.fromString(raw, p));
			p = RawParseUtils
					.nextLF(raw, p + Constants.OBJECT_ID_STRING_LENGTH);
		}
		return heads;
	}

	/**
	 * Write new merge-heads into $GIT_DIR/MERGE_HEAD. In this file operations
	 * triggering a merge will store the IDs of all heads which should be merged
	 * together with HEAD. If <code>null</code> is specified as list of commits
	 * the file will be deleted
	 *
	 * @param heads
	 *            a list of commits which IDs should be written to
	 *            $GIT_DIR/MERGE_HEAD or <code>null</code> to delete the file
	 * @throws IOException
	 */
	public void writeMergeHeads(List<ObjectId> heads) throws IOException {
		writeHeadsFile(heads, Constants.MERGE_HEAD);
	}

	/**
	 * Return the information stored in the file $GIT_DIR/CHERRY_PICK_HEAD.
	 *
	 * @return object id from CHERRY_PICK_HEAD file or {@code null} if this file
	 *         doesn't exist. Also if the file exists but is empty {@code null}
	 *         will be returned
	 * @throws IOException
	 * @throws NoWorkTreeException
	 *             if this is bare, which implies it has no working directory.
	 *             See {@link #isBare()}.
	 */
	public ObjectId readCherryPickHead() throws IOException,
			NoWorkTreeException {
		if (isBare() || getDirectory() == null)
			throw new NoWorkTreeException();

		byte[] raw = readGitDirectoryFile(Constants.CHERRY_PICK_HEAD);
		if (raw == null)
			return null;

		return ObjectId.fromString(raw, 0);
	}

	/**
	 * Return the information stored in the file $GIT_DIR/REVERT_HEAD.
	 *
	 * @return object id from REVERT_HEAD file or {@code null} if this file
	 *         doesn't exist. Also if the file exists but is empty {@code null}
	 *         will be returned
	 * @throws IOException
	 * @throws NoWorkTreeException
	 *             if this is bare, which implies it has no working directory.
	 *             See {@link #isBare()}.
	 */
	public ObjectId readRevertHead() throws IOException, NoWorkTreeException {
		if (isBare() || getDirectory() == null)
			throw new NoWorkTreeException();

		byte[] raw = readGitDirectoryFile(Constants.REVERT_HEAD);
		if (raw == null)
			return null;
		return ObjectId.fromString(raw, 0);
	}

	/**
	 * Write cherry pick commit into $GIT_DIR/CHERRY_PICK_HEAD. This is used in
	 * case of conflicts to store the cherry which was tried to be picked.
	 *
	 * @param head
	 *            an object id of the cherry commit or <code>null</code> to
	 *            delete the file
	 * @throws IOException
	 */
	public void writeCherryPickHead(ObjectId head) throws IOException {
		List<ObjectId> heads = (head != null) ? Collections.singletonList(head)
				: null;
		writeHeadsFile(heads, Constants.CHERRY_PICK_HEAD);
	}

	/**
	 * Write revert commit into $GIT_DIR/REVERT_HEAD. This is used in case of
	 * conflicts to store the revert which was tried to be picked.
	 *
	 * @param head
	 *            an object id of the revert commit or <code>null</code> to
	 *            delete the file
	 * @throws IOException
	 */
	public void writeRevertHead(ObjectId head) throws IOException {
		List<ObjectId> heads = (head != null) ? Collections.singletonList(head)
				: null;
		writeHeadsFile(heads, Constants.REVERT_HEAD);
	}

	/**
	 * Write original HEAD commit into $GIT_DIR/ORIG_HEAD.
	 *
	 * @param head
	 *            an object id of the original HEAD commit or <code>null</code>
	 *            to delete the file
	 * @throws IOException
	 */
	public void writeOrigHead(ObjectId head) throws IOException {
		List<ObjectId> heads = head != null ? Collections.singletonList(head)
				: null;
		writeHeadsFile(heads, Constants.ORIG_HEAD);
	}

	/**
	 * Return the information stored in the file $GIT_DIR/ORIG_HEAD.
	 *
	 * @return object id from ORIG_HEAD file or {@code null} if this file
	 *         doesn't exist. Also if the file exists but is empty {@code null}
	 *         will be returned
	 * @throws IOException
	 * @throws NoWorkTreeException
	 *             if this is bare, which implies it has no working directory.
	 *             See {@link #isBare()}.
	 */
	public ObjectId readOrigHead() throws IOException, NoWorkTreeException {
		if (isBare() || getDirectory() == null)
			throw new NoWorkTreeException();

		byte[] raw = readGitDirectoryFile(Constants.ORIG_HEAD);
		return raw != null ? ObjectId.fromString(raw, 0) : null;
	}

	/**
	 * Return the information stored in the file $GIT_DIR/SQUASH_MSG. In this
	 * file operations triggering a squashed merge will store a template for the
	 * commit message of the squash commit.
	 *
	 * @return a String containing the content of the SQUASH_MSG file or
	 *         {@code null} if this file doesn't exist
	 * @throws IOException
	 * @throws NoWorkTreeException
	 *             if this is bare, which implies it has no working directory.
	 *             See {@link #isBare()}.
	 */
	public String readSquashCommitMsg() throws IOException {
		return readCommitMsgFile(Constants.SQUASH_MSG);
	}

	/**
	 * Write new content to the file $GIT_DIR/SQUASH_MSG. In this file
	 * operations triggering a squashed merge will store a template for the
	 * commit message of the squash commit. If <code>null</code> is specified as
	 * message the file will be deleted.
	 *
	 * @param msg
	 *            the message which should be written or <code>null</code> to
	 *            delete the file
	 *
	 * @throws IOException
	 */
	public void writeSquashCommitMsg(String msg) throws IOException {
		File squashMsgFile = new File(gitDir, Constants.SQUASH_MSG);
		writeCommitMsg(squashMsgFile, msg);
	}

	private String readCommitMsgFile(String msgFilename) throws IOException {
		if (isBare() || getDirectory() == null)
			throw new NoWorkTreeException();

		File mergeMsgFile = new File(getDirectory(), msgFilename);
		try {
			return RawParseUtils.decode(IO.readFully(mergeMsgFile));
		} catch (FileNotFoundException e) {
			// the file has disappeared in the meantime ignore it
			return null;
		}
	}

	private void writeCommitMsg(File msgFile, String msg) throws IOException {
		if (msg != null) {
			FileOutputStream fos = new FileOutputStream(msgFile);
			try {
				fos.write(msg.getBytes(Constants.CHARACTER_ENCODING));
			} finally {
				fos.close();
			}
		} else {
			FileUtils.delete(msgFile, FileUtils.SKIP_MISSING);
		}
	}

	/**
	 * Read a file from the git directory.
	 *
	 * @param filename
	 * @return the raw contents or null if the file doesn't exist or is empty
	 * @throws IOException
	 */
	private byte[] readGitDirectoryFile(String filename) throws IOException {
		File file = new File(getDirectory(), filename);
		try {
			byte[] raw = IO.readFully(file);
			return raw.length > 0 ? raw : null;
		} catch (FileNotFoundException notFound) {
			return null;
		}
	}

	/**
	 * Write the given heads to a file in the git directory.
	 *
	 * @param heads
	 *            a list of object ids to write or null if the file should be
	 *            deleted.
	 * @param filename
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private void writeHeadsFile(List<ObjectId> heads, String filename)
			throws FileNotFoundException, IOException {
		File headsFile = new File(getDirectory(), filename);
		if (heads != null) {
			BufferedOutputStream bos = new SafeBufferedOutputStream(
					new FileOutputStream(headsFile));
			try {
				for (ObjectId id : heads) {
					id.copyTo(bos);
					bos.write('\n');
				}
			} finally {
				bos.close();
			}
		} else {
			FileUtils.delete(headsFile, FileUtils.SKIP_MISSING);
		}
	}

	/**
	 * Read a file formatted like the git-rebase-todo file. The "done" file is
	 * also formatted like the git-rebase-todo file. These files can be found in
	 * .git/rebase-merge/ or .git/rebase-append/ folders.
	 * 
	 * @param path
	 *            path to the file relative to the repository's git-dir. E.g.
	 *            "rebase-merge/git-rebase-todo" or "rebase-append/done"
	 * @param includeComments
	 *            <code>true</code> if also comments should be reported
	 * @return the list of steps
	 * @throws IOException
	 */
	public List<RebaseTodoLine> readRebaseTodo(String path,
			boolean includeComments)
			throws IOException {
		return new RebaseTodoFile(this).readRebaseTodo(path, includeComments);
	}

	/**
	 * Write a file formatted like a git-rebase-todo file.
	 *
	 * @param path
	 *            path to the file relative to the repository's git-dir. E.g.
	 *            "rebase-merge/git-rebase-todo" or "rebase-append/done"
	 * @param steps
	 *            the steps to be written
	 * @param append
	 *            whether to append to an existing file or to write a new file
	 * @throws IOException
	 */
	public void writeRebaseTodoFile(String path, List<RebaseTodoLine> steps,
			boolean append)
			throws IOException {
		new RebaseTodoFile(this).writeRebaseTodoFile(path, steps, append);
	}
}

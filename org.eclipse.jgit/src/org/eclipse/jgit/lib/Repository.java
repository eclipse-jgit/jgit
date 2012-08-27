/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2012, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2012, Shawn O. Pearce <spearce@spearce.org>
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.events.ListenerList;
import org.eclipse.jgit.events.RepositoryEvent;
import org.eclipse.jgit.storage.file.ReflogReader;
import org.eclipse.jgit.util.FS;

/**
 * Represents a Git repository.
 * <p>
 * A repository holds all objects and refs used for managing source code (could
 * be any type of file, but source code is what SCM's are typically used for).
 * <p>
 * This class only contains static method fields and implementations, in effect
 * this is an interface, but has to be a class for backwards compatibity.
 * <p>
 * Subclasses of this class <em>should be</em> thread-safe. This class is.
 */
public abstract class Repository implements Replacements {
	static final ListenerList globalListeners = new ListenerList();

	/** @return the global listener list observing all events in this JVM. */
	public static ListenerList getGlobalListenerList() {
		return globalListeners;
	}

	/** @return listeners observing only events on this repository. */
	public abstract ListenerList getListenerList();

	/**
	 * Initialize a new repository instance.
	 * 
	 * @param options
	 *            options to configure the repository.
	 */
	protected Repository(final BaseRepositoryBuilder options) {
		// empty
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
	public abstract void fireEvent(RepositoryEvent<?> event);

	/**
	 * Create a new Git repository.
	 * <p>
	 * Repository with working tree is created using this method. This method is
	 * the same as {@code create(false)}.
	 *
	 * @throws IOException
	 * @see #create(boolean)
	 */
	public abstract void create() throws IOException;

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
	public abstract File getDirectory();

	/**
	 * @return the object database which stores this repository's data.
	 */
	public abstract ObjectDatabase getObjectDatabase();

	/** @return a new inserter to create objects in {@link #getObjectDatabase()} */
	public abstract ObjectInserter newObjectInserter();

	/** @return a new reader to read objects from {@link #getObjectDatabase()} */
	public abstract ObjectReader newObjectReader();

	/** @return the reference database which stores the reference namespace. */
	public abstract RefDatabase getRefDatabase();

	/** @return the grafts database which store the grafted parents */
	public abstract GraftsDatabase getGraftsDatabase();

	/**
	 * @return the configuration of this repository
	 */
	public abstract StoredConfig getConfig();

	/**
	 * @return the used file system abstraction
	 */
	public abstract FS getFS();

	/**
	 * @param objectId
	 * @return true if the specified object is stored in this repo or any of the
	 *         known shared repositories.
	 */
	public abstract boolean hasObject(AnyObjectId objectId);

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
	public abstract ObjectLoader open(AnyObjectId objectId)
			throws MissingObjectException,
			IOException;

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
	public abstract ObjectLoader open(AnyObjectId objectId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException;

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
	public abstract RefUpdate updateRef(String ref) throws IOException;

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
	public abstract RefUpdate updateRef(String ref, boolean detach)
			throws IOException;

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
	public abstract RefRename renameRef(String fromRef, String toRef)
			throws IOException;

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
	public abstract ObjectId resolve(String revstr)
			throws AmbiguousObjectException,
			IOException;

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
	public abstract String simplify(final String revstr)
			throws AmbiguousObjectException, IOException;

	/** Increment the use counter by one, requiring a matched {@link #close()}. */
	public abstract void incrementOpen();

	/** Decrement the use count, and maybe close resources. */
	public abstract void close();

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
	public abstract String getFullBranch() throws IOException;

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
	public abstract String getBranch() throws IOException;

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
	public abstract Set<ObjectId> getAdditionalHaves();

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
	public abstract Ref getRef(String name) throws IOException;

	/**
	 * @return mutable map of all known refs (heads, tags, remotes).
	 */
	public abstract Map<String, Ref> getAllRefs();

	/**
	 * @return mutable map of all tags; key is short tag name ("v1.0") and value
	 *         of the entry contains the ref with the full tag name
	 *         ("refs/tags/v1.0").
	 */
	public abstract Map<String, Ref> getTags();

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
	public abstract Ref peel(Ref ref);

	/**
	 * @return a map with all objects referenced by a peeled ref.
	 */
	public abstract Map<AnyObjectId, Set<Ref>> getAllRefsByPeeledObjectId();

	/**
	 * @return the index file location
	 * @throws NoWorkTreeException
	 *             if this is bare, which implies it has no working directory.
	 *             See {@link #isBare()}.
	 */
	public abstract File getIndexFile() throws NoWorkTreeException;

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
	public abstract DirCache readDirCache() throws NoWorkTreeException,
			CorruptObjectException,
			IOException;

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
	public abstract DirCache lockDirCache() throws NoWorkTreeException,
			CorruptObjectException,
			IOException;

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
		if (refName.endsWith(".lock"))
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
	 * @return true if this is bare, which implies it has no working directory.
	 */
	public abstract boolean isBare();

	/**
	 * @return the root directory of the working tree, where files are checked
	 *         out for viewing and editing.
	 * @throws NoWorkTreeException
	 *             if this is bare, which implies it has no working directory.
	 *             See {@link #isBare()}.
	 */
	public abstract File getWorkTree() throws NoWorkTreeException;

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
	 * @throws IOException the ref could not be accessed.
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
	public abstract String readMergeCommitMsg() throws IOException,
			NoWorkTreeException;

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
	public abstract void writeMergeCommitMsg(String msg) throws IOException;

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
	public abstract List<ObjectId> readMergeHeads() throws IOException,
			NoWorkTreeException;

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
	public abstract void writeMergeHeads(List<ObjectId> heads)
			throws IOException;

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
	public abstract ObjectId readCherryPickHead() throws IOException,
			NoWorkTreeException;

	/**
	 * Write cherry pick commit into $GIT_DIR/CHERRY_PICK_HEAD. This is used in
	 * case of conflicts to store the cherry which was tried to be picked.
	 *
	 * @param head
	 *            an object id of the cherry commit or <code>null</code> to
	 *            delete the file
	 * @throws IOException
	 */
	public abstract void writeCherryPickHead(ObjectId head) throws IOException;

	/**
	 * Write original HEAD commit into $GIT_DIR/ORIG_HEAD.
	 *
	 * @param head
	 *            an object id of the original HEAD commit or <code>null</code>
	 *            to delete the file
	 * @throws IOException
	 */
	public abstract void writeOrigHead(ObjectId head) throws IOException;

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
	public abstract ObjectId readOrigHead() throws IOException,
			NoWorkTreeException;

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
	public abstract String readSquashCommitMsg() throws IOException;

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
	public abstract void writeSquashCommitMsg(String msg) throws IOException;

	/* (non-Javadoc)
	 * @see org.eclipse.jgit.lib.Replacements#getGrafts()
	 */
	public abstract Map<AnyObjectId, List<ObjectId>> getGrafts()
			throws IOException;

	/* (non-Javadoc)
	 * @see org.eclipse.jgit.lib.Replacements#getReplacements()
	 */
	public abstract Map<AnyObjectId, ObjectId> getReplacements()
			throws IOException;
}

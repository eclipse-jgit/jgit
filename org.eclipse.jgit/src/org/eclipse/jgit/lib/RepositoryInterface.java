/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2006-2010, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2012, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2012, Daniel Megert <daniel_megert@ch.ibm.com>
 * Copyright (C) 2017, Wim Jongman <wim.jongman@remainsoftware.com>
 * Copyright (C) 2019, Luca Milanesio <luca.milanesio@gmail.com>
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

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.attributes.AttributesNodeProvider;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.events.ListenerList;
import org.eclipse.jgit.events.RepositoryEvent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface of a Git repository.
 * <p>
 * A repository holds all objects and refs used for managing source code (could
 * be any type of file, but source code is what SCM's are typically used for).
 * <p>
 * The thread-safety of a {@link org.eclipse.jgit.lib.RepositoryInterface} very much
 * depends on the concrete implementation. Applications working with a generic
 * {@code RepositoryInterface} type must not assume the instance is thread-safe.
 * <ul>
 * <li>{@code FileRepository} is thread-safe.
 * <li>{@code DfsRepository} thread-safety is determined by its subclass.
 * </ul>
 *
 * @since 5.2.3
 */
public interface RepositoryInterface extends AutoCloseable {


	/**
	 * Get listeners observing only events on this repository.
	 *
	 * @return listeners observing only events on this repository.
	 */
	@NonNull
	ListenerList getListenerList();

	/**
	 * Fire an event to all registered listeners.
	 * <p>
	 * The source repository of the event is automatically set to this
	 * repository, before the event is delivered to any listeners.
	 *
	 * @param event
	 *            the event to deliver.
	 */
	void fireEvent(RepositoryEvent<?> event);

	/**
	 * Create a new Git repository.
	 * <p>
	 * Repository with working tree is created using this method. This method is
	 * the same as {@code create(false)}.
	 *
	 * @throws java.io.IOException
	 * @see #create(boolean)
	 */
	void create() throws IOException;

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
	void create(boolean bare) throws IOException;

	/**
	 * Get local metadata directory
	 *
	 * @return local metadata directory; {@code null} if repository isn't local.
	 */
	/*
	 * TODO This method should be annotated as Nullable, because in some
	 * specific configurations metadata is not located in the local file system
	 * (for example in memory databases). In "usual" repositories this
	 * annotation would only cause compiler errors at places where the actual
	 * directory can never be null.
	 */
	File getDirectory();

	/**
	 * Get the object database which stores this repository's data.
	 *
	 * @return the object database which stores this repository's data.
	 */
	@NonNull
	ObjectDatabase getObjectDatabase();

	/**
	 * Create a new inserter to create objects in {@link #getObjectDatabase()}.
	 *
	 * @return a new inserter to create objects in {@link #getObjectDatabase()}.
	 */
	@NonNull
	ObjectInserter newObjectInserter();

	/**
	 * Create a new reader to read objects from {@link #getObjectDatabase()}.
	 *
	 * @return a new reader to read objects from {@link #getObjectDatabase()}.
	 */
	@NonNull
	ObjectReader newObjectReader();

	/**
	 * Get the reference database which stores the reference namespace.
	 *
	 * @return the reference database which stores the reference namespace.
	 */
	@NonNull
	RefDatabase getRefDatabase();

	/**
	 * Get the configuration of this repository.
	 *
	 * @return the configuration of this repository.
	 */
	@NonNull
	StoredConfig getConfig();

	/**
	 * Create a new {@link org.eclipse.jgit.attributes.AttributesNodeProvider}.
	 *
	 * @return a new {@link org.eclipse.jgit.attributes.AttributesNodeProvider}.
	 *         This {@link org.eclipse.jgit.attributes.AttributesNodeProvider}
	 *         is lazy loaded only once. It means that it will not be updated
	 *         after loading. Prefer creating new instance for each use.
	 * @since 4.2
	 */
	@NonNull
	AttributesNodeProvider createAttributesNodeProvider();

	/**
	 * Get the used file system abstraction.
	 *
	 * @return the used file system abstraction, or or {@code null} if
	 *         repository isn't local.
	 */
	/*
	 * TODO This method should be annotated as Nullable, because in some
	 * specific configurations metadata is not located in the local file system
	 * (for example in memory databases). In "usual" repositories this
	 * annotation would only cause compiler errors at places where the actual
	 * directory can never be null.
	 */
	FS getFS();

	/**
	 * Whether the specified object is stored in this repo or any of the known
	 * shared repositories.
	 *
	 * @param objectId
	 *            a {@link AnyObjectId} object.
	 * @return true if the specified object is stored in this repo or any of the
	 *         known shared repositories.
	 */
	boolean hasObject(AnyObjectId objectId);

	/**
	 * Open an object from this repository.
	 * <p>
	 * This is a one-shot call interface which may be faster than allocating a
	 * {@link #newObjectReader()} to perform the lookup.
	 *
	 * @param objectId
	 *            identity of the object to open.
	 * @return a {@link ObjectLoader} for accessing the
	 *         object.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             the object does not exist.
	 * @throws IOException
	 *             the object store cannot be accessed.
	 */
	@NonNull
	ObjectLoader open(AnyObjectId objectId)
			throws MissingObjectException, IOException;

	/**
	 * Open an object from this repository.
	 * <p>
	 * This is a one-shot call interface which may be faster than allocating a
	 * {@link #newObjectReader()} to perform the lookup.
	 *
	 * @param objectId
	 *            identity of the object to open.
	 * @param typeHint
	 *            hint about the type of object being requested, e.g.
	 *            {@link Constants#OBJ_BLOB};
	 *            {@link ObjectReader#OBJ_ANY} if the
	 *            object type is not known, or does not matter to the caller.
	 * @return a {@link ObjectLoader} for accessing the
	 *         object.
	 * @throws MissingObjectException
	 *             the object does not exist.
	 * @throws org.eclipse.jgit.errors.IncorrectObjectTypeException
	 *             typeHint was not OBJ_ANY, and the object's actual type does
	 *             not match typeHint.
	 * @throws IOException
	 *             the object store cannot be accessed.
	 */
	@NonNull
	ObjectLoader open(AnyObjectId objectId, int typeHint)
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
	@NonNull
	RefUpdate updateRef(String ref) throws IOException;

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
	@NonNull
	RefUpdate updateRef(String ref, boolean detach) throws IOException;

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
	 */
	@NonNull
	RefRename renameRef(String fromRef, String toRef) throws IOException;

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
	 * @return an ObjectId or {@code null} if revstr can't be resolved to any
	 *         ObjectId
	 * @throws org.eclipse.jgit.errors.AmbiguousObjectException
	 *             {@code revstr} contains an abbreviated ObjectId and this
	 *             repository contains more than one object which match to the
	 *             input abbreviation.
	 * @throws IncorrectObjectTypeException
	 *             the id parsed does not meet the type required to finish
	 *             applying the operators in the expression.
	 * @throws org.eclipse.jgit.errors.RevisionSyntaxException
	 *             the expression is not supported by this implementation, or
	 *             does not meet the standard syntax.
	 * @throws IOException
	 *             on serious errors
	 */
	@Nullable
	ObjectId resolve(String revstr)
			throws AmbiguousObjectException, IncorrectObjectTypeException,
			RevisionSyntaxException, IOException;

	/**
	 * Simplify an expression, but unlike {@link #resolve(String)} it will not
	 * resolve a branch passed or resulting from the expression, such as @{-}.
	 * Thus this method can be used to process an expression to a method that
	 * expects a branch or revision id.
	 *
	 * @param revstr a {@link String} object.
	 * @return object id or ref name from resolved expression or {@code null} if
	 *         given expression cannot be resolved
	 * @throws AmbiguousObjectException
	 * @throws IOException
	 */
	@Nullable
	String simplify(String revstr)
			throws AmbiguousObjectException, IOException;

	/**
	 * Increment the use counter by one, requiring a matched {@link #close()}.
	 */
	void incrementOpen();

	/**
	 * {@inheritDoc}
	 * <p>
	 * Decrement the use count, and maybe close resources.
	 */
	@Override
	void close();

	/** {@inheritDoc} */
	@Override
	@NonNull
	String toString();

	/**
	 * Get the name of the reference that {@code HEAD} points to.
	 * <p>
	 * This is essentially the same as doing:
	 *
	 * <pre>
	 * return exactRef(Constants.HEAD).getTarget().getName()
	 * </pre>
	 *
	 * Except when HEAD is detached, in which case this method returns the
	 * current ObjectId in hexadecimal string format.
	 *
	 * @return name of current branch (for example {@code refs/heads/master}),
	 *         an ObjectId in hex format if the current branch is detached, or
	 *         {@code null} if the repository is corrupt and has no HEAD
	 *         reference.
	 * @throws IOException
	 */
	@Nullable
	String getFullBranch() throws IOException;

	/**
	 * Get the short name of the current branch that {@code HEAD} points to.
	 * <p>
	 * This is essentially the same as {@link #getFullBranch()}, except the
	 * leading prefix {@code refs/heads/} is removed from the reference before
	 * it is returned to the caller.
	 *
	 * @return name of current branch (for example {@code master}), an ObjectId
	 *         in hex format if the current branch is detached, or {@code null}
	 *         if the repository is corrupt and has no HEAD reference.
	 * @throws IOException
	 */
	@Nullable
	String getBranch() throws IOException;

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
	@NonNull
	Set<ObjectId> getAdditionalHaves();

	/**
	 * Get a ref by name.
	 *
	 * @param name
	 *            the name of the ref to lookup. Must not be a short-hand
	 *            form; e.g., "master" is not automatically expanded to
	 *            "refs/heads/master".
	 * @return the Ref with the given name, or {@code null} if it does not exist
	 * @throws IOException
	 * @since 4.2
	 */
	@Nullable
	Ref exactRef(String name) throws IOException;

	/**
	 * Search for a ref by (possibly abbreviated) name.
	 *
	 * @param name
	 *            the name of the ref to lookup. May be a short-hand form, e.g.
	 *            "master" which is is automatically expanded to
	 *            "refs/heads/master" if "refs/heads/master" already exists.
	 * @return the Ref with the given name, or {@code null} if it does not exist
	 * @throws IOException
	 * @since 4.2
	 */
	@Nullable
	Ref findRef(String name) throws IOException;

	/**
	 * Get mutable map of all known refs, including symrefs like HEAD that may
	 * not point to any object yet.
	 *
	 * @return mutable map of all known refs (heads, tags, remotes).
	 * @deprecated use {@code getRefDatabase().getRefs()} instead.
	 */
	@Deprecated
	@NonNull
	Map<String, Ref> getAllRefs();

	/**
	 * Get mutable map of all tags
	 *
	 * @return mutable map of all tags; key is short tag name ("v1.0") and value
	 *         of the entry contains the ref with the full tag name
	 *         ("refs/tags/v1.0").
	 * @deprecated use {@code getRefDatabase().getRefsByPrefix(R_TAGS)} instead
	 */
	@Deprecated
	@NonNull
	Map<String, Ref> getTags();

	/**
	 * Peel a possibly unpeeled reference to an annotated tag.
	 * <p>
	 * If the ref cannot be peeled (as it does not refer to an annotated tag)
	 * the peeled id stays null, but {@link Ref#isPeeled()}
	 * will be true.
	 *
	 * @param ref
	 *            The ref to peel
	 * @return <code>ref</code> if <code>ref.isPeeled()</code> is true; else a
	 *         new Ref object representing the same data as Ref, but isPeeled()
	 *         will be true and getPeeledObjectId will contain the peeled object
	 *         (or null).
	 * @deprecated use {@code getRefDatabase().peel(ref)} instead.
	 */
	@Deprecated
	@NonNull
	Ref peel(Ref ref);

	/**
	 * Get a map with all objects referenced by a peeled ref.
	 *
	 * @return a map with all objects referenced by a peeled ref.
	 */
	@NonNull
	Map<AnyObjectId, Set<Ref>> getAllRefsByPeeledObjectId();

	/**
	 * Get the index file location or {@code null} if repository isn't local.
	 *
	 * @return the index file location or {@code null} if repository isn't
	 *         local.
	 * @throws org.eclipse.jgit.errors.NoWorkTreeException
	 *             if this is bare, which implies it has no working directory.
	 *             See {@link #isBare()}.
	 */
	@NonNull
	File getIndexFile() throws NoWorkTreeException;

	/**
	 * Locate a reference to a commit and immediately parse its content.
	 * <p>
	 * This method only returns successfully if the commit object exists,
	 * is verified to be a commit, and was parsed without error.
	 *
	 * @param id
	 *            name of the commit object.
	 * @return reference to the commit object. Never null.
	 * @throws MissingObjectException
	 *             the supplied commit does not exist.
	 * @throws IncorrectObjectTypeException
	 *             the supplied id is not a commit or an annotated tag.
	 * @throws IOException
	 *             a pack file or loose object could not be read.
	 * @since 4.8
	 */
	RevCommit parseCommit(AnyObjectId id) throws IncorrectObjectTypeException,
			IOException, MissingObjectException;

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
	 * @throws org.eclipse.jgit.errors.CorruptObjectException
	 *             the index file is using a format or extension that this
	 *             library does not support.
	 */
	@NonNull
	DirCache readDirCache() throws NoWorkTreeException,
			CorruptObjectException, IOException;

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
	@NonNull
	DirCache lockDirCache() throws NoWorkTreeException,
			CorruptObjectException, IOException;

	/**
	 * Get the repository state
	 *
	 * @return the repository state
	 */
	@NonNull
	RepositoryState getRepositoryState();

	/**
	 * Whether this repository is bare
	 *
	 * @return true if this is bare, which implies it has no working directory.
	 */
	boolean isBare();

	/**
	 * Get the root directory of the working tree, where files are checked out
	 * for viewing and editing.
	 *
	 * @return the root directory of the working tree, where files are checked
	 *         out for viewing and editing.
	 * @throws NoWorkTreeException
	 *             if this is bare, which implies it has no working directory.
	 *             See {@link #isBare()}.
	 */
	@NonNull
	File getWorkTree() throws NoWorkTreeException;

	/**
	 * Force a scan for changed refs. Fires an IndexChangedEvent(false) if
	 * changes are detected.
	 *
	 * @throws IOException
	 */
	void scanForRepoChanges() throws IOException;

	/**
	 * Notify that the index changed by firing an IndexChangedEvent.
	 *
	 * @param internal
	 *                     {@code true} if the index was changed by the same
	 *                     JGit process
	 * @since 5.0
	 */
	void notifyIndexChanged(boolean internal);

	/**
	 * Get a shortened more user friendly remote tracking branch name
	 *
	 * @param refName
	 *            a {@link String} object.
	 * @return the remote branch name part of <code>refName</code>, i.e. without
	 *         the <code>refs/remotes/&lt;remote&gt;</code> prefix, if
	 *         <code>refName</code> represents a remote tracking branch;
	 *         otherwise {@code null}.
	 * @since 3.4
	 */
	@Nullable
	String shortenRemoteBranchName(String refName);

	/**
	 * Get remote name
	 *
	 * @param refName
	 *            a {@link String} object.
	 * @return the remote name part of <code>refName</code>, i.e. without the
	 *         <code>refs/remotes/&lt;remote&gt;</code> prefix, if
	 *         <code>refName</code> represents a remote tracking branch;
	 *         otherwise {@code null}.
	 * @since 3.4
	 */
	@Nullable
	String getRemoteName(String refName);

	/**
	 * Read the {@code GIT_DIR/description} file for gitweb.
	 *
	 * @return description text; null if no description has been configured.
	 * @throws IOException
	 *             description cannot be accessed.
	 * @since 4.6
	 */
	@Nullable
	String getGitwebDescription() throws IOException;

	/**
	 * Set the {@code GIT_DIR/description} file for gitweb.
	 *
	 * @param description
	 *            new description; null to clear the description.
	 * @throws IOException
	 *             description cannot be persisted.
	 * @since 4.6
	 */
	void setGitwebDescription(@Nullable String description)
			throws IOException;

	/**
	 * Get the reflog reader
	 *
	 * @param refName
	 *            a {@link String} object.
	 * @return a {@link ReflogReader} for the supplied
	 *         refname, or {@code null} if the named ref does not exist.
	 * @throws IOException
	 *             the ref could not be accessed.
	 * @since 3.0
	 */
	@Nullable
	ReflogReader getReflogReader(String refName)
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
	@Nullable
	String readMergeCommitMsg() throws IOException, NoWorkTreeException;

	/**
	 * Write new content to the file $GIT_DIR/MERGE_MSG. In this file operations
	 * triggering a merge will store a template for the commit message of the
	 * merge commit. If <code>null</code> is specified as message the file will
	 * be deleted.
	 *
	 * @param msg
	 *            the message which should be written or <code>null</code> to
	 *            delete the file
	 * @throws IOException
	 */
	void writeMergeCommitMsg(String msg) throws IOException;

	/**
	 * Return the information stored in the file $GIT_DIR/COMMIT_EDITMSG. In
	 * this file hooks triggered by an operation may read or modify the current
	 * commit message.
	 *
	 * @return a String containing the content of the COMMIT_EDITMSG file or
	 *         {@code null} if this file doesn't exist
	 * @throws IOException
	 * @throws NoWorkTreeException
	 *             if this is bare, which implies it has no working directory.
	 *             See {@link #isBare()}.
	 * @since 4.0
	 */
	@Nullable
	String readCommitEditMsg() throws IOException, NoWorkTreeException;

	/**
	 * Write new content to the file $GIT_DIR/COMMIT_EDITMSG. In this file hooks
	 * triggered by an operation may read or modify the current commit message.
	 * If {@code null} is specified as message the file will be deleted.
	 *
	 * @param msg
	 *            the message which should be written or {@code null} to delete
	 *            the file
	 * @throws IOException
	 * @since 4.0
	 */
	void writeCommitEditMsg(String msg) throws IOException;

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
	@Nullable
	List<ObjectId> readMergeHeads() throws IOException, NoWorkTreeException;

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
	void writeMergeHeads(List<? extends ObjectId> heads) throws IOException;

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
	@Nullable
	ObjectId readCherryPickHead() throws IOException,
			NoWorkTreeException;

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
	@Nullable
	ObjectId readRevertHead() throws IOException, NoWorkTreeException;

	/**
	 * Write cherry pick commit into $GIT_DIR/CHERRY_PICK_HEAD. This is used in
	 * case of conflicts to store the cherry which was tried to be picked.
	 *
	 * @param head
	 *            an object id of the cherry commit or <code>null</code> to
	 *            delete the file
	 * @throws IOException
	 */
	void writeCherryPickHead(ObjectId head) throws IOException;

	/**
	 * Write revert commit into $GIT_DIR/REVERT_HEAD. This is used in case of
	 * conflicts to store the revert which was tried to be picked.
	 *
	 * @param head
	 *            an object id of the revert commit or <code>null</code> to
	 *            delete the file
	 * @throws IOException
	 */
	void writeRevertHead(ObjectId head) throws IOException;

	/**
	 * Write original HEAD commit into $GIT_DIR/ORIG_HEAD.
	 *
	 * @param head
	 *            an object id of the original HEAD commit or <code>null</code>
	 *            to delete the file
	 * @throws IOException
	 */
	void writeOrigHead(ObjectId head) throws IOException;

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
	@Nullable
	ObjectId readOrigHead() throws IOException, NoWorkTreeException;

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
	@Nullable
	String readSquashCommitMsg() throws IOException;

	/**
	 * Write new content to the file $GIT_DIR/SQUASH_MSG. In this file
	 * operations triggering a squashed merge will store a template for the
	 * commit message of the squash commit. If <code>null</code> is specified as
	 * message the file will be deleted.
	 *
	 * @param msg
	 *            the message which should be written or <code>null</code> to
	 *            delete the file
	 * @throws IOException
	 */
	void writeSquashCommitMsg(String msg) throws IOException;

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
	 * @since 3.2
	 */
	@NonNull
	List<RebaseTodoLine> readRebaseTodo(String path,
										boolean includeComments)
			throws IOException;

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
	 * @since 3.2
	 */
	void writeRebaseTodoFile(String path, List<RebaseTodoLine> steps,
							 boolean append)
			throws IOException;

	/**
	 * Get the names of all known remotes
	 *
	 * @return the names of all known remotes
	 * @since 3.4
	 */
	@NonNull
	Set<String> getRemoteNames();

	/**
	 * Check whether any housekeeping is required; if yes, run garbage
	 * collection; if not, exit without performing any work. Some JGit commands
	 * run autoGC after performing operations that could create many loose
	 * objects.
	 * <p>
	 * Currently this option is supported for repositories of type
	 * {@code FileRepository} only. See
	 * {@link org.eclipse.jgit.internal.storage.file.GC#setAuto(boolean)} for
	 * configuration details.
	 *
	 * @param monitor
	 *            to report progress
	 * @since 4.6
	 */
	void autoGC(ProgressMonitor monitor);
}

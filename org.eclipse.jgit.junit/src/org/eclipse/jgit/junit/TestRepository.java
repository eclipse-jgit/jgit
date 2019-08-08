/*
 * Copyright (C) 2009-2010, Google Inc.
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

package org.eclipse.jgit.junit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.DeletePath;
import org.eclipse.jgit.dircache.DirCacheEditor.DeleteTree;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.ObjectWritingException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.internal.storage.file.ObjectDirectory;
import org.eclipse.jgit.internal.storage.file.PackFile;
import org.eclipse.jgit.internal.storage.file.PackIndex.MutableEntry;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefWriter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TagBuilder;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.util.ChangeIdUtil;
import org.eclipse.jgit.util.FileUtils;

/**
 * Wrapper to make creating test data easier.
 *
 * @param <R>
 *            type of Repository the test data is stored on.
 */
public class TestRepository<R extends Repository> implements AutoCloseable {

	/** Constant <code>AUTHOR="J. Author"</code> */
	public static final String AUTHOR = "J. Author";

	/** Constant <code>AUTHOR_EMAIL="jauthor@example.com"</code> */
	public static final String AUTHOR_EMAIL = "jauthor@example.com";

	/** Constant <code>COMMITTER="J. Committer"</code> */
	public static final String COMMITTER = "J. Committer";

	/** Constant <code>COMMITTER_EMAIL="jcommitter@example.com"</code> */
	public static final String COMMITTER_EMAIL = "jcommitter@example.com";

	private final PersonIdent defaultAuthor;

	private final PersonIdent defaultCommitter;

	private final R db;

	private final Git git;

	private final RevWalk pool;

	private final ObjectInserter inserter;

	private final MockSystemReader mockSystemReader;

	/**
	 * Wrap a repository with test building tools.
	 *
	 * @param db
	 *            the test repository to write into.
	 * @throws IOException
	 */
	public TestRepository(R db) throws IOException {
		this(db, new RevWalk(db), new MockSystemReader());
	}

	/**
	 * Wrap a repository with test building tools.
	 *
	 * @param db
	 *            the test repository to write into.
	 * @param rw
	 *            the RevObject pool to use for object lookup.
	 * @throws IOException
	 */
	public TestRepository(R db, RevWalk rw) throws IOException {
		this(db, rw, new MockSystemReader());
	}

	/**
	 * Wrap a repository with test building tools.
	 *
	 * @param db
	 *            the test repository to write into.
	 * @param rw
	 *            the RevObject pool to use for object lookup.
	 * @param reader
	 *            the MockSystemReader to use for clock and other system
	 *            operations.
	 * @throws IOException
	 * @since 4.2
	 */
	public TestRepository(R db, RevWalk rw, MockSystemReader reader)
			throws IOException {
		this.db = db;
		this.git = Git.wrap(db);
		this.pool = rw;
		this.inserter = db.newObjectInserter();
		this.mockSystemReader = reader;
		long now = mockSystemReader.getCurrentTime();
		int tz = mockSystemReader.getTimezone(now);
		defaultAuthor = new PersonIdent(AUTHOR, AUTHOR_EMAIL, now, tz);
		defaultCommitter = new PersonIdent(COMMITTER, COMMITTER_EMAIL, now, tz);
	}

	/**
	 * Get repository
	 *
	 * @return the repository this helper class operates against.
	 */
	public R getRepository() {
		return db;
	}

	/**
	 * Get RevWalk
	 *
	 * @return get the RevWalk pool all objects are allocated through.
	 */
	public RevWalk getRevWalk() {
		return pool;
	}

	/**
	 * Return Git API wrapper
	 *
	 * @return an API wrapper for the underlying repository. This wrapper does
	 *         not allocate any new resources and need not be closed (but
	 *         closing it is harmless).
	 */
	public Git git() {
		return git;
	}

	/**
	 * Get date
	 *
	 * @return current date.
	 * @since 4.2
	 */
	public Date getDate() {
		return new Date(mockSystemReader.getCurrentTime());
	}

	/**
	 * Get timezone
	 *
	 * @return timezone used for default identities.
	 */
	public TimeZone getTimeZone() {
		return mockSystemReader.getTimeZone();
	}

	/**
	 * Adjust the current time that will used by the next commit.
	 *
	 * @param secDelta
	 *            number of seconds to add to the current time.
	 */
	public void tick(int secDelta) {
		mockSystemReader.tick(secDelta);
	}

	/**
	 * Set the author and committer using {@link #getDate()}.
	 *
	 * @param c
	 *            the commit builder to store.
	 */
	public void setAuthorAndCommitter(org.eclipse.jgit.lib.CommitBuilder c) {
		c.setAuthor(new PersonIdent(defaultAuthor, getDate()));
		c.setCommitter(new PersonIdent(defaultCommitter, getDate()));
	}

	/**
	 * Create a new blob object in the repository.
	 *
	 * @param content
	 *            file content, will be UTF-8 encoded.
	 * @return reference to the blob.
	 * @throws Exception
	 */
	public RevBlob blob(String content) throws Exception {
		return blob(content.getBytes(UTF_8));
	}

	/**
	 * Create a new blob object in the repository.
	 *
	 * @param content
	 *            binary file content.
	 * @return the new, fully parsed blob.
	 * @throws Exception
	 */
	public RevBlob blob(byte[] content) throws Exception {
		ObjectId id;
		try (ObjectInserter ins = inserter) {
			id = ins.insert(Constants.OBJ_BLOB, content);
			ins.flush();
		}
		return (RevBlob) pool.parseAny(id);
	}

	/**
	 * Construct a regular file mode tree entry.
	 *
	 * @param path
	 *            path of the file.
	 * @param blob
	 *            a blob, previously constructed in the repository.
	 * @return the entry.
	 * @throws Exception
	 */
	public DirCacheEntry file(String path, RevBlob blob)
			throws Exception {
		final DirCacheEntry e = new DirCacheEntry(path);
		e.setFileMode(FileMode.REGULAR_FILE);
		e.setObjectId(blob);
		return e;
	}

	/**
	 * Construct a tree from a specific listing of file entries.
	 *
	 * @param entries
	 *            the files to include in the tree. The collection does not need
	 *            to be sorted properly and may be empty.
	 * @return the new, fully parsed tree specified by the entry list.
	 * @throws Exception
	 */
	public RevTree tree(DirCacheEntry... entries) throws Exception {
		final DirCache dc = DirCache.newInCore();
		final DirCacheBuilder b = dc.builder();
		for (DirCacheEntry e : entries) {
			b.add(e);
		}
		b.finish();
		ObjectId root;
		try (ObjectInserter ins = inserter) {
			root = dc.writeTree(ins);
			ins.flush();
		}
		return pool.parseTree(root);
	}

	/**
	 * Lookup an entry stored in a tree, failing if not present.
	 *
	 * @param tree
	 *            the tree to search.
	 * @param path
	 *            the path to find the entry of.
	 * @return the parsed object entry at this path, never null.
	 * @throws Exception
	 */
	public RevObject get(RevTree tree, String path)
			throws Exception {
		try (TreeWalk tw = new TreeWalk(pool.getObjectReader())) {
			tw.setFilter(PathFilterGroup.createFromStrings(Collections
					.singleton(path)));
			tw.reset(tree);
			while (tw.next()) {
				if (tw.isSubtree() && !path.equals(tw.getPathString())) {
					tw.enterSubtree();
					continue;
				}
				final ObjectId entid = tw.getObjectId(0);
				final FileMode entmode = tw.getFileMode(0);
				return pool.lookupAny(entid, entmode.getObjectType());
			}
		}
		fail("Can't find " + path + " in tree " + tree.name());
		return null; // never reached.
	}

	/**
	 * Create a new commit.
	 * <p>
	 * See {@link #commit(int, RevTree, RevCommit...)}. The tree is the empty
	 * tree (no files or subdirectories).
	 *
	 * @param parents
	 *            zero or more parents of the commit.
	 * @return the new commit.
	 * @throws Exception
	 */
	public RevCommit commit(RevCommit... parents) throws Exception {
		return commit(1, tree(), parents);
	}

	/**
	 * Create a new commit.
	 * <p>
	 * See {@link #commit(int, RevTree, RevCommit...)}.
	 *
	 * @param tree
	 *            the root tree for the commit.
	 * @param parents
	 *            zero or more parents of the commit.
	 * @return the new commit.
	 * @throws Exception
	 */
	public RevCommit commit(RevTree tree, RevCommit... parents)
			throws Exception {
		return commit(1, tree, parents);
	}

	/**
	 * Create a new commit.
	 * <p>
	 * See {@link #commit(int, RevTree, RevCommit...)}. The tree is the empty
	 * tree (no files or subdirectories).
	 *
	 * @param secDelta
	 *            number of seconds to advance {@link #tick(int)} by.
	 * @param parents
	 *            zero or more parents of the commit.
	 * @return the new commit.
	 * @throws Exception
	 */
	public RevCommit commit(int secDelta, RevCommit... parents)
			throws Exception {
		return commit(secDelta, tree(), parents);
	}

	/**
	 * Create a new commit.
	 * <p>
	 * The author and committer identities are stored using the current
	 * timestamp, after being incremented by {@code secDelta}. The message body
	 * is empty.
	 *
	 * @param secDelta
	 *            number of seconds to advance {@link #tick(int)} by.
	 * @param tree
	 *            the root tree for the commit.
	 * @param parents
	 *            zero or more parents of the commit.
	 * @return the new, fully parsed commit.
	 * @throws Exception
	 */
	public RevCommit commit(final int secDelta, final RevTree tree,
			final RevCommit... parents) throws Exception {
		tick(secDelta);

		final org.eclipse.jgit.lib.CommitBuilder c;

		c = new org.eclipse.jgit.lib.CommitBuilder();
		c.setTreeId(tree);
		c.setParentIds(parents);
		c.setAuthor(new PersonIdent(defaultAuthor, getDate()));
		c.setCommitter(new PersonIdent(defaultCommitter, getDate()));
		c.setMessage("");
		ObjectId id;
		try (ObjectInserter ins = inserter) {
			id = ins.insert(c);
			ins.flush();
		}
		return pool.parseCommit(id);
	}

	/**
	 * Create commit builder
	 *
	 * @return a new commit builder.
	 */
	public CommitBuilder commit() {
		return new CommitBuilder();
	}

	/**
	 * Construct an annotated tag object pointing at another object.
	 * <p>
	 * The tagger is the committer identity, at the current time as specified by
	 * {@link #tick(int)}. The time is not increased.
	 * <p>
	 * The tag message is empty.
	 *
	 * @param name
	 *            name of the tag. Traditionally a tag name should not start
	 *            with {@code refs/tags/}.
	 * @param dst
	 *            object the tag should be pointed at.
	 * @return the new, fully parsed annotated tag object.
	 * @throws Exception
	 */
	public RevTag tag(String name, RevObject dst) throws Exception {
		final TagBuilder t = new TagBuilder();
		t.setObjectId(dst);
		t.setTag(name);
		t.setTagger(new PersonIdent(defaultCommitter, getDate()));
		t.setMessage("");
		ObjectId id;
		try (ObjectInserter ins = inserter) {
			id = ins.insert(t);
			ins.flush();
		}
		return pool.parseTag(id);
	}

	/**
	 * Update a reference to point to an object.
	 *
	 * @param ref
	 *            the name of the reference to update to. If {@code ref} does
	 *            not start with {@code refs/} and is not the magic names
	 *            {@code HEAD} {@code FETCH_HEAD} or {@code MERGE_HEAD}, then
	 *            {@code refs/heads/} will be prefixed in front of the given
	 *            name, thereby assuming it is a branch.
	 * @param to
	 *            the target object.
	 * @return the target object.
	 * @throws Exception
	 */
	public RevCommit update(String ref, CommitBuilder to) throws Exception {
		return update(ref, to.create());
	}

	/**
	 * Amend an existing ref.
	 *
	 * @param ref
	 *            the name of the reference to amend, which must already exist.
	 *            If {@code ref} does not start with {@code refs/} and is not the
	 *            magic names {@code HEAD} {@code FETCH_HEAD} or {@code
	 *            MERGE_HEAD}, then {@code refs/heads/} will be prefixed in front
	 *            of the given name, thereby assuming it is a branch.
	 * @return commit builder that amends the branch on commit.
	 * @throws Exception
	 */
	public CommitBuilder amendRef(String ref) throws Exception {
		String name = normalizeRef(ref);
		Ref r = db.exactRef(name);
		if (r == null)
			throw new IOException("Not a ref: " + ref);
		return amend(pool.parseCommit(r.getObjectId()), branch(name).commit());
	}

	/**
	 * Amend an existing commit.
	 *
	 * @param id
	 *            the id of the commit to amend.
	 * @return commit builder.
	 * @throws Exception
	 */
	public CommitBuilder amend(AnyObjectId id) throws Exception {
		return amend(pool.parseCommit(id), commit());
	}

	private CommitBuilder amend(RevCommit old, CommitBuilder b) throws Exception {
		pool.parseBody(old);
		b.author(old.getAuthorIdent());
		b.committer(old.getCommitterIdent());
		b.message(old.getFullMessage());
		// Use the committer name from the old commit, but update it after ticking
		// the clock in CommitBuilder#create().
		b.updateCommitterTime = true;

		// Reset parents to original parents.
		b.noParents();
		for (int i = 0; i < old.getParentCount(); i++)
			b.parent(old.getParent(i));

		// Reset tree to original tree; resetting parents reset tree contents to the
		// first parent.
		b.tree.clear();
		try (TreeWalk tw = new TreeWalk(db)) {
			tw.reset(old.getTree());
			tw.setRecursive(true);
			while (tw.next()) {
				b.edit(new PathEdit(tw.getPathString()) {
					@Override
					public void apply(DirCacheEntry ent) {
						ent.setFileMode(tw.getFileMode(0));
						ent.setObjectId(tw.getObjectId(0));
					}
				});
			}
		}

		return b;
	}

	/**
	 * Update a reference to point to an object.
	 *
	 * @param <T>
	 *            type of the target object.
	 * @param ref
	 *            the name of the reference to update to. If {@code ref} does
	 *            not start with {@code refs/} and is not the magic names
	 *            {@code HEAD} {@code FETCH_HEAD} or {@code MERGE_HEAD}, then
	 *            {@code refs/heads/} will be prefixed in front of the given
	 *            name, thereby assuming it is a branch.
	 * @param obj
	 *            the target object.
	 * @return the target object.
	 * @throws Exception
	 */
	public <T extends AnyObjectId> T update(String ref, T obj) throws Exception {
		ref = normalizeRef(ref);
		RefUpdate u = db.updateRef(ref);
		u.setNewObjectId(obj);
		switch (u.forceUpdate()) {
		case FAST_FORWARD:
		case FORCED:
		case NEW:
		case NO_CHANGE:
			updateServerInfo();
			return obj;

		default:
			throw new IOException("Cannot write " + ref + " " + u.getResult());
		}
	}

	/**
	 * Delete a reference.
	 *
	 * @param ref
	 *	      the name of the reference to delete. This is normalized
	 *	      in the same way as {@link #update(String, AnyObjectId)}.
	 * @throws Exception
	 * @since 4.4
	 */
	public void delete(String ref) throws Exception {
		ref = normalizeRef(ref);
		RefUpdate u = db.updateRef(ref);
		u.setForceUpdate(true);
		switch (u.delete()) {
		case FAST_FORWARD:
		case FORCED:
		case NEW:
		case NO_CHANGE:
			updateServerInfo();
			return;

		default:
			throw new IOException("Cannot delete " + ref + " " + u.getResult());
		}
	}

	private static String normalizeRef(String ref) {
		if (Constants.HEAD.equals(ref)) {
			// nothing
		} else if ("FETCH_HEAD".equals(ref)) {
			// nothing
		} else if ("MERGE_HEAD".equals(ref)) {
			// nothing
		} else if (ref.startsWith(Constants.R_REFS)) {
			// nothing
		} else
			ref = Constants.R_HEADS + ref;
		return ref;
	}

	/**
	 * Soft-reset HEAD to a detached state.
	 *
	 * @param id
	 *            ID of detached head.
	 * @throws Exception
	 * @see #reset(String)
	 */
	public void reset(AnyObjectId id) throws Exception {
		RefUpdate ru = db.updateRef(Constants.HEAD, true);
		ru.setNewObjectId(id);
		RefUpdate.Result result = ru.forceUpdate();
		switch (result) {
			case FAST_FORWARD:
			case FORCED:
			case NEW:
			case NO_CHANGE:
				break;
			default:
				throw new IOException(String.format(
						"Checkout \"%s\" failed: %s", id.name(), result));
		}
	}

	/**
	 * Soft-reset HEAD to a different commit.
	 * <p>
	 * This is equivalent to {@code git reset --soft} in that it modifies HEAD but
	 * not the index or the working tree of a non-bare repository.
	 *
	 * @param name
	 *            revision string; either an existing ref name, or something that
	 *            can be parsed to an object ID.
	 * @throws Exception
	 */
	public void reset(String name) throws Exception {
		RefUpdate.Result result;
		ObjectId id = db.resolve(name);
		if (id == null)
			throw new IOException("Not a revision: " + name);
		RefUpdate ru = db.updateRef(Constants.HEAD, false);
		ru.setNewObjectId(id);
		result = ru.forceUpdate();
		switch (result) {
			case FAST_FORWARD:
			case FORCED:
			case NEW:
			case NO_CHANGE:
				break;
			default:
				throw new IOException(String.format(
						"Checkout \"%s\" failed: %s", name, result));
		}
	}

	/**
	 * Cherry-pick a commit onto HEAD.
	 * <p>
	 * This differs from {@code git cherry-pick} in that it works in a bare
	 * repository. As a result, any merge failure results in an exception, as
	 * there is no way to recover.
	 *
	 * @param id
	 *            commit-ish to cherry-pick.
	 * @return the new, fully parsed commit, or null if no work was done due to
	 *         the resulting tree being identical.
	 * @throws Exception
	 */
	public RevCommit cherryPick(AnyObjectId id) throws Exception {
		RevCommit commit = pool.parseCommit(id);
		pool.parseBody(commit);
		if (commit.getParentCount() != 1)
			throw new IOException(String.format(
					"Expected 1 parent for %s, found: %s",
					id.name(), Arrays.asList(commit.getParents())));
		RevCommit parent = commit.getParent(0);
		pool.parseHeaders(parent);

		Ref headRef = db.exactRef(Constants.HEAD);
		if (headRef == null)
			throw new IOException("Missing HEAD");
		RevCommit head = pool.parseCommit(headRef.getObjectId());

		ThreeWayMerger merger = MergeStrategy.RECURSIVE.newMerger(db, true);
		merger.setBase(parent.getTree());
		if (merger.merge(head, commit)) {
			if (AnyObjectId.equals(head.getTree(), merger.getResultTreeId()))
				return null;
			tick(1);
			org.eclipse.jgit.lib.CommitBuilder b =
					new org.eclipse.jgit.lib.CommitBuilder();
			b.setParentId(head);
			b.setTreeId(merger.getResultTreeId());
			b.setAuthor(commit.getAuthorIdent());
			b.setCommitter(new PersonIdent(defaultCommitter, getDate()));
			b.setMessage(commit.getFullMessage());
			ObjectId result;
			try (ObjectInserter ins = inserter) {
				result = ins.insert(b);
				ins.flush();
			}
			update(Constants.HEAD, result);
			return pool.parseCommit(result);
		} else {
			throw new IOException("Merge conflict");
		}
	}

	/**
	 * Update the dumb client server info files.
	 *
	 * @throws Exception
	 */
	public void updateServerInfo() throws Exception {
		if (db instanceof FileRepository) {
			final FileRepository fr = (FileRepository) db;
			RefWriter rw = new RefWriter(fr.getRefDatabase().getRefs()) {
				@Override
				protected void writeFile(String name, byte[] bin)
						throws IOException {
					File path = new File(fr.getDirectory(), name);
					TestRepository.this.writeFile(path, bin);
				}
			};
			rw.writePackedRefs();
			rw.writeInfoRefs();

			final StringBuilder w = new StringBuilder();
			for (PackFile p : fr.getObjectDatabase().getPacks()) {
				w.append("P ");
				w.append(p.getPackFile().getName());
				w.append('\n');
			}
			writeFile(new File(new File(fr.getObjectDatabase().getDirectory(),
					"info"), "packs"), Constants.encodeASCII(w.toString()));
		}
	}

	/**
	 * Ensure the body of the given object has been parsed.
	 *
	 * @param <T>
	 *            type of object, e.g. {@link org.eclipse.jgit.revwalk.RevTag}
	 *            or {@link org.eclipse.jgit.revwalk.RevCommit}.
	 * @param object
	 *            reference to the (possibly unparsed) object to force body
	 *            parsing of.
	 * @return {@code object}
	 * @throws Exception
	 */
	public <T extends RevObject> T parseBody(T object) throws Exception {
		pool.parseBody(object);
		return object;
	}

	/**
	 * Create a new branch builder for this repository.
	 *
	 * @param ref
	 *            name of the branch to be constructed. If {@code ref} does not
	 *            start with {@code refs/} the prefix {@code refs/heads/} will
	 *            be added.
	 * @return builder for the named branch.
	 */
	public BranchBuilder branch(String ref) {
		if (Constants.HEAD.equals(ref)) {
			// nothing
		} else if (ref.startsWith(Constants.R_REFS)) {
			// nothing
		} else
			ref = Constants.R_HEADS + ref;
		return new BranchBuilder(ref);
	}

	/**
	 * Tag an object using a lightweight tag.
	 *
	 * @param name
	 *            the tag name. The /refs/tags/ prefix will be added if the name
	 *            doesn't start with it
	 * @param obj
	 *            the object to tag
	 * @return the tagged object
	 * @throws Exception
	 */
	public ObjectId lightweightTag(String name, ObjectId obj) throws Exception {
		if (!name.startsWith(Constants.R_TAGS))
			name = Constants.R_TAGS + name;
		return update(name, obj);
	}

	/**
	 * Run consistency checks against the object database.
	 * <p>
	 * This method completes silently if the checks pass. A temporary revision
	 * pool is constructed during the checking.
	 *
	 * @param tips
	 *            the tips to start checking from; if not supplied the refs of
	 *            the repository are used instead.
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws IOException
	 */
	public void fsck(RevObject... tips) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		try (ObjectWalk ow = new ObjectWalk(db)) {
			if (tips.length != 0) {
				for (RevObject o : tips)
					ow.markStart(ow.parseAny(o));
			} else {
				for (Ref r : db.getRefDatabase().getRefs())
					ow.markStart(ow.parseAny(r.getObjectId()));
			}

			ObjectChecker oc = new ObjectChecker();
			for (;;) {
				final RevCommit o = ow.next();
				if (o == null)
					break;

				final byte[] bin = db.open(o, o.getType()).getCachedBytes();
				oc.checkCommit(o, bin);
				assertHash(o, bin);
			}

			for (;;) {
				final RevObject o = ow.nextObject();
				if (o == null)
					break;

				final byte[] bin = db.open(o, o.getType()).getCachedBytes();
				oc.check(o, o.getType(), bin);
				assertHash(o, bin);
			}
		}
	}

	private static void assertHash(RevObject id, byte[] bin) {
		MessageDigest md = Constants.newMessageDigest();
		md.update(Constants.encodedTypeString(id.getType()));
		md.update((byte) ' ');
		md.update(Constants.encodeASCII(bin.length));
		md.update((byte) 0);
		md.update(bin);
		assertEquals(id, ObjectId.fromRaw(md.digest()));
	}

	/**
	 * Pack all reachable objects in the repository into a single pack file.
	 * <p>
	 * All loose objects are automatically pruned. Existing packs however are
	 * not removed.
	 *
	 * @throws Exception
	 */
	public void packAndPrune() throws Exception {
		if (db.getObjectDatabase() instanceof ObjectDirectory) {
			ObjectDirectory odb = (ObjectDirectory) db.getObjectDatabase();
			NullProgressMonitor m = NullProgressMonitor.INSTANCE;

			final File pack, idx;
			try (PackWriter pw = new PackWriter(db)) {
				Set<ObjectId> all = new HashSet<>();
				for (Ref r : db.getRefDatabase().getRefs())
					all.add(r.getObjectId());
				pw.preparePack(m, all, PackWriter.NONE);

				final ObjectId name = pw.computeName();

				pack = nameFor(odb, name, ".pack");
				try (OutputStream out =
						new BufferedOutputStream(new FileOutputStream(pack))) {
					pw.writePack(m, m, out);
				}
				pack.setReadOnly();

				idx = nameFor(odb, name, ".idx");
				try (OutputStream out =
						new BufferedOutputStream(new FileOutputStream(idx))) {
					pw.writeIndex(out);
				}
				idx.setReadOnly();
			}

			odb.openPack(pack);
			updateServerInfo();
			prunePacked(odb);
		}
	}

	/**
	 * Closes the underlying {@link Repository} object and any other internal
	 * resources.
	 * <p>
	 * {@link AutoCloseable} resources that may escape this object, such as
	 * those returned by the {@link #git} and {@link #getRevWalk()} methods are
	 * not closed.
	 */
	@Override
	public void close() {
		try {
			inserter.close();
		} finally {
			db.close();
		}
	}

	private static void prunePacked(ObjectDirectory odb) throws IOException {
		for (PackFile p : odb.getPacks()) {
			for (MutableEntry e : p)
				FileUtils.delete(odb.fileFor(e.toObjectId()));
		}
	}

	private static File nameFor(ObjectDirectory odb, ObjectId name, String t) {
		File packdir = odb.getPackDirectory();
		return new File(packdir, "pack-" + name.name() + t);
	}

	private void writeFile(File p, byte[] bin) throws IOException,
			ObjectWritingException {
		final LockFile lck = new LockFile(p);
		if (!lck.lock())
			throw new ObjectWritingException("Can't write " + p);
		try {
			lck.write(bin);
		} catch (IOException ioe) {
			throw new ObjectWritingException("Can't write " + p);
		}
		if (!lck.commit())
			throw new ObjectWritingException("Can't write " + p);
	}

	/** Helper to build a branch with one or more commits */
	public class BranchBuilder {
		private final String ref;

		BranchBuilder(String ref) {
			this.ref = ref;
		}

		/**
		 * @return construct a new commit builder that updates this branch. If
		 *         the branch already exists, the commit builder will have its
		 *         first parent as the current commit and its tree will be
		 *         initialized to the current files.
		 * @throws Exception
		 *             the commit builder can't read the current branch state
		 */
		public CommitBuilder commit() throws Exception {
			return new CommitBuilder(this);
		}

		/**
		 * Forcefully update this branch to a particular commit.
		 *
		 * @param to
		 *            the commit to update to.
		 * @return {@code to}.
		 * @throws Exception
		 */
		public RevCommit update(CommitBuilder to) throws Exception {
			return update(to.create());
		}

		/**
		 * Forcefully update this branch to a particular commit.
		 *
		 * @param to
		 *            the commit to update to.
		 * @return {@code to}.
		 * @throws Exception
		 */
		public RevCommit update(RevCommit to) throws Exception {
			return TestRepository.this.update(ref, to);
		}

		/**
		 * Delete this branch.
		 * @throws Exception
		 * @since 4.4
		 */
		public void delete() throws Exception {
			TestRepository.this.delete(ref);
		}
	}

	/** Helper to generate a commit. */
	public class CommitBuilder {
		private final BranchBuilder branch;

		private final DirCache tree = DirCache.newInCore();

		private ObjectId topLevelTree;

		private final List<RevCommit> parents = new ArrayList<>(2);

		private int tick = 1;

		private String message = "";

		private RevCommit self;

		private PersonIdent author;
		private PersonIdent committer;

		private String changeId;

		private boolean updateCommitterTime;

		CommitBuilder() {
			branch = null;
		}

		CommitBuilder(BranchBuilder b) throws Exception {
			branch = b;

			Ref ref = db.exactRef(branch.ref);
			if (ref != null && ref.getObjectId() != null)
				parent(pool.parseCommit(ref.getObjectId()));
		}

		CommitBuilder(CommitBuilder prior) throws Exception {
			branch = prior.branch;

			DirCacheBuilder b = tree.builder();
			for (int i = 0; i < prior.tree.getEntryCount(); i++)
				b.add(prior.tree.getEntry(i));
			b.finish();

			parents.add(prior.create());
		}

		/**
		 * set parent commit
		 *
		 * @param p
		 *            parent commit
		 * @return this commit builder
		 * @throws Exception
		 */
		public CommitBuilder parent(RevCommit p) throws Exception {
			if (parents.isEmpty()) {
				DirCacheBuilder b = tree.builder();
				parseBody(p);
				b.addTree(new byte[0], DirCacheEntry.STAGE_0, pool
						.getObjectReader(), p.getTree());
				b.finish();
			}
			parents.add(p);
			return this;
		}

		/**
		 * Get parent commits
		 *
		 * @return parent commits
		 */
		public List<RevCommit> parents() {
			return Collections.unmodifiableList(parents);
		}

		/**
		 * Remove parent commits
		 *
		 * @return this commit builder
		 */
		public CommitBuilder noParents() {
			parents.clear();
			return this;
		}

		/**
		 * Remove files
		 *
		 * @return this commit builder
		 */
		public CommitBuilder noFiles() {
			tree.clear();
			return this;
		}

		/**
		 * Set top level tree
		 *
		 * @param treeId
		 *            the top level tree
		 * @return this commit builder
		 */
		public CommitBuilder setTopLevelTree(ObjectId treeId) {
			topLevelTree = treeId;
			return this;
		}

		/**
		 * Add file with given content
		 *
		 * @param path
		 *            path of the file
		 * @param content
		 *            the file content
		 * @return this commit builder
		 * @throws Exception
		 */
		public CommitBuilder add(String path, String content) throws Exception {
			return add(path, blob(content));
		}

		/**
		 * Add file with given path and blob
		 *
		 * @param path
		 *            path of the file
		 * @param id
		 *            blob for this file
		 * @return this commit builder
		 * @throws Exception
		 */
		public CommitBuilder add(String path, RevBlob id)
				throws Exception {
			return edit(new PathEdit(path) {
				@Override
				public void apply(DirCacheEntry ent) {
					ent.setFileMode(FileMode.REGULAR_FILE);
					ent.setObjectId(id);
				}
			});
		}

		/**
		 * Edit the index
		 *
		 * @param edit
		 *            the index record update
		 * @return this commit builder
		 */
		public CommitBuilder edit(PathEdit edit) {
			DirCacheEditor e = tree.editor();
			e.add(edit);
			e.finish();
			return this;
		}

		/**
		 * Remove a file
		 *
		 * @param path
		 *            path of the file
		 * @return this commit builder
		 */
		public CommitBuilder rm(String path) {
			DirCacheEditor e = tree.editor();
			e.add(new DeletePath(path));
			e.add(new DeleteTree(path));
			e.finish();
			return this;
		}

		/**
		 * Set commit message
		 *
		 * @param m
		 *            the message
		 * @return this commit builder
		 */
		public CommitBuilder message(String m) {
			message = m;
			return this;
		}

		/**
		 * Get the commit message
		 *
		 * @return the commit message
		 */
		public String message() {
			return message;
		}

		/**
		 * Tick the clock
		 *
		 * @param secs
		 *            number of seconds
		 * @return this commit builder
		 */
		public CommitBuilder tick(int secs) {
			tick = secs;
			return this;
		}

		/**
		 * Set author and committer identity
		 *
		 * @param ident
		 *            identity to set
		 * @return this commit builder
		 */
		public CommitBuilder ident(PersonIdent ident) {
			author = ident;
			committer = ident;
			return this;
		}

		/**
		 * Set the author identity
		 *
		 * @param a
		 *            the author's identity
		 * @return this commit builder
		 */
		public CommitBuilder author(PersonIdent a) {
			author = a;
			return this;
		}

		/**
		 * Get the author identity
		 *
		 * @return the author identity
		 */
		public PersonIdent author() {
			return author;
		}

		/**
		 * Set the committer identity
		 *
		 * @param c
		 *            the committer identity
		 * @return this commit builder
		 */
		public CommitBuilder committer(PersonIdent c) {
			committer = c;
			return this;
		}

		/**
		 * Get the committer identity
		 *
		 * @return the committer identity
		 */
		public PersonIdent committer() {
			return committer;
		}

		/**
		 * Insert changeId
		 *
		 * @return this commit builder
		 */
		public CommitBuilder insertChangeId() {
			changeId = "";
			return this;
		}

		/**
		 * Insert given changeId
		 *
		 * @param c
		 *            changeId
		 * @return this commit builder
		 */
		public CommitBuilder insertChangeId(String c) {
			// Validate, but store as a string so we can use "" as a sentinel.
			ObjectId.fromString(c);
			changeId = c;
			return this;
		}

		/**
		 * Create the commit
		 *
		 * @return the new commit
		 * @throws Exception
		 *             if creation failed
		 */
		public RevCommit create() throws Exception {
			if (self == null) {
				TestRepository.this.tick(tick);

				final org.eclipse.jgit.lib.CommitBuilder c;

				c = new org.eclipse.jgit.lib.CommitBuilder();
				c.setParentIds(parents);
				setAuthorAndCommitter(c);
				if (author != null)
					c.setAuthor(author);
				if (committer != null) {
					if (updateCommitterTime)
						committer = new PersonIdent(committer, getDate());
					c.setCommitter(committer);
				}

				ObjectId commitId;
				try (ObjectInserter ins = inserter) {
					if (topLevelTree != null)
						c.setTreeId(topLevelTree);
					else
						c.setTreeId(tree.writeTree(ins));
					insertChangeId(c);
					c.setMessage(message);
					commitId = ins.insert(c);
					ins.flush();
				}
				self = pool.parseCommit(commitId);

				if (branch != null)
					branch.update(self);
			}
			return self;
		}

		private void insertChangeId(org.eclipse.jgit.lib.CommitBuilder c) {
			if (changeId == null)
				return;
			int idx = ChangeIdUtil.indexOfChangeId(message, "\n");
			if (idx >= 0)
				return;

			ObjectId firstParentId = null;
			if (!parents.isEmpty())
				firstParentId = parents.get(0);

			ObjectId cid;
			if (changeId.isEmpty())
				cid = ChangeIdUtil.computeChangeId(c.getTreeId(), firstParentId,
						c.getAuthor(), c.getCommitter(), message);
			else
				cid = ObjectId.fromString(changeId);
			message = ChangeIdUtil.insertId(message, cid);
			if (cid != null)
				message = message.replaceAll("\nChange-Id: I" //$NON-NLS-1$
						+ ObjectId.zeroId().getName() + "\n", "\nChange-Id: I" //$NON-NLS-1$ //$NON-NLS-2$
						+ cid.getName() + "\n"); //$NON-NLS-1$
		}

		/**
		 * Create child commit builder
		 *
		 * @return child commit builder
		 * @throws Exception
		 */
		public CommitBuilder child() throws Exception {
			return new CommitBuilder(this);
		}
	}
}

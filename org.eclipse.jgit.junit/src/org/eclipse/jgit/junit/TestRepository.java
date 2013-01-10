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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.storage.file.LockFile;
import org.eclipse.jgit.storage.file.ObjectDirectory;
import org.eclipse.jgit.storage.file.PackFile;
import org.eclipse.jgit.storage.file.PackIndex.MutableEntry;
import org.eclipse.jgit.storage.pack.PackWriter;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.io.SafeBufferedOutputStream;

/**
 * Wrapper to make creating test data easier.
 *
 * @param <R>
 *            type of Repository the test data is stored on.
 */
public class TestRepository<R extends Repository> {
	private static final PersonIdent author;

	private static final PersonIdent committer;

	static {
		final MockSystemReader m = new MockSystemReader();
		final long now = m.getCurrentTime();
		final int tz = m.getTimezone(now);

		final String an = "J. Author";
		final String ae = "jauthor@example.com";
		author = new PersonIdent(an, ae, now, tz);

		final String cn = "J. Committer";
		final String ce = "jcommitter@example.com";
		committer = new PersonIdent(cn, ce, now, tz);
	}

	private final R db;

	private final RevWalk pool;

	private final ObjectInserter inserter;

	private long now;

	/**
	 * Wrap a repository with test building tools.
	 *
	 * @param db
	 *            the test repository to write into.
	 * @throws IOException
	 */
	public TestRepository(R db) throws IOException {
		this(db, new RevWalk(db));
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
		this.db = db;
		this.pool = rw;
		this.inserter = db.newObjectInserter();
		this.now = 1236977987000L;
	}

	/** @return the repository this helper class operates against. */
	public R getRepository() {
		return db;
	}

	/** @return get the RevWalk pool all objects are allocated through. */
	public RevWalk getRevWalk() {
		return pool;
	}

	/** @return current time adjusted by {@link #tick(int)}. */
	public Date getClock() {
		return new Date(now);
	}

	/**
	 * Adjust the current time that will used by the next commit.
	 *
	 * @param secDelta
	 *            number of seconds to add to the current time.
	 */
	public void tick(final int secDelta) {
		now += secDelta * 1000L;
	}

	/**
	 * Set the author and committer using {@link #getClock()}.
	 *
	 * @param c
	 *            the commit builder to store.
	 */
	public void setAuthorAndCommitter(org.eclipse.jgit.lib.CommitBuilder c) {
		c.setAuthor(new PersonIdent(author, new Date(now)));
		c.setCommitter(new PersonIdent(committer, new Date(now)));
	}

	/**
	 * Create a new blob object in the repository.
	 *
	 * @param content
	 *            file content, will be UTF-8 encoded.
	 * @return reference to the blob.
	 * @throws Exception
	 */
	public RevBlob blob(final String content) throws Exception {
		return blob(content.getBytes("UTF-8"));
	}

	/**
	 * Create a new blob object in the repository.
	 *
	 * @param content
	 *            binary file content.
	 * @return reference to the blob.
	 * @throws Exception
	 */
	public RevBlob blob(final byte[] content) throws Exception {
		ObjectId id;
		try {
			id = inserter.insert(Constants.OBJ_BLOB, content);
			inserter.flush();
		} finally {
			inserter.release();
		}
		return pool.lookupBlob(id);
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
	public DirCacheEntry file(final String path, final RevBlob blob)
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
	 * @return reference to the tree specified by the entry list.
	 * @throws Exception
	 */
	public RevTree tree(final DirCacheEntry... entries) throws Exception {
		final DirCache dc = DirCache.newInCore();
		final DirCacheBuilder b = dc.builder();
		for (final DirCacheEntry e : entries)
			b.add(e);
		b.finish();
		ObjectId root;
		try {
			root = dc.writeTree(inserter);
			inserter.flush();
		} finally {
			inserter.release();
		}
		return pool.lookupTree(root);
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
	public RevObject get(final RevTree tree, final String path)
			throws Exception {
		final TreeWalk tw = new TreeWalk(pool.getObjectReader());
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
	public RevCommit commit(final RevCommit... parents) throws Exception {
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
	public RevCommit commit(final RevTree tree, final RevCommit... parents)
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
	public RevCommit commit(final int secDelta, final RevCommit... parents)
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
	 * @return the new commit.
	 * @throws Exception
	 */
	public RevCommit commit(final int secDelta, final RevTree tree,
			final RevCommit... parents) throws Exception {
		tick(secDelta);

		final org.eclipse.jgit.lib.CommitBuilder c;

		c = new org.eclipse.jgit.lib.CommitBuilder();
		c.setTreeId(tree);
		c.setParentIds(parents);
		c.setAuthor(new PersonIdent(author, new Date(now)));
		c.setCommitter(new PersonIdent(committer, new Date(now)));
		c.setMessage("");
		ObjectId id;
		try {
			id = inserter.insert(c);
			inserter.flush();
		} finally {
			inserter.release();
		}
		return pool.lookupCommit(id);
	}

	/** @return a new commit builder. */
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
	 * @return the annotated tag object.
	 * @throws Exception
	 */
	public RevTag tag(final String name, final RevObject dst) throws Exception {
		final TagBuilder t = new TagBuilder();
		t.setObjectId(dst);
		t.setTag(name);
		t.setTagger(new PersonIdent(committer, new Date(now)));
		t.setMessage("");
		ObjectId id;
		try {
			id = inserter.insert(t);
			inserter.flush();
		} finally {
			inserter.release();
		}
		return (RevTag) pool.lookupAny(id, Constants.OBJ_TAG);
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
	 * Update the dumb client server info files.
	 *
	 * @throws Exception
	 */
	public void updateServerInfo() throws Exception {
		if (db instanceof FileRepository) {
			final FileRepository fr = (FileRepository) db;
			RefWriter rw = new RefWriter(fr.getAllRefs().values()) {
				@Override
				protected void writeFile(final String name, final byte[] bin)
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
	 *            type of object, e.g. {@link RevTag} or {@link RevCommit}.
	 * @param object
	 *            reference to the (possibly unparsed) object to force body
	 *            parsing of.
	 * @return {@code object}
	 * @throws Exception
	 */
	public <T extends RevObject> T parseBody(final T object) throws Exception {
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
		ObjectWalk ow = new ObjectWalk(db);
		if (tips.length != 0) {
			for (RevObject o : tips)
				ow.markStart(ow.parseAny(o));
		} else {
			for (Ref r : db.getAllRefs().values())
				ow.markStart(ow.parseAny(r.getObjectId()));
		}

		ObjectChecker oc = new ObjectChecker();
		for (;;) {
			final RevCommit o = ow.next();
			if (o == null)
				break;

			final byte[] bin = db.open(o, o.getType()).getCachedBytes();
			oc.checkCommit(bin);
			assertHash(o, bin);
		}

		for (;;) {
			final RevObject o = ow.nextObject();
			if (o == null)
				break;

			final byte[] bin = db.open(o, o.getType()).getCachedBytes();
			oc.check(o.getType(), bin);
			assertHash(o, bin);
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
			PackWriter pw = new PackWriter(db);
			try {
				Set<ObjectId> all = new HashSet<ObjectId>();
				for (Ref r : db.getAllRefs().values())
					all.add(r.getObjectId());
				pw.preparePack(m, all, Collections.<ObjectId> emptySet());

				final ObjectId name = pw.computeName();
				OutputStream out;

				pack = nameFor(odb, name, ".pack");
				out = new SafeBufferedOutputStream(new FileOutputStream(pack));
				try {
					pw.writePack(m, m, out);
				} finally {
					out.close();
				}
				pack.setReadOnly();

				idx = nameFor(odb, name, ".idx");
				out = new SafeBufferedOutputStream(new FileOutputStream(idx));
				try {
					pw.writeIndex(out);
				} finally {
					out.close();
				}
				idx.setReadOnly();
			} finally {
				pw.release();
			}

			odb.openPack(pack);
			updateServerInfo();
			prunePacked(odb);
		}
	}

	private static void prunePacked(ObjectDirectory odb) throws IOException {
		for (PackFile p : odb.getPacks()) {
			for (MutableEntry e : p)
				FileUtils.delete(odb.fileFor(e.toObjectId()));
		}
	}

	private static File nameFor(ObjectDirectory odb, ObjectId name, String t) {
		File packdir = new File(odb.getDirectory(), "pack");
		return new File(packdir, "pack-" + name.name() + t);
	}

	private void writeFile(final File p, final byte[] bin) throws IOException,
			ObjectWritingException {
		final LockFile lck = new LockFile(p, db.getFS());
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

		BranchBuilder(final String ref) {
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
	}

	/** Helper to generate a commit. */
	public class CommitBuilder {
		private final BranchBuilder branch;

		private final DirCache tree = DirCache.newInCore();

		private ObjectId topLevelTree;

		private final List<RevCommit> parents = new ArrayList<RevCommit>(2);

		private int tick = 1;

		private String message = "";

		private RevCommit self;

		CommitBuilder() {
			branch = null;
		}

		CommitBuilder(BranchBuilder b) throws Exception {
			branch = b;

			Ref ref = db.getRef(branch.ref);
			if (ref != null) {
				parent(pool.parseCommit(ref.getObjectId()));
			}
		}

		CommitBuilder(CommitBuilder prior) throws Exception {
			branch = prior.branch;

			DirCacheBuilder b = tree.builder();
			for (int i = 0; i < prior.tree.getEntryCount(); i++)
				b.add(prior.tree.getEntry(i));
			b.finish();

			parents.add(prior.create());
		}

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

		public CommitBuilder noParents() {
			parents.clear();
			return this;
		}

		public CommitBuilder noFiles() {
			tree.clear();
			return this;
		}

		public CommitBuilder setTopLevelTree(ObjectId treeId) {
			topLevelTree = treeId;
			return this;
		}

		public CommitBuilder add(String path, String content) throws Exception {
			return add(path, blob(content));
		}

		public CommitBuilder add(String path, final RevBlob id)
				throws Exception {
			return edit(new PathEdit(path) {
				@Override
				public void apply(DirCacheEntry ent) {
					ent.setFileMode(FileMode.REGULAR_FILE);
					ent.setObjectId(id);
				}
			});
		}

		public CommitBuilder edit(PathEdit edit) {
			DirCacheEditor e = tree.editor();
			e.add(edit);
			e.finish();
			return this;
		}

		public CommitBuilder rm(String path) {
			DirCacheEditor e = tree.editor();
			e.add(new DeletePath(path));
			e.add(new DeleteTree(path));
			e.finish();
			return this;
		}

		public CommitBuilder message(String m) {
			message = m;
			return this;
		}

		public CommitBuilder tick(int secs) {
			tick = secs;
			return this;
		}

		public RevCommit create() throws Exception {
			if (self == null) {
				TestRepository.this.tick(tick);

				final org.eclipse.jgit.lib.CommitBuilder c;

				c = new org.eclipse.jgit.lib.CommitBuilder();
				c.setParentIds(parents);
				setAuthorAndCommitter(c);
				c.setMessage(message);

				ObjectId commitId;
				try {
					if (topLevelTree != null)
						c.setTreeId(topLevelTree);
					else
						c.setTreeId(tree.writeTree(inserter));
					commitId = inserter.insert(c);
					inserter.flush();
				} finally {
					inserter.release();
				}
				self = pool.lookupCommit(commitId);

				if (branch != null)
					branch.update(self);
			}
			return self;
		}

		public CommitBuilder child() throws Exception {
			return new CommitBuilder(this);
		}
	}
}

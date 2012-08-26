/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2006-2012, Robin Rosenberg <robin.rosenberg@dewire.com>
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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
import org.eclipse.jgit.storage.file.ReflogEntry;
import org.eclipse.jgit.storage.file.ReflogReader;
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
public abstract class AbstractRepository extends Repository {
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
	protected AbstractRepository(final BaseRepositoryBuilder options) {
		gitDir = options.getGitDir();
		fs = options.getFS();
		workTree = options.getWorkTree();
		indexFile = options.getIndexFile();
	}

	@Override
	public ListenerList getListenerList() {
		return myListeners;
	}

	@Override
	public void fireEvent(RepositoryEvent<?> event) {
		event.setRepository(this);
		myListeners.dispatch(event);
		Repository.globalListeners.dispatch(event);
	}

	@Override
	public void create() throws IOException {
		create(false);
	}

	@Override
	public abstract void create(boolean bare) throws IOException;

	@Override
	public File getDirectory() {
		return gitDir;
	}

	@Override
	public abstract ObjectDatabase getObjectDatabase();

	@Override
	public ObjectInserter newObjectInserter() {
		return getObjectDatabase().newInserter();
	}

	@Override
	public ObjectReader newObjectReader() {
		return getObjectDatabase().newReader();
	}

	@Override
	public abstract RefDatabase getRefDatabase();

	@Override
	public abstract StoredConfig getConfig();

	@Override
	public FS getFS() {
		return fs;
	}

	@Override
	public boolean hasObject(AnyObjectId objectId) {
		try {
			return getObjectDatabase().has(objectId);
		} catch (IOException e) {
			// Legacy API, assume error means "no"
			return false;
		}
	}

	@Override
	public ObjectLoader open(final AnyObjectId objectId)
			throws MissingObjectException, IOException {
		return getObjectDatabase().open(objectId);
	}

	@Override
	public ObjectLoader open(AnyObjectId objectId, int typeHint)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		return getObjectDatabase().open(objectId, typeHint);
	}

	@Override
	public RefUpdate updateRef(final String ref) throws IOException {
		return updateRef(ref, false);
	}

	@Override
	public RefUpdate updateRef(final String ref, final boolean detach) throws IOException {
		return getRefDatabase().newUpdate(ref, detach);
	}

	@Override
	public RefRename renameRef(final String fromRef, final String toRef) throws IOException {
		return getRefDatabase().newRename(fromRef, toRef);
	}

	@Override
	public ObjectId resolve(final String revstr)
			throws AmbiguousObjectException, IOException {
		RevWalk rw = new RevWalk(this);
		try {
			return resolve(rw, revstr);
		} finally {
			rw.release();
		}
	}

	private ObjectId resolve(final RevWalk rw, final String revstr) throws IOException {
		char[] revChars = revstr.toCharArray();
		RevObject rev = null;
		for (int i = 0; i < revChars.length; ++i) {
			switch (revChars[i]) {
			case '^':
				if (rev == null) {
					rev = parseSimple(rw, new String(revChars, 0, i));
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
							if (item.equals("tree")) {
								rev = rw.parseTree(rev);
							} else if (item.equals("commit")) {
								rev = rw.parseCommit(rev);
							} else if (item.equals("blob")) {
								rev = rw.peel(rev);
								if (!(rev instanceof RevBlob))
									throw new IncorrectObjectTypeException(rev,
											Constants.TYPE_BLOB);
							} else if (item.equals("")) {
								rev = rw.peel(rev);
							} else
								throw new RevisionSyntaxException(revstr);
						else
							throw new RevisionSyntaxException(revstr);
						break;
					default:
						rev = rw.parseAny(rev);
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
				break;
			case '~':
				if (rev == null) {
					rev = parseSimple(rw, new String(revChars, 0, i));
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
				break;
			case '@':
				int m;
				String time = null;
				for (m = i + 2; m < revChars.length; ++m) {
					if (revChars[m] == '}') {
						time = new String(revChars, i + 2, m - i - 2);
						break;
					}
				}
				if (time != null) {
					String refName = new String(revChars, 0, i);
					Ref resolved = getRefDatabase().getRef(refName);
					if (resolved == null)
						return null;
					rev = resolveReflog(rw, resolved, time);
					i = m;
				} else
					i = m - 1;
				break;
			case ':': {
				RevTree tree;
				if (rev == null) {
					// We might not yet have parsed the left hand side.
					ObjectId id;
					try {
						if (i == 0)
							id = resolve(rw, Constants.HEAD);
						else
							id = resolve(rw, new String(revChars, 0, i));
					} catch (RevisionSyntaxException badSyntax) {
						throw new RevisionSyntaxException(revstr);
					}
					if (id == null)
						return null;
					tree = rw.parseTree(id);
				} else {
					tree = rw.parseTree(rev);
				}

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
		return rev != null ? rev.copy() : resolveSimple(revstr);
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

		Ref r = getRefDatabase().getRef(revstr);
		if (r != null)
			return r.getObjectId();

		if (AbbreviatedObjectId.isId(revstr))
			return resolveAbbreviation(revstr);

		int dashg = revstr.indexOf("-g");
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

	private RevCommit resolveReflog(RevWalk rw, Ref ref, String time)
			throws IOException {
		int number;
		try {
			number = Integer.parseInt(time);
		} catch (NumberFormatException nfe) {
			throw new RevisionSyntaxException(MessageFormat.format(
					JGitText.get().invalidReflogRevision, time));
		}
		if (number < 0)
			throw new RevisionSyntaxException(MessageFormat.format(
					JGitText.get().invalidReflogRevision, time));

		ReflogReader reader = new ReflogReader(this, ref.getName());
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

	@Override
	public void incrementOpen() {
		useCnt.incrementAndGet();
	}

	@Override
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

	@Override
	public String toString() {
		String desc;
		if (getDirectory() != null)
			desc = getDirectory().getPath();
		else
			desc = getClass().getSimpleName() + "-"
					+ System.identityHashCode(this);
		return "Repository[" + desc + "]";
	}

	@Override
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

	@Override
	public String getBranch() throws IOException {
		String name = getFullBranch();
		if (name != null)
			return Repository.shortenRefName(name);
		return name;
	}

	@Override
	public Set<ObjectId> getAdditionalHaves() {
		return Collections.emptySet();
	}

	@Override
	public Ref getRef(final String name) throws IOException {
		return getRefDatabase().getRef(name);
	}

	@Override
	public Map<String, Ref> getAllRefs() {
		try {
			return getRefDatabase().getRefs(RefDatabase.ALL);
		} catch (IOException e) {
			return new HashMap<String, Ref>();
		}
	}

	@Override
	public Map<String, Ref> getTags() {
		try {
			return getRefDatabase().getRefs(Constants.R_TAGS);
		} catch (IOException e) {
			return new HashMap<String, Ref>();
		}
	}

	@Override
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

	@Override
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

	@Override
	public File getIndexFile() throws NoWorkTreeException {
		if (isBare())
			throw new NoWorkTreeException();
		return indexFile;
	}

	@Override
	public DirCache readDirCache() throws NoWorkTreeException,
			CorruptObjectException, IOException {
		return DirCache.read(this);
	}

	@Override
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

	@Override
	public RepositoryState getRepositoryState() {
		if (isBare() || getDirectory() == null)
			return RepositoryState.BARE;

		// Pre Git-1.6 logic
		if (new File(getWorkTree(), ".dotest").exists())
			return RepositoryState.REBASING;
		if (new File(getDirectory(), ".dotest-merge").exists())
			return RepositoryState.REBASING_INTERACTIVE;

		// From 1.6 onwards
		if (new File(getDirectory(),"rebase-apply/rebasing").exists())
			return RepositoryState.REBASING_REBASING;
		if (new File(getDirectory(),"rebase-apply/applying").exists())
			return RepositoryState.APPLY;
		if (new File(getDirectory(),"rebase-apply").exists())
			return RepositoryState.REBASING;

		if (new File(getDirectory(),"rebase-merge/interactive").exists())
			return RepositoryState.REBASING_INTERACTIVE;
		if (new File(getDirectory(),"rebase-merge").exists())
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

		if (new File(getDirectory(), "BISECT_LOG").exists())
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

		return RepositoryState.SAFE;
	}

	@Override
	public boolean isBare() {
		return workTree == null;
	}

	@Override
	public File getWorkTree() throws NoWorkTreeException {
		if (isBare())
			throw new NoWorkTreeException();
		return workTree;
	}

	@Override
	public abstract void scanForRepoChanges() throws IOException;

	@Override
	public abstract void notifyIndexChanged();

	@Override
	public abstract ReflogReader getReflogReader(String refName)
			throws IOException;

	@Override
	public String readMergeCommitMsg() throws IOException, NoWorkTreeException {
		return readCommitMsgFile(Constants.MERGE_MSG);
	}

	@Override
	public void writeMergeCommitMsg(String msg) throws IOException {
		File mergeMsgFile = new File(gitDir, Constants.MERGE_MSG);
		writeCommitMsg(mergeMsgFile, msg);
	}

	@Override
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

	@Override
	public void writeMergeHeads(List<ObjectId> heads) throws IOException {
		writeHeadsFile(heads, Constants.MERGE_HEAD);
	}

	@Override
	public ObjectId readCherryPickHead() throws IOException,
			NoWorkTreeException {
		if (isBare() || getDirectory() == null)
			throw new NoWorkTreeException();

		byte[] raw = readGitDirectoryFile(Constants.CHERRY_PICK_HEAD);
		if (raw == null)
			return null;

		return ObjectId.fromString(raw, 0);
	}

	@Override
	public void writeCherryPickHead(ObjectId head) throws IOException {
		List<ObjectId> heads = (head != null) ? Collections.singletonList(head)
				: null;
		writeHeadsFile(heads, Constants.CHERRY_PICK_HEAD);
	}

	@Override
	public void writeOrigHead(ObjectId head) throws IOException {
		List<ObjectId> heads = head != null ? Collections.singletonList(head)
				: null;
		writeHeadsFile(heads, Constants.ORIG_HEAD);
	}

	@Override
	public ObjectId readOrigHead() throws IOException, NoWorkTreeException {
		if (isBare() || getDirectory() == null)
			throw new NoWorkTreeException();

		byte[] raw = readGitDirectoryFile(Constants.ORIG_HEAD);
		return raw != null ? ObjectId.fromString(raw, 0) : null;
	}

	@Override
	public String readSquashCommitMsg() throws IOException {
		return readCommitMsgFile(Constants.SQUASH_MSG);
	}

	@Override
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
}

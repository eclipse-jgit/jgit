/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2010, Matthias Sohn <matthias.sohn@sap.com>
 * Copyright (C) 2012-2013, Robin Rosenberg
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

package org.eclipse.jgit.treewalk;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.ignore.IgnoreNode;
import org.eclipse.jgit.ignore.IgnoreRule;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig;
import org.eclipse.jgit.lib.CoreConfig.CheckStat;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.io.EolCanonicalizingInputStream;

/**
 * Walks a working directory tree as part of a {@link TreeWalk}.
 * <p>
 * Most applications will want to use the standard implementation of this
 * iterator, {@link FileTreeIterator}, as that does all IO through the standard
 * <code>java.io</code> package. Plugins for a Java based IDE may however wish
 * to create their own implementations of this class to allow traversal of the
 * IDE's project space, as well as benefit from any caching the IDE may have.
 *
 * @see FileTreeIterator
 */
public abstract class WorkingTreeIterator extends AbstractTreeIterator {
	/** An empty entry array, suitable for {@link #init(Entry[])}. */
	protected static final Entry[] EOF = {};

	/** Size we perform file IO in if we have to read and hash a file. */
	static final int BUFFER_SIZE = 2048;

	/**
	 * Maximum size of files which may be read fully into memory for performance
	 * reasons.
	 */
	private static final long MAXIMUM_FILE_SIZE_TO_READ_FULLY = 65536;

	/** Inherited state of this iterator, describing working tree, etc. */
	private final IteratorState state;

	/** The {@link #idBuffer()} for the current entry. */
	private byte[] contentId;

	/** Index within {@link #entries} that {@link #contentId} came from. */
	private int contentIdFromPtr;

	/** List of entries obtained from the subclass. */
	private Entry[] entries;

	/** Total number of entries in {@link #entries} that are valid. */
	private int entryCnt;

	/** Current position within {@link #entries}. */
	private int ptr;

	/** If there is a .gitignore file present, the parsed rules from it. */
	private IgnoreNode ignoreNode;

	/** Repository that is the root level being iterated over */
	protected Repository repository;

	/** Cached canonical length, initialized from {@link #idBuffer()} */
	private long canonLen = -1;

	/** The offset of the content id in {@link #idBuffer()} */
	private int contentIdOffset;

	/**
	 * Create a new iterator with no parent.
	 *
	 * @param options
	 *            working tree options to be used
	 */
	protected WorkingTreeIterator(WorkingTreeOptions options) {
		super();
		state = new IteratorState(options);
	}

	/**
	 * Create a new iterator with no parent and a prefix.
	 * <p>
	 * The prefix path supplied is inserted in front of all paths generated by
	 * this iterator. It is intended to be used when an iterator is being
	 * created for a subsection of an overall repository and needs to be
	 * combined with other iterators that are created to run over the entire
	 * repository namespace.
	 *
	 * @param prefix
	 *            position of this iterator in the repository tree. The value
	 *            may be null or the empty string to indicate the prefix is the
	 *            root of the repository. A trailing slash ('/') is
	 *            automatically appended if the prefix does not end in '/'.
	 * @param options
	 *            working tree options to be used
	 */
	protected WorkingTreeIterator(final String prefix,
			WorkingTreeOptions options) {
		super(prefix);
		state = new IteratorState(options);
	}

	/**
	 * Create an iterator for a subtree of an existing iterator.
	 *
	 * @param p
	 *            parent tree iterator.
	 */
	protected WorkingTreeIterator(final WorkingTreeIterator p) {
		super(p);
		state = p.state;
	}

	/**
	 * Initialize this iterator for the root level of a repository.
	 * <p>
	 * This method should only be invoked after calling {@link #init(Entry[])},
	 * and only for the root iterator.
	 *
	 * @param repo
	 *            the repository.
	 */
	protected void initRootIterator(Repository repo) {
		repository = repo;
		Entry entry;
		if (ignoreNode instanceof PerDirectoryIgnoreNode)
			entry = ((PerDirectoryIgnoreNode) ignoreNode).entry;
		else
			entry = null;
		ignoreNode = new RootIgnoreNode(entry, repo);
	}

	/**
	 * Define the matching {@link DirCacheIterator}, to optimize ObjectIds.
	 *
	 * Once the DirCacheIterator has been set this iterator must only be
	 * advanced by the TreeWalk that is supplied, as it assumes that itself and
	 * the corresponding DirCacheIterator are positioned on the same file path
	 * whenever {@link #idBuffer()} is invoked.
	 *
	 * @param walk
	 *            the walk that will be advancing this iterator.
	 * @param treeId
	 *            index of the matching {@link DirCacheIterator}.
	 */
	public void setDirCacheIterator(TreeWalk walk, int treeId) {
		state.walk = walk;
		state.dirCacheTree = treeId;
	}

	@Override
	public boolean hasId() {
		if (contentIdFromPtr == ptr)
			return true;
		return (mode & FileMode.TYPE_MASK) == FileMode.TYPE_FILE;
	}

	@Override
	public byte[] idBuffer() {
		if (contentIdFromPtr == ptr)
			return contentId;

		if (state.walk != null) {
			// If there is a matching DirCacheIterator, we can reuse
			// its idBuffer, but only if we appear to be clean against
			// the cached index information for the path.
			//
			DirCacheIterator i = state.walk.getTree(state.dirCacheTree,
					DirCacheIterator.class);
			if (i != null) {
				DirCacheEntry ent = i.getDirCacheEntry();
				if (ent != null && compareMetadata(ent) == MetadataDiff.EQUAL) {
					contentIdOffset = i.idOffset();
					contentIdFromPtr = ptr;
					return contentId = i.idBuffer();
				}
				contentIdOffset = 0;
			} else {
				contentIdOffset = 0;
			}
		}
		switch (mode & FileMode.TYPE_MASK) {
		case FileMode.TYPE_FILE:
			contentIdFromPtr = ptr;
			return contentId = idBufferBlob(entries[ptr]);
		case FileMode.TYPE_SYMLINK:
			// Java does not support symbolic links, so we should not
			// have reached this particular part of the walk code.
			//
			return zeroid;
		case FileMode.TYPE_GITLINK:
			contentIdFromPtr = ptr;
			return contentId = idSubmodule(entries[ptr]);
		}
		return zeroid;
	}

	/**
	 * Get submodule id for given entry.
	 *
	 * @param e
	 * @return non-null submodule id
	 */
	protected byte[] idSubmodule(Entry e) {
		if (repository == null)
			return zeroid;
		File directory;
		try {
			directory = repository.getWorkTree();
		} catch (NoWorkTreeException nwte) {
			return zeroid;
		}
		return idSubmodule(directory, e);
	}

	/**
	 * Get submodule id using the repository at the location of the entry
	 * relative to the directory.
	 *
	 * @param directory
	 * @param e
	 * @return non-null submodule id
	 */
	protected byte[] idSubmodule(File directory, Entry e) {
		final Repository submoduleRepo;
		try {
			submoduleRepo = SubmoduleWalk.getSubmoduleRepository(directory,
					e.getName());
		} catch (IOException exception) {
			return zeroid;
		}
		if (submoduleRepo == null)
			return zeroid;

		final ObjectId head;
		try {
			head = submoduleRepo.resolve(Constants.HEAD);
		} catch (IOException exception) {
			return zeroid;
		} finally {
			submoduleRepo.close();
		}
		if (head == null)
			return zeroid;
		final byte[] id = new byte[Constants.OBJECT_ID_LENGTH];
		head.copyRawTo(id, 0);
		return id;
	}

	private static final byte[] digits = { '0', '1', '2', '3', '4', '5', '6',
			'7', '8', '9' };

	private static final byte[] hblob = Constants
			.encodedTypeString(Constants.OBJ_BLOB);

	private byte[] idBufferBlob(final Entry e) {
		try {
			final InputStream is = e.openInputStream();
			if (is == null)
				return zeroid;
			try {
				state.initializeDigestAndReadBuffer();

				final long len = e.getLength();
				InputStream filteredIs = possiblyFilteredInputStream(e, is, len);
				return computeHash(filteredIs, canonLen);
			} finally {
				safeClose(is);
			}
		} catch (IOException err) {
			// Can't read the file? Don't report the failure either.
			return zeroid;
		}
	}

	private InputStream possiblyFilteredInputStream(final Entry e,
			final InputStream is, final long len) throws IOException {
		if (!mightNeedCleaning()) {
			canonLen = len;
			return is;
		}

		if (len <= MAXIMUM_FILE_SIZE_TO_READ_FULLY) {
			ByteBuffer rawbuf = IO.readWholeStream(is, (int) len);
			byte[] raw = rawbuf.array();
			int n = rawbuf.limit();
			if (!isBinary(raw, n)) {
				rawbuf = filterClean(raw, n);
				raw = rawbuf.array();
				n = rawbuf.limit();
			}
			canonLen = n;
			return new ByteArrayInputStream(raw, 0, n);
		}

		if (isBinary(e)) {
			canonLen = len;
			return is;
		}

		final InputStream lenIs = filterClean(e.openInputStream());
		try {
			canonLen = computeLength(lenIs);
		} finally {
			safeClose(lenIs);
		}
		return filterClean(is);
	}

	private static void safeClose(final InputStream in) {
		try {
			in.close();
		} catch (IOException err2) {
			// Suppress any error related to closing an input
			// stream. We don't care, we should not have any
			// outstanding data to flush or anything like that.
		}
	}

	private boolean mightNeedCleaning() {
		switch (getOptions().getAutoCRLF()) {
		case FALSE:
		default:
			return false;

		case TRUE:
		case INPUT:
			return true;
		}
	}

	private boolean isBinary(byte[] content, int sz) {
		return RawText.isBinary(content, sz);
	}

	private boolean isBinary(Entry entry) throws IOException {
		InputStream in = entry.openInputStream();
		try {
			return RawText.isBinary(in);
		} finally {
			safeClose(in);
		}
	}

	private ByteBuffer filterClean(byte[] src, int n)
			throws IOException {
		InputStream in = new ByteArrayInputStream(src);
		try {
			return IO.readWholeStream(filterClean(in), n);
		} finally {
			safeClose(in);
		}
	}

	private InputStream filterClean(InputStream in) {
		return new EolCanonicalizingInputStream(in, true);
	}

	/**
	 * Returns the working tree options used by this iterator.
	 *
	 * @return working tree options
	 */
	public WorkingTreeOptions getOptions() {
		return state.options;
	}

	@Override
	public int idOffset() {
		return contentIdOffset;
	}

	@Override
	public void reset() {
		if (!first()) {
			ptr = 0;
			if (!eof())
				parseEntry();
		}
	}

	@Override
	public boolean first() {
		return ptr == 0;
	}

	@Override
	public boolean eof() {
		return ptr == entryCnt;
	}

	@Override
	public void next(final int delta) throws CorruptObjectException {
		ptr += delta;
		if (!eof()) {
			parseEntry();
		}
	}

	@Override
	public void back(final int delta) throws CorruptObjectException {
		ptr -= delta;
		parseEntry();
	}

	private void parseEntry() {
		final Entry e = entries[ptr];
		mode = e.getMode().getBits();

		final int nameLen = e.encodedNameLen;
		ensurePathCapacity(pathOffset + nameLen, pathOffset);
		System.arraycopy(e.encodedName, 0, path, pathOffset, nameLen);
		pathLen = pathOffset + nameLen;
		canonLen = -1;
	}

	/**
	 * Get the raw byte length of this entry.
	 *
	 * @return size of this file, in bytes.
	 */
	public long getEntryLength() {
		return current().getLength();
	}

	/**
	 * Get the filtered input length of this entry
	 *
	 * @return size of the content, in bytes
	 * @throws IOException
	 */
	public long getEntryContentLength() throws IOException {
		if (canonLen == -1) {
			long rawLen = getEntryLength();
			if (rawLen == 0)
				canonLen = 0;
			InputStream is = current().openInputStream();
			try {
				// canonLen gets updated here
				possiblyFilteredInputStream(current(), is, current()
						.getLength());
			} finally {
				safeClose(is);
			}
		}
		return canonLen;
	}

	/**
	 * Get the last modified time of this entry.
	 *
	 * @return last modified time of this file, in milliseconds since the epoch
	 *         (Jan 1, 1970 UTC).
	 */
	public long getEntryLastModified() {
		return current().getLastModified();
	}

	/**
	 * Obtain an input stream to read the file content.
	 * <p>
	 * Efficient implementations are not required. The caller will usually
	 * obtain the stream only once per entry, if at all.
	 * <p>
	 * The input stream should not use buffering if the implementation can avoid
	 * it. The caller will buffer as necessary to perform efficient block IO
	 * operations.
	 * <p>
	 * The caller will close the stream once complete.
	 *
	 * @return a stream to read from the file.
	 * @throws IOException
	 *             the file could not be opened for reading.
	 */
	public InputStream openEntryStream() throws IOException {
		InputStream rawis = current().openInputStream();
		if (mightNeedCleaning())
			return filterClean(rawis);
		else
			return rawis;
	}

	/**
	 * Determine if the current entry path is ignored by an ignore rule.
	 *
	 * @return true if the entry was ignored by an ignore rule file.
	 * @throws IOException
	 *             a relevant ignore rule file exists but cannot be read.
	 */
	public boolean isEntryIgnored() throws IOException {
		return isEntryIgnored(pathLen);
	}

	/**
	 * Determine if the entry path is ignored by an ignore rule.
	 *
	 * @param pLen
	 *            the length of the path in the path buffer.
	 * @return true if the entry is ignored by an ignore rule.
	 * @throws IOException
	 *             a relevant ignore rule file exists but cannot be read.
	 */
	protected boolean isEntryIgnored(final int pLen) throws IOException {
		IgnoreNode rules = getIgnoreNode();
		if (rules != null) {
			// The ignore code wants path to start with a '/' if possible.
			// If we have the '/' in our path buffer because we are inside
			// a subdirectory include it in the range we convert to string.
			//
			int pOff = pathOffset;
			if (0 < pOff)
				pOff--;
			String p = TreeWalk.pathOf(path, pOff, pLen);
			switch (rules.isIgnored(p, FileMode.TREE.equals(mode))) {
			case IGNORED:
				return true;
			case NOT_IGNORED:
				return false;
			case CHECK_PARENT:
				break;
			}
		}
		if (parent instanceof WorkingTreeIterator)
			return ((WorkingTreeIterator) parent).isEntryIgnored(pLen);
		return false;
	}

	private IgnoreNode getIgnoreNode() throws IOException {
		if (ignoreNode instanceof PerDirectoryIgnoreNode)
			ignoreNode = ((PerDirectoryIgnoreNode) ignoreNode).load();
		return ignoreNode;
	}

	private static final Comparator<Entry> ENTRY_CMP = new Comparator<Entry>() {
		public int compare(final Entry o1, final Entry o2) {
			final byte[] a = o1.encodedName;
			final byte[] b = o2.encodedName;
			final int aLen = o1.encodedNameLen;
			final int bLen = o2.encodedNameLen;
			int cPos;

			for (cPos = 0; cPos < aLen && cPos < bLen; cPos++) {
				final int cmp = (a[cPos] & 0xff) - (b[cPos] & 0xff);
				if (cmp != 0)
					return cmp;
			}

			if (cPos < aLen)
				return (a[cPos] & 0xff) - lastPathChar(o2);
			if (cPos < bLen)
				return lastPathChar(o1) - (b[cPos] & 0xff);
			return lastPathChar(o1) - lastPathChar(o2);
		}
	};

	static int lastPathChar(final Entry e) {
		return e.getMode() == FileMode.TREE ? '/' : '\0';
	}

	/**
	 * Constructor helper.
	 *
	 * @param list
	 *            files in the subtree of the work tree this iterator operates
	 *            on
	 */
	protected void init(final Entry[] list) {
		// Filter out nulls, . and .. as these are not valid tree entries,
		// also cache the encoded forms of the path names for efficient use
		// later on during sorting and iteration.
		//
		entries = list;
		int i, o;

		final CharsetEncoder nameEncoder = state.nameEncoder;
		for (i = 0, o = 0; i < entries.length; i++) {
			final Entry e = entries[i];
			if (e == null)
				continue;
			final String name = e.getName();
			if (".".equals(name) || "..".equals(name)) //$NON-NLS-1$ //$NON-NLS-2$
				continue;
			if (Constants.DOT_GIT.equals(name))
				continue;
			if (Constants.DOT_GIT_IGNORE.equals(name))
				ignoreNode = new PerDirectoryIgnoreNode(e);
			if (i != o)
				entries[o] = e;
			e.encodeName(nameEncoder);
			o++;
		}
		entryCnt = o;
		Arrays.sort(entries, 0, entryCnt, ENTRY_CMP);

		contentIdFromPtr = -1;
		ptr = 0;
		if (!eof())
			parseEntry();
	}

	/**
	 * Obtain the current entry from this iterator.
	 *
	 * @return the currently selected entry.
	 */
	protected Entry current() {
		return entries[ptr];
	}

	/**
	 * The result of a metadata-comparison between the current entry and a
	 * {@link DirCacheEntry}
	 */
	public enum MetadataDiff {
		/**
		 * The entries are equal by metaData (mode, length,
		 * modification-timestamp) or the <code>assumeValid</code> attribute of
		 * the index entry is set
		 */
		EQUAL,

		/**
		 * The entries are not equal by metaData (mode, length) or the
		 * <code>isUpdateNeeded</code> attribute of the index entry is set
		 */
		DIFFER_BY_METADATA,

		/** index entry is smudged - can't use that entry for comparison */
		SMUDGED,

		/**
		 * The entries are equal by metaData (mode, length) but differ by
		 * modification-timestamp.
		 */
		DIFFER_BY_TIMESTAMP
	}

	/**
	 * Is the file mode of the current entry different than the given raw mode?
	 *
	 * @param rawMode
	 * @return true if different, false otherwise
	 */
	public boolean isModeDifferent(final int rawMode) {
		// Determine difference in mode-bits of file and index-entry. In the
		// bitwise presentation of modeDiff we'll have a '1' when the two modes
		// differ at this position.
		int modeDiff = getEntryRawMode() ^ rawMode;

		if (modeDiff == 0)
			return false;

		// Do not rely on filemode differences in case of symbolic links
		if (FileMode.SYMLINK.equals(rawMode))
			return false;

		// Ignore the executable file bits if WorkingTreeOptions tell me to
		// do so. Ignoring is done by setting the bits representing a
		// EXECUTABLE_FILE to '0' in modeDiff
		if (!state.options.isFileMode())
			modeDiff &= ~FileMode.EXECUTABLE_FILE.getBits();
		return modeDiff != 0;
	}

	/**
	 * Compare the metadata (mode, length, modification-timestamp) of the
	 * current entry and a {@link DirCacheEntry}
	 *
	 * @param entry
	 *            the {@link DirCacheEntry} to compare with
	 * @return a {@link MetadataDiff} which tells whether and how the entries
	 *         metadata differ
	 */
	public MetadataDiff compareMetadata(DirCacheEntry entry) {
		if (entry.isAssumeValid())
			return MetadataDiff.EQUAL;

		if (entry.isUpdateNeeded())
			return MetadataDiff.DIFFER_BY_METADATA;

		if (!entry.isSmudged() && entry.getLength() != (int) getEntryLength())
			return MetadataDiff.DIFFER_BY_METADATA;

		if (isModeDifferent(entry.getRawMode()))
			return MetadataDiff.DIFFER_BY_METADATA;

		// Git under windows only stores seconds so we round the timestamp
		// Java gives us if it looks like the timestamp in index is seconds
		// only. Otherwise we compare the timestamp at millisecond precision,
		// unless core.checkstat is set to "minimal", in which case we only
		// compare the whole second part.
		long cacheLastModified = entry.getLastModified();
		long fileLastModified = getEntryLastModified();
		long lastModifiedMillis = fileLastModified % 1000;
		long cacheMillis = cacheLastModified % 1000;
		if (getOptions().getCheckStat() == CheckStat.MINIMAL) {
			fileLastModified = fileLastModified - lastModifiedMillis;
			cacheLastModified = cacheLastModified - cacheMillis;
		} else if (cacheMillis == 0)
			fileLastModified = fileLastModified - lastModifiedMillis;
		// Some Java version on Linux return whole seconds only even when
		// the file systems supports more precision.
		else if (lastModifiedMillis == 0)
			cacheLastModified = cacheLastModified - cacheMillis;

		if (fileLastModified != cacheLastModified)
			return MetadataDiff.DIFFER_BY_TIMESTAMP;
		else if (!entry.isSmudged())
			// The file is clean when you look at timestamps.
			return MetadataDiff.EQUAL;
		else
			return MetadataDiff.SMUDGED;
	}

	/**
	 * Checks whether this entry differs from a given entry from the
	 * {@link DirCache}.
	 *
	 * File status information is used and if status is same we consider the
	 * file identical to the state in the working directory. Native git uses
	 * more stat fields than we have accessible in Java.
	 *
	 * @param entry
	 *            the entry from the dircache we want to compare against
	 * @param forceContentCheck
	 *            True if the actual file content should be checked if
	 *            modification time differs.
	 * @return true if content is most likely different.
	 * @deprecated Use {@link #isModified(DirCacheEntry, boolean, ObjectReader)}
	 */
	public boolean isModified(DirCacheEntry entry, boolean forceContentCheck) {
		return isModified(entry, forceContentCheck, null);
	}

	/**
	 * Checks whether this entry differs from a given entry from the
	 * {@link DirCache}.
	 *
	 * File status information is used and if status is same we consider the
	 * file identical to the state in the working directory. Native git uses
	 * more stat fields than we have accessible in Java.
	 *
	 * @param entry
	 *            the entry from the dircache we want to compare against
	 * @param forceContentCheck
	 *            True if the actual file content should be checked if
	 *            modification time differs.
	 * @param reader
	 *            access to repository objects if necessary. Should not be null.
	 * @return true if content is most likely different.
	 * @since 3.3
	 */
	public boolean isModified(DirCacheEntry entry, boolean forceContentCheck,
			ObjectReader reader) {
		MetadataDiff diff = compareMetadata(entry);
		switch (diff) {
		case DIFFER_BY_TIMESTAMP:
			if (forceContentCheck)
				// But we are told to look at content even though timestamps
				// tell us about modification
				return contentCheck(entry, reader);
			else
				// We are told to assume a modification if timestamps differs
				return true;
		case SMUDGED:
			// The file is clean by timestamps but the entry was smudged.
			// Lets do a content check
			return contentCheck(entry, reader);
		case EQUAL:
			return false;
		case DIFFER_BY_METADATA:
			return true;
		default:
			throw new IllegalStateException(MessageFormat.format(
					JGitText.get().unexpectedCompareResult, diff.name()));
		}
	}

	/**
	 * Get the file mode to use for the current entry when it is to be updated
	 * in the index.
	 *
	 * @param indexIter
	 *            {@link DirCacheIterator} positioned at the same entry as this
	 *            iterator or null if no {@link DirCacheIterator} is available
	 *            at this iterator's current entry
	 * @return index file mode
	 */
	public FileMode getIndexFileMode(final DirCacheIterator indexIter) {
		final FileMode wtMode = getEntryFileMode();
		if (indexIter == null)
			return wtMode;
		if (getOptions().isFileMode())
			return wtMode;
		final FileMode iMode = indexIter.getEntryFileMode();
		if (FileMode.REGULAR_FILE == wtMode
				&& FileMode.EXECUTABLE_FILE == iMode)
			return iMode;
		if (FileMode.EXECUTABLE_FILE == wtMode
				&& FileMode.REGULAR_FILE == iMode)
			return iMode;
		return wtMode;
	}

	/**
	 * Compares the entries content with the content in the filesystem.
	 * Unsmudges the entry when it is detected that it is clean.
	 *
	 * @param entry
	 *            the entry to be checked
	 * @param reader
	 *            acccess to repository data if necessary
	 * @return <code>true</code> if the content doesn't match,
	 *         <code>false</code> if it matches
	 */
	private boolean contentCheck(DirCacheEntry entry, ObjectReader reader) {
		if (getEntryObjectId().equals(entry.getObjectId())) {
			// Content has not changed

			// We know the entry can't be racily clean because it's still clean.
			// Therefore we unsmudge the entry!
			// If by any chance we now unsmudge although we are still in the
			// same time-slot as the last modification to the index file the
			// next index write operation will smudge again.
			// Caution: we are unsmudging just by setting the length of the
			// in-memory entry object. It's the callers task to detect that we
			// have modified the entry and to persist the modified index.
			entry.setLength((int) getEntryLength());

			return false;
		} else {
			// Content differs: that's a real change, perhaps
			if (reader == null) // deprecated use, do no further checks
				return true;
			switch (getOptions().getAutoCRLF()) {
			case INPUT:
			case TRUE:
				InputStream dcIn = null;
				try {
					ObjectLoader loader = reader.open(entry.getObjectId());
					if (loader == null)
						return true;

					// We need to compute the length, but only if it is not
					// a binary stream.
					dcIn = new EolCanonicalizingInputStream(
							loader.openStream(), true, true /* abort if binary */);
					long dcInLen;
					try {
						dcInLen = computeLength(dcIn);
					} catch (EolCanonicalizingInputStream.IsBinaryException e) {
						return true;
					} finally {
						dcIn.close();
					}

					dcIn = new EolCanonicalizingInputStream(
							loader.openStream(), true);
					byte[] autoCrLfHash = computeHash(dcIn, dcInLen);
					boolean changed = getEntryObjectId().compareTo(
							autoCrLfHash, 0) != 0;
					return changed;
				} catch (IOException e) {
					return true;
				} finally {
					if (dcIn != null)
						try {
							dcIn.close();
						} catch (IOException e) {
							// empty
						}
				}
			case FALSE:
				break;
			}
			return true;
		}
	}

	private long computeLength(InputStream in) throws IOException {
		// Since we only care about the length, use skip. The stream
		// may be able to more efficiently wade through its data.
		//
		long length = 0;
		for (;;) {
			long n = in.skip(1 << 20);
			if (n <= 0)
				break;
			length += n;
		}
		return length;
	}

	private byte[] computeHash(InputStream in, long length) throws IOException {
		final MessageDigest contentDigest = state.contentDigest;
		final byte[] contentReadBuffer = state.contentReadBuffer;

		contentDigest.reset();
		contentDigest.update(hblob);
		contentDigest.update((byte) ' ');

		long sz = length;
		if (sz == 0) {
			contentDigest.update((byte) '0');
		} else {
			final int bufn = contentReadBuffer.length;
			int p = bufn;
			do {
				contentReadBuffer[--p] = digits[(int) (sz % 10)];
				sz /= 10;
			} while (sz > 0);
			contentDigest.update(contentReadBuffer, p, bufn - p);
		}
		contentDigest.update((byte) 0);

		for (;;) {
			final int r = in.read(contentReadBuffer);
			if (r <= 0)
				break;
			contentDigest.update(contentReadBuffer, 0, r);
			sz += r;
		}
		if (sz != length)
			return zeroid;
		return contentDigest.digest();
	}

	/** A single entry within a working directory tree. */
	protected static abstract class Entry {
		byte[] encodedName;

		int encodedNameLen;

		void encodeName(final CharsetEncoder enc) {
			final ByteBuffer b;
			try {
				b = enc.encode(CharBuffer.wrap(getName()));
			} catch (CharacterCodingException e) {
				// This should so never happen.
				throw new RuntimeException(MessageFormat.format(
						JGitText.get().unencodeableFile, getName()));
			}

			encodedNameLen = b.limit();
			if (b.hasArray() && b.arrayOffset() == 0)
				encodedName = b.array();
			else
				b.get(encodedName = new byte[encodedNameLen]);
		}

		public String toString() {
			return getMode().toString() + " " + getName(); //$NON-NLS-1$
		}

		/**
		 * Get the type of this entry.
		 * <p>
		 * <b>Note: Efficient implementation required.</b>
		 * <p>
		 * The implementation of this method must be efficient. If a subclass
		 * needs to compute the value they should cache the reference within an
		 * instance member instead.
		 *
		 * @return a file mode constant from {@link FileMode}.
		 */
		public abstract FileMode getMode();

		/**
		 * Get the byte length of this entry.
		 * <p>
		 * <b>Note: Efficient implementation required.</b>
		 * <p>
		 * The implementation of this method must be efficient. If a subclass
		 * needs to compute the value they should cache the reference within an
		 * instance member instead.
		 *
		 * @return size of this file, in bytes.
		 */
		public abstract long getLength();

		/**
		 * Get the last modified time of this entry.
		 * <p>
		 * <b>Note: Efficient implementation required.</b>
		 * <p>
		 * The implementation of this method must be efficient. If a subclass
		 * needs to compute the value they should cache the reference within an
		 * instance member instead.
		 *
		 * @return time since the epoch (in ms) of the last change.
		 */
		public abstract long getLastModified();

		/**
		 * Get the name of this entry within its directory.
		 * <p>
		 * Efficient implementations are not required. The caller will obtain
		 * the name only once and cache it once obtained.
		 *
		 * @return name of the entry.
		 */
		public abstract String getName();

		/**
		 * Obtain an input stream to read the file content.
		 * <p>
		 * Efficient implementations are not required. The caller will usually
		 * obtain the stream only once per entry, if at all.
		 * <p>
		 * The input stream should not use buffering if the implementation can
		 * avoid it. The caller will buffer as necessary to perform efficient
		 * block IO operations.
		 * <p>
		 * The caller will close the stream once complete.
		 *
		 * @return a stream to read from the file.
		 * @throws IOException
		 *             the file could not be opened for reading.
		 */
		public abstract InputStream openInputStream() throws IOException;
	}

	/** Magic type indicating we know rules exist, but they aren't loaded. */
	private static class PerDirectoryIgnoreNode extends IgnoreNode {
		final Entry entry;

		PerDirectoryIgnoreNode(Entry entry) {
			super(Collections.<IgnoreRule> emptyList());
			this.entry = entry;
		}

		IgnoreNode load() throws IOException {
			IgnoreNode r = new IgnoreNode();
			InputStream in = entry.openInputStream();
			try {
				r.parse(in);
			} finally {
				in.close();
			}
			return r.getRules().isEmpty() ? null : r;
		}
	}

	/** Magic type indicating there may be rules for the top level. */
	private static class RootIgnoreNode extends PerDirectoryIgnoreNode {
		final Repository repository;

		RootIgnoreNode(Entry entry, Repository repository) {
			super(entry);
			this.repository = repository;
		}

		@Override
		IgnoreNode load() throws IOException {
			IgnoreNode r;
			if (entry != null) {
				r = super.load();
				if (r == null)
					r = new IgnoreNode();
			} else {
				r = new IgnoreNode();
			}

			FS fs = repository.getFS();
			String path = repository.getConfig().get(CoreConfig.KEY)
					.getExcludesFile();
			if (path != null) {
				File excludesfile;
				if (path.startsWith("~/")) //$NON-NLS-1$
					excludesfile = fs.resolve(fs.userHome(), path.substring(2));
				else
					excludesfile = fs.resolve(null, path);
				loadRulesFromFile(r, excludesfile);
			}

			File exclude = fs.resolve(repository.getDirectory(),
					Constants.INFO_EXCLUDE);
			loadRulesFromFile(r, exclude);

			return r.getRules().isEmpty() ? null : r;
		}

		private void loadRulesFromFile(IgnoreNode r, File exclude)
				throws FileNotFoundException, IOException {
			if (exclude.exists()) {
				FileInputStream in = new FileInputStream(exclude);
				try {
					r.parse(in);
				} finally {
					in.close();
				}
			}
		}
	}

	private static final class IteratorState {
		/** Options used to process the working tree. */
		final WorkingTreeOptions options;

		/** File name character encoder. */
		final CharsetEncoder nameEncoder;

		/** Digest computer for {@link #contentId} computations. */
		MessageDigest contentDigest;

		/** Buffer used to perform {@link #contentId} computations. */
		byte[] contentReadBuffer;

		/** TreeWalk with a (supposedly) matching DirCacheIterator. */
		TreeWalk walk;

		/** Position of the matching {@link DirCacheIterator}. */
		int dirCacheTree;

		IteratorState(WorkingTreeOptions options) {
			this.options = options;
			this.nameEncoder = Constants.CHARSET.newEncoder();
		}

		void initializeDigestAndReadBuffer() {
			if (contentDigest == null) {
				contentDigest = Constants.newMessageDigest();
				contentReadBuffer = new byte[BUFFER_SIZE];
			}
		}
	}
}

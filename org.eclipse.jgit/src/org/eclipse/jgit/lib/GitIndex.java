/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2007, Robin Rosenberg <me@lathund.dewire.com>
 * Copyright (C) 2007-2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Roger C. Soares <rogersoares@intelinet.com.br>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.events.IndexChangedEvent;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * A representation of the Git index.
 *
 * The index points to the objects currently checked out or in the process of
 * being prepared for committing or objects involved in an unfinished merge.
 *
 * The abstract format is:<br/> path stage flags statdata SHA-1
 * <ul>
 * <li>Path is the relative path in the workdir</li>
 * <li>stage is 0 (normally), but when
 * merging 1 is the common ancestor version, 2 is 'our' version and 3 is 'their'
 * version. A fully resolved merge only contains stage 0.</li>
 * <li>flags is the object type and information of validity</li>
 * <li>statdata is the size of this object and some other file system specifics,
 * some of it ignored by JGit</li>
 * <li>SHA-1 represents the content of the references object</li>
 * </ul>
 *
 * An index can also contain a tree cache which we ignore for now. We drop the
 * tree cache when writing the index.
 *
 * @deprecated Use {@link DirCache} instead.
 */
public class GitIndex {

	/** Stage 0 represents merged entries. */
	public static final int STAGE_0 = 0;

	private RandomAccessFile cache;

	private File cacheFile;

	// Index is modified
	private boolean changed;

	// Stat information updated
	private boolean statDirty;

	private Header header;

	private long lastCacheTime;

	private final Repository db;

	private Map<byte[], Entry> entries = new TreeMap<byte[], Entry>(new Comparator<byte[]>() {
		public int compare(byte[] o1, byte[] o2) {
			for (int i = 0; i < o1.length && i < o2.length; ++i) {
				int c = (o1[i] & 0xff) - (o2[i] & 0xff);
				if (c != 0)
					return c;
			}
			if (o1.length < o2.length)
				return -1;
			else if (o1.length > o2.length)
				return 1;
			return 0;
		}
	});

	/**
	 * Construct a Git index representation.
	 * @param db
	 */
	public GitIndex(Repository db) {
		this.db = db;
		this.cacheFile = db.getIndexFile();
	}

	/**
	 * @return true if we have modified the index in memory since reading it from disk
	 */
	public boolean isChanged() {
		return changed || statDirty;
	}

	/**
	 * Reread index data from disk if the index file has been changed
	 * @throws IOException
	 */
	public void rereadIfNecessary() throws IOException {
		if (cacheFile.exists() && cacheFile.lastModified() != lastCacheTime) {
			read();
			db.fireEvent(new IndexChangedEvent());
		}
	}

	/**
	 * Add the content of a file to the index.
	 *
	 * @param wd workdir
	 * @param f the file
	 * @return a new or updated index entry for the path represented by f
	 * @throws IOException
	 */
	public Entry add(File wd, File f) throws IOException {
		byte[] key = makeKey(wd, f);
		Entry e = entries.get(key);
		if (e == null) {
			e = new Entry(key, f, 0);
			entries.put(key, e);
		} else {
			e.update(f);
		}
		return e;
	}

	/**
	 * Add the content of a file to the index.
	 *
	 * @param wd
	 *            workdir
	 * @param f
	 *            the file
	 * @param content
	 *            content of the file
	 * @return a new or updated index entry for the path represented by f
	 * @throws IOException
	 */
	public Entry add(File wd, File f, byte[] content) throws IOException {
		byte[] key = makeKey(wd, f);
		Entry e = entries.get(key);
		if (e == null) {
			e = new Entry(key, f, 0, content);
			entries.put(key, e);
		} else {
			e.update(f, content);
		}
		return e;
	}

	/**
	 * Remove a path from the index.
	 *
	 * @param wd
	 *            workdir
	 * @param f
	 *            the file whose path shall be removed.
	 * @return true if such a path was found (and thus removed)
	 * @throws IOException
	 */
	public boolean remove(File wd, File f) throws IOException {
		byte[] key = makeKey(wd, f);
		return entries.remove(key) != null;
	}

	/**
	 * Read the cache file into memory.
	 *
	 * @throws IOException
	 */
	public void read() throws IOException {
		changed = false;
		statDirty = false;
		if (!cacheFile.exists()) {
			header = null;
			entries.clear();
			lastCacheTime = 0;
			return;
		}
		cache = new RandomAccessFile(cacheFile, "r");
		try {
			FileChannel channel = cache.getChannel();
			ByteBuffer buffer = ByteBuffer.allocateDirect((int) cacheFile.length());
			buffer.order(ByteOrder.BIG_ENDIAN);
			int j = channel.read(buffer);
			if (j != buffer.capacity())
				throw new IOException(MessageFormat.format(JGitText.get().couldNotReadIndexInOneGo
						, j, buffer.capacity()));
			buffer.flip();
			header = new Header(buffer);
			entries.clear();
			for (int i = 0; i < header.entries; ++i) {
				Entry entry = new Entry(buffer);
				final GitIndex.Entry existing = entries.get(entry.name);
				entries.put(entry.name, entry);
				if (existing != null) {
					entry.stages |= existing.stages;
				}
			}
			lastCacheTime = cacheFile.lastModified();
		} finally {
			cache.close();
		}
	}

	/**
	 * Write content of index to disk.
	 *
	 * @throws IOException
	 */
	public void write() throws IOException {
		checkWriteOk();
		File tmpIndex = new File(cacheFile.getAbsoluteFile() + ".tmp");
		File lock = new File(cacheFile.getAbsoluteFile() + ".lock");
		if (!lock.createNewFile())
			throw new IOException(JGitText.get().indexFileIsInUse);
		try {
			FileOutputStream fileOutputStream = new FileOutputStream(tmpIndex);
			FileChannel fc = fileOutputStream.getChannel();
			ByteBuffer buf = ByteBuffer.allocate(4096);
			MessageDigest newMessageDigest = Constants.newMessageDigest();
			header = new Header(entries);
			header.write(buf);
			buf.flip();
			newMessageDigest
					.update(buf.array(), buf.arrayOffset(), buf.limit());
			fc.write(buf);
			buf.flip();
			buf.clear();
			for (Iterator i = entries.values().iterator(); i.hasNext();) {
				Entry e = (Entry) i.next();
				e.write(buf);
				buf.flip();
				newMessageDigest.update(buf.array(), buf.arrayOffset(), buf
						.limit());
				fc.write(buf);
				buf.flip();
				buf.clear();
			}
			buf.put(newMessageDigest.digest());
			buf.flip();
			fc.write(buf);
			fc.close();
			fileOutputStream.close();
			if (cacheFile.exists()) {
				if (db.getFS().retryFailedLockFileCommit()) {
					// file deletion fails on windows if another
					// thread is reading the file concurrently
					// So let's try 10 times...
					boolean deleted = false;
					for (int i = 0; i < 10; i++) {
						if (cacheFile.delete()) {
							deleted = true;
							break;
						}
						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							// ignore
						}
					}
					if (!deleted)
						throw new IOException(
								JGitText.get().couldNotRenameDeleteOldIndex);
				} else {
					if (!cacheFile.delete())
						throw new IOException(
								JGitText.get().couldNotRenameDeleteOldIndex);
				}
			}
			if (!tmpIndex.renameTo(cacheFile))
				throw new IOException(
						JGitText.get().couldNotRenameTemporaryIndexFileToIndex);
			changed = false;
			statDirty = false;
			lastCacheTime = cacheFile.lastModified();
			db.fireEvent(new IndexChangedEvent());
		} finally {
			if (!lock.delete())
				throw new IOException(
						JGitText.get().couldNotDeleteLockFileShouldNotHappen);
			if (tmpIndex.exists() && !tmpIndex.delete())
				throw new IOException(
						JGitText.get().couldNotDeleteTemporaryIndexFileShouldNotHappen);
		}
	}

	private void checkWriteOk() throws IOException {
		for (Iterator i = entries.values().iterator(); i.hasNext();) {
			Entry e = (Entry) i.next();
			if (e.getStage() != 0) {
				throw new NotSupportedException(JGitText.get().cannotWorkWithOtherStagesThanZeroRightNow);
			}
		}
	}

	private boolean File_canExecute(File f){
		return db.getFS().canExecute(f);
	}

	private boolean File_setExecute(File f, boolean value) {
		return db.getFS().setExecute(f, value);
	}

	private boolean File_hasExecute() {
		return db.getFS().supportsExecute();
	}

	static byte[] makeKey(File wd, File f) {
		if (!f.getPath().startsWith(wd.getPath()))
			throw new Error(JGitText.get().pathIsNotInWorkingDir);
		String relName = Repository.stripWorkDir(wd, f);
		return Constants.encode(relName);
	}

	Boolean filemode;
	private boolean config_filemode() {
		// temporary til we can actually set parameters. We need to be able
		// to change this for testing.
		if (filemode != null)
			return filemode.booleanValue();
		Config config = db.getConfig();
		filemode = Boolean.valueOf(config.getBoolean("core", null, "filemode", true));
		return filemode.booleanValue();
	}

	/**
	 * An index entry
	 *
	 * @deprecated Use {@link org.eclipse.jgit.dircache.DirCacheEntry}.
	 */
	@Deprecated
	public class Entry {
		private long ctime;

		private long mtime;

		private int dev;

		private int ino;

		private int mode;

		private int uid;

		private int gid;

		private int size;

		private ObjectId sha1;

		private short flags;

		private byte[] name;

		private int stages;

		Entry(byte[] key, File f, int stage)
				throws IOException {
			ctime = f.lastModified() * 1000000L;
			mtime = ctime; // we use same here
			dev = -1;
			ino = -1;
			if (config_filemode() && File_canExecute(f))
				mode = FileMode.EXECUTABLE_FILE.getBits();
			else
				mode = FileMode.REGULAR_FILE.getBits();
			uid = -1;
			gid = -1;
			size = (int) f.length();
			ObjectInserter inserter = db.newObjectInserter();
			try {
				InputStream in = new FileInputStream(f);
				try {
					sha1 = inserter.insert(Constants.OBJ_BLOB, f.length(), in);
				} finally {
					in.close();
				}
				inserter.flush();
			} finally {
				inserter.release();
			}
			name = key;
			flags = (short) ((stage << 12) | name.length); // TODO: fix flags
			stages = (1 >> getStage());
		}

		Entry(byte[] key, File f, int stage, byte[] newContent)
				throws IOException {
			ctime = f.lastModified() * 1000000L;
			mtime = ctime; // we use same here
			dev = -1;
			ino = -1;
			if (config_filemode() && File_canExecute(f))
				mode = FileMode.EXECUTABLE_FILE.getBits();
			else
				mode = FileMode.REGULAR_FILE.getBits();
			uid = -1;
			gid = -1;
			size = newContent.length;
			ObjectInserter inserter = db.newObjectInserter();
			try {
				InputStream in = new FileInputStream(f);
				try {
					sha1 = inserter.insert(Constants.OBJ_BLOB, newContent);
				} finally {
					in.close();
				}
				inserter.flush();
			} finally {
				inserter.release();
			}
			name = key;
			flags = (short) ((stage << 12) | name.length); // TODO: fix flags
			stages = (1 >> getStage());
		}

		Entry(TreeEntry f, int stage) {
			ctime = -1; // hmm
			mtime = -1;
			dev = -1;
			ino = -1;
			mode = f.getMode().getBits();
			uid = -1;
			gid = -1;
			try {
				size = (int) db.open(f.getId(), Constants.OBJ_BLOB).getSize();
			} catch (IOException e) {
				e.printStackTrace();
				size = -1;
			}
			sha1 = f.getId();
			name = Constants.encode(f.getFullName());
			flags = (short) ((stage << 12) | name.length); // TODO: fix flags
			stages = (1 >> getStage());
		}

		Entry(ByteBuffer b) {
			int startposition = b.position();
			ctime = b.getInt() * 1000000000L + (b.getInt() % 1000000000L);
			mtime = b.getInt() * 1000000000L + (b.getInt() % 1000000000L);
			dev = b.getInt();
			ino = b.getInt();
			mode = b.getInt();
			uid = b.getInt();
			gid = b.getInt();
			size = b.getInt();
			byte[] sha1bytes = new byte[Constants.OBJECT_ID_LENGTH];
			b.get(sha1bytes);
			sha1 = ObjectId.fromRaw(sha1bytes);
			flags = b.getShort();
			stages = (1 << getStage());
			name = new byte[flags & 0xFFF];
			b.get(name);
			b
					.position(startposition
							+ ((8 + 8 + 4 + 4 + 4 + 4 + 4 + 4 + 20 + 2
									+ name.length + 8) & ~7));
		}

		/**
		 * Update this index entry with stat and SHA-1 information if it looks
		 * like the file has been modified in the workdir.
		 *
		 * @param f
		 *            file in work dir
		 * @return true if a change occurred
		 * @throws IOException
		 */
		public boolean update(File f) throws IOException {
			long lm = f.lastModified() * 1000000L;
			boolean modified = mtime != lm;
			mtime = lm;
			if (size != f.length())
				modified = true;
			if (config_filemode()) {
				if (File_canExecute(f) != FileMode.EXECUTABLE_FILE.equals(mode)) {
					mode = FileMode.EXECUTABLE_FILE.getBits();
					modified = true;
				}
			}
			if (modified) {
				size = (int) f.length();
				ObjectInserter oi = db.newObjectInserter();
				try {
					InputStream in = new FileInputStream(f);
					try {
						ObjectId newsha1 = oi.insert(Constants.OBJ_BLOB, f
								.length(), in);
						oi.flush();
						if (!newsha1.equals(sha1))
							modified = true;
						sha1 = newsha1;
					} finally {
						in.close();
					}
				} finally {
					oi.release();
				}
			}
			return modified;
		}

		/**
		 * Update this index entry with stat and SHA-1 information if it looks
		 * like the file has been modified in the workdir.
		 *
		 * @param f
		 *            file in work dir
		 * @param newContent
		 *            the new content of the file
		 * @return true if a change occurred
		 * @throws IOException
		 */
		public boolean update(File f, byte[] newContent) throws IOException {
			boolean modified = false;
			size = newContent.length;
			ObjectInserter oi = db.newObjectInserter();
			try {
				ObjectId newsha1 = oi.insert(Constants.OBJ_BLOB, newContent);
				oi.flush();
				if (!newsha1.equals(sha1))
					modified = true;
				sha1 = newsha1;
			} finally {
				oi.release();
			}
			return modified;
		}

		void write(ByteBuffer buf) {
			int startposition = buf.position();
			buf.putInt((int) (ctime / 1000000000L));
			buf.putInt((int) (ctime % 1000000000L));
			buf.putInt((int) (mtime / 1000000000L));
			buf.putInt((int) (mtime % 1000000000L));
			buf.putInt(dev);
			buf.putInt(ino);
			buf.putInt(mode);
			buf.putInt(uid);
			buf.putInt(gid);
			buf.putInt(size);
			sha1.copyRawTo(buf);
			buf.putShort(flags);
			buf.put(name);
			int end = startposition
					+ ((8 + 8 + 4 + 4 + 4 + 4 + 4 + 4 + 20 + 2 + name.length + 8) & ~7);
			int remain = end - buf.position();
			while (remain-- > 0)
				buf.put((byte) 0);
		}

		/**
		 * Check if an entry's content is different from the cache,
		 *
		 * File status information is used and status is same we
		 * consider the file identical to the state in the working
		 * directory. Native git uses more stat fields than we
		 * have accessible in Java.
		 *
		 * @param wd working directory to compare content with
		 * @return true if content is most likely different.
		 */
		public boolean isModified(File wd) {
			return isModified(wd, false);
		}

		/**
		 * Check if an entry's content is different from the cache,
		 *
		 * File status information is used and status is same we
		 * consider the file identical to the state in the working
		 * directory. Native git uses more stat fields than we
		 * have accessible in Java.
		 *
		 * @param wd working directory to compare content with
		 * @param forceContentCheck True if the actual file content
		 * should be checked if modification time differs.
		 *
		 * @return true if content is most likely different.
		 */
		public boolean isModified(File wd, boolean forceContentCheck) {

			if (isAssumedValid())
				return false;

			if (isUpdateNeeded())
				return true;

			File file = getFile(wd);
			long length = file.length();
			if (length == 0) {
				if (!file.exists())
					return true;
			}
			if (length != size)
				return true;

			// JDK1.6 has file.canExecute
			// if (file.canExecute() != FileMode.EXECUTABLE_FILE.equals(mode))
			// return true;
			final int exebits = FileMode.EXECUTABLE_FILE.getBits()
					^ FileMode.REGULAR_FILE.getBits();

			if (config_filemode() && FileMode.EXECUTABLE_FILE.equals(mode)) {
				if (!File_canExecute(file)&& File_hasExecute())
					return true;
			} else {
				if (FileMode.REGULAR_FILE.equals(mode&~exebits)) {
					if (!file.isFile())
						return true;
					if (config_filemode() && File_canExecute(file) && File_hasExecute())
						return true;
				} else {
					if (FileMode.SYMLINK.equals(mode)) {
						return true;
					} else {
						if (FileMode.TREE.equals(mode)) {
							if (!file.isDirectory())
								return true;
						} else {
							System.out.println(MessageFormat.format(JGitText.get().doesNotHandleMode
									, mode, file));
							return true;
						}
					}
				}
			}

			// Git under windows only stores seconds so we round the timestamp
			// Java gives us if it looks like the timestamp in index is seconds
			// only. Otherwise we compare the timestamp at millisecond prevision.
			long javamtime = mtime / 1000000L;
			long lastm = file.lastModified();
			if (javamtime % 1000 == 0)
				lastm = lastm - lastm % 1000;
			if (lastm != javamtime) {
				if (!forceContentCheck)
					return true;

				try {
					InputStream is = new FileInputStream(file);
					try {
						ObjectId newId = new ObjectInserter.Formatter().idFor(
								Constants.OBJ_BLOB, file.length(), is);
						return !newId.equals(sha1);
					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						try {
							is.close();
						} catch (IOException e) {
							// can't happen, but if it does we ignore it
							e.printStackTrace();
						}
					}
				} catch (FileNotFoundException e) {
					// should not happen because we already checked this
					e.printStackTrace();
					throw new Error(e);
				}
			}
			return false;
		}

		/**
		 * Returns the stages in which the entry's file is recorded in the index.
		 * The stages are bit-encoded: bit N is set if the file is present
		 * in stage N. In particular, the N-th bit will be set if this entry
		 * itself is in stage N (see getStage()).
		 *
		 * @return flags denoting stages
		 * @see #getStage()
		 */
		public int getStages() {
			return stages;
		}

		// for testing
		void forceRecheck() {
			mtime = -1;
		}

		private File getFile(File wd) {
			return new File(wd, getName());
		}

		public String toString() {
			return getName() + "/SHA-1(" + sha1.name() + ")/M:"
					+ new Date(ctime / 1000000L) + "/C:"
					+ new Date(mtime / 1000000L) + "/d" + dev + "/i" + ino
					+ "/m" + Integer.toString(mode, 8) + "/u" + uid + "/g"
					+ gid + "/s" + size + "/f" + flags + "/@" + getStage();
		}

		/**
		 * @return path name for this entry
		 */
		public String getName() {
			return RawParseUtils.decode(name);
		}

		/**
		 * @return path name for this entry as byte array, hopefully UTF-8 encoded
		 */
		public byte[] getNameUTF8() {
			return name;
		}

		/**
		 * @return SHA-1 of the entry managed by this index
		 */
		public ObjectId getObjectId() {
			return sha1;
		}

		/**
		 * @return the stage this entry is in
		 */
		public int getStage() {
			return (flags & 0x3000) >> 12;
		}

		/**
		 * @return size of disk object
		 */
		public int getSize() {
			return size;
		}

		/**
		 * @return true if this entry shall be assumed valid
		 */
		public boolean isAssumedValid() {
			return (flags & 0x8000) != 0;
		}

		/**
		 * @return true if this entry should be checked for changes
		 */
		public boolean isUpdateNeeded() {
			return (flags & 0x4000) != 0;
		}

		/**
		 * Set whether to always assume this entry valid
		 *
		 * @param assumeValid true to ignore changes
		 */
		public void setAssumeValid(boolean assumeValid) {
			if (assumeValid)
				flags |= 0x8000;
			else
				flags &= ~0x8000;
		}

		/**
		 * Set whether this entry must be checked
		 *
		 * @param updateNeeded
		 */
		public void setUpdateNeeded(boolean updateNeeded) {
			if (updateNeeded)
				flags |= 0x4000;
			else
				flags &= ~0x4000;
		}

		/**
		 * Return raw file mode bits. See {@link FileMode}
		 * @return file mode bits
		 */
		public int getModeBits() {
			return mode;
		}
	}

	static class Header {
		private int signature;

		private int version;

		int entries;

		Header(ByteBuffer map) throws CorruptObjectException {
			read(map);
		}

		private void read(ByteBuffer buf) throws CorruptObjectException {
			signature = buf.getInt();
			version = buf.getInt();
			entries = buf.getInt();
			if (signature != 0x44495243)
				throw new CorruptObjectException(MessageFormat.format(
						JGitText.get().indexSignatureIsInvalid, signature));
			if (version != 2)
				throw new CorruptObjectException(MessageFormat.format(
						JGitText.get().unknownIndexVersionOrCorruptIndex, version));
		}

		void write(ByteBuffer buf) {
			buf.order(ByteOrder.BIG_ENDIAN);
			buf.putInt(signature);
			buf.putInt(version);
			buf.putInt(entries);
		}

		Header(Map entryset) {
			signature = 0x44495243;
			version = 2;
			entries = entryset.size();
		}
	}

	/**
	 * Read a Tree recursively into the index
	 *
	 * @param t The tree to read
	 *
	 * @throws IOException
	 */
	public void readTree(Tree t) throws IOException {
		entries.clear();
		readTree("", t);
	}

	void readTree(String prefix, Tree t) throws IOException {
		TreeEntry[] members = t.members();
		for (int i = 0; i < members.length; ++i) {
			TreeEntry te = members[i];
			String name;
			if (prefix.length() > 0)
				name = prefix + "/" + te.getName();
			else
				name = te.getName();
			if (te instanceof Tree) {
				readTree(name, (Tree) te);
			} else {
				Entry e = new Entry(te, 0);
				entries.put(Constants.encode(name), e);
			}
		}
	}

	/**
	 * Add tree entry to index
	 * @param te tree entry
	 * @return new or modified index entry
	 * @throws IOException
	 */
	public Entry addEntry(TreeEntry te) throws IOException {
		byte[] key = Constants.encode(te.getFullName());
		Entry e = new Entry(te, 0);
		entries.put(key, e);
		return e;
	}

	/**
	 * Check out content of the content represented by the index
	 *
	 * @param wd
	 *            workdir
	 * @throws IOException
	 */
	public void checkout(File wd) throws IOException {
		for (Entry e : entries.values()) {
			if (e.getStage() != 0)
				continue;
			checkoutEntry(wd, e);
		}
	}

	/**
	 * Check out content of the specified index entry
	 *
	 * @param wd workdir
	 * @param e index entry
	 * @throws IOException
	 */
	public void checkoutEntry(File wd, Entry e) throws IOException {
		ObjectLoader ol = db.open(e.sha1, Constants.OBJ_BLOB);
		File file = new File(wd, e.getName());
		file.delete();
		FileUtils.mkdirs(file.getParentFile(), true);
		FileOutputStream dst = new FileOutputStream(file);
		try {
			ol.copyTo(dst);
		} finally {
			dst.close();
		}
		if (config_filemode() && File_hasExecute()) {
			if (FileMode.EXECUTABLE_FILE.equals(e.mode)) {
				if (!File_canExecute(file))
					File_setExecute(file, true);
			} else {
				if (File_canExecute(file))
					File_setExecute(file, false);
			}
		}
		e.mtime = file.lastModified() * 1000000L;
		e.ctime = e.mtime;
	}

	/**
	 * Construct and write tree out of index.
	 *
	 * @return SHA-1 of the constructed tree
	 *
	 * @throws IOException
	 */
	public ObjectId writeTree() throws IOException {
		checkWriteOk();
		ObjectInserter inserter = db.newObjectInserter();
			try {
			Tree current = new Tree(db);
			Stack<Tree> trees = new Stack<Tree>();
			trees.push(current);
			String[] prevName = new String[0];
			for (Entry e : entries.values()) {
				if (e.getStage() != 0)
					continue;
				String[] newName = splitDirPath(e.getName());
				int c = longestCommonPath(prevName, newName);
				while (c < trees.size() - 1) {
					current.setId(inserter.insert(Constants.OBJ_TREE, current.format()));
					trees.pop();
					current = trees.isEmpty() ? null : (Tree) trees.peek();
				}
				while (trees.size() < newName.length) {
					if (!current.existsTree(newName[trees.size() - 1])) {
						current = new Tree(current, Constants.encode(newName[trees.size() - 1]));
						current.getParent().addEntry(current);
						trees.push(current);
					} else {
						current = (Tree) current.findTreeMember(newName[trees
								.size() - 1]);
						trees.push(current);
					}
				}
				FileTreeEntry ne = new FileTreeEntry(current, e.sha1,
						Constants.encode(newName[newName.length - 1]),
						(e.mode & FileMode.EXECUTABLE_FILE.getBits()) == FileMode.EXECUTABLE_FILE.getBits());
				current.addEntry(ne);
			}
			while (!trees.isEmpty()) {
				current.setId(inserter.insert(Constants.OBJ_TREE, current.format()));
				trees.pop();
				if (!trees.isEmpty())
					current = trees.peek();
			}
			inserter.flush();
			return current.getId();
		} finally {
			inserter.release();
		}
	}

	String[] splitDirPath(String name) {
		String[] tmp = new String[name.length() / 2 + 1];
		int p0 = -1;
		int p1;
		int c = 0;
		while ((p1 = name.indexOf('/', p0 + 1)) != -1) {
			tmp[c++] = name.substring(p0 + 1, p1);
			p0 = p1;
		}
		tmp[c++] = name.substring(p0 + 1);
		String[] ret = new String[c];
		for (int i = 0; i < c; ++i) {
			ret[i] = tmp[i];
		}
		return ret;
	}

	int longestCommonPath(String[] a, String[] b) {
		int i;
		for (i = 0; i < a.length && i < b.length; ++i)
			if (!a[i].equals(b[i]))
				return i;
		return i;
	}

	/**
	 * Return the members of the index sorted by the unsigned byte
	 * values of the path names.
	 *
	 * Small beware: Unaccounted for are unmerged entries. You may want
	 * to abort if members with stage != 0 are found if you are doing
	 * any updating operations. All stages will be found after one another
	 * here later. Currently only one stage per name is returned.
	 *
	 * @return The index entries sorted
	 */
	public Entry[] getMembers() {
		return entries.values().toArray(new Entry[entries.size()]);
	}

	/**
	 * Look up an entry with the specified path.
	 *
	 * @param path
	 * @return index entry for the path or null if not in index.
	 * @throws UnsupportedEncodingException
	 */
	public Entry getEntry(String path) throws UnsupportedEncodingException {
		return entries.get(Repository.gitInternalSlash(Constants.encode(path)));
	}

	/**
	 * @return The repository holding this index.
	 */
	public Repository getRepository() {
		return db;
	}
}

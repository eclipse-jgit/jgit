/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.reftable;

import static java.lang.Math.log;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.internal.storage.reftable.BlockWriter.padBetweenBlocks;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.FILE_FOOTER_LEN;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.FILE_HEADER_LEN;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.FILE_HEADER_MAGIC;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.INDEX_BLOCK_TYPE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.LOG_BLOCK_TYPE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.MAX_BLOCK_SIZE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.MAX_RESTARTS;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.OBJ_BLOCK_TYPE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.REF_BLOCK_TYPE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.VERSION_1;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;

import java.io.IOException;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.reftable.BlockWriter.DeleteLogEntry;
import org.eclipse.jgit.internal.storage.reftable.BlockWriter.Entry;
import org.eclipse.jgit.internal.storage.reftable.BlockWriter.IndexEntry;
import org.eclipse.jgit.internal.storage.reftable.BlockWriter.LogEntry;
import org.eclipse.jgit.internal.storage.reftable.BlockWriter.ObjEntry;
import org.eclipse.jgit.internal.storage.reftable.BlockWriter.RefEntry;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.lib.ObjectIdSubclassMap;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.util.LongList;
import org.eclipse.jgit.util.NB;

/**
 * Writes a reftable formatted file.
 * <p>
 * A reftable can be written in a streaming fashion, provided the caller sorts
 * all references. A
 * {@link org.eclipse.jgit.internal.storage.reftable.ReftableWriter} is
 * single-use, and not thread-safe.
 */
public class ReftableWriter {
	private ReftableConfig config;
	private int refBlockSize;
	private int logBlockSize;
	private int restartInterval;
	private int maxIndexLevels;
	private boolean alignBlocks;
	private boolean indexObjects;

	private long minUpdateIndex;
	private long maxUpdateIndex;

	private OutputStream outputStream;
	private ReftableOutputStream out;
	private ObjectIdSubclassMap<RefList> obj2ref;

	private BlockWriter.Entry lastRef;
	private BlockWriter.Entry lastLog;
	private BlockWriter cur;
	private Section refs;
	private Section objs;
	private Section logs;
	private int objIdLen;
	private Stats stats;

	/**
	 * Initialize a writer with a default configuration.
	 *
	 * @param os
	 *            output stream.
	 */
	public ReftableWriter(OutputStream os) {
		this(new ReftableConfig(), os);
		lastRef = null;
		lastLog = null;
	}

	/**
	 * Initialize a writer with a configuration.
	 *
	 * @param cfg
	 *            configuration for the writer
	 * @param os
	 *            output stream.
	 */
	public ReftableWriter(ReftableConfig cfg, OutputStream os) {
		config = cfg;
		outputStream = os;
	}

	/**
	 * Set configuration for the writer.
	 *
	 * @param cfg
	 *            configuration for the writer.
	 * @return {@code this}
	 */
	public ReftableWriter setConfig(ReftableConfig cfg) {
		this.config = cfg != null ? cfg : new ReftableConfig();
		return this;
	}

	/**
	 * Set the minimum update index for log entries that appear in this
	 * reftable.
	 *
	 * @param min
	 *            the minimum update index for log entries that appear in this
	 *            reftable. This should be 1 higher than the prior reftable's
	 *            {@code maxUpdateIndex} if this table will be used in a stack.
	 * @return {@code this}
	 */
	public ReftableWriter setMinUpdateIndex(long min) {
		minUpdateIndex = min;
		return this;
	}

	/**
	 * Set the maximum update index for log entries that appear in this
	 * reftable.
	 *
	 * @param max
	 *            the maximum update index for log entries that appear in this
	 *            reftable. This should be at least 1 higher than the prior
	 *            reftable's {@code maxUpdateIndex} if this table will be used
	 *            in a stack.
	 * @return {@code this}
	 */
	public ReftableWriter setMaxUpdateIndex(long max) {
		maxUpdateIndex = max;
		return this;
	}

	/**
	 * Begin writing the reftable. Should be called only once. Call this
	 * if a stream was passed to the constructor.
	 *
	 * @return {@code this}
	 */
	public ReftableWriter begin() {
		if (out != null) {
			throw new IllegalStateException("begin() called twice.");//$NON-NLS-1$
		}

		refBlockSize = config.getRefBlockSize();
		logBlockSize = config.getLogBlockSize();
		restartInterval = config.getRestartInterval();
		maxIndexLevels = config.getMaxIndexLevels();
		alignBlocks = config.isAlignBlocks();
		indexObjects = config.isIndexObjects();

		if (refBlockSize <= 0) {
			refBlockSize = 4 << 10;
		} else if (refBlockSize > MAX_BLOCK_SIZE) {
			throw new IllegalArgumentException();
		}
		if (logBlockSize <= 0) {
			logBlockSize = 2 * refBlockSize;
		}
		if (restartInterval <= 0) {
			restartInterval = refBlockSize < (60 << 10) ? 16 : 64;
		}

		out = new ReftableOutputStream(outputStream, refBlockSize, alignBlocks);
		refs = new Section(REF_BLOCK_TYPE);
		if (indexObjects) {
			obj2ref = new ObjectIdSubclassMap<>();
		}
		writeFileHeader();
		return this;
	}

	/**
	 * Sort a collection of references and write them to the reftable.
	 * The input refs may not have duplicate names.
	 *
	 * @param refsToPack
	 *            references to sort and write.
	 * @return {@code this}
	 * @throws java.io.IOException
	 *             if reftable cannot be written.
	 */
	public ReftableWriter sortAndWriteRefs(Collection<Ref> refsToPack)
			throws IOException {
		Iterator<RefEntry> itr = refsToPack.stream()
				.map(r -> new RefEntry(r, maxUpdateIndex - minUpdateIndex))
				.sorted(Entry::compare)
				.iterator();
		RefEntry last = null;
		while (itr.hasNext()) {
			RefEntry entry = itr.next();
			if (last != null && Entry.compare(last, entry) == 0) {
				throwIllegalEntry(last, entry);
			}

			long blockPos = refs.write(entry);
			indexRef(entry.ref, blockPos);
			last = entry;
		}
		return this;
	}

	/**
	 * Write one reference to the reftable.
	 * <p>
	 * References must be passed in sorted order.
	 *
	 * @param ref
	 *            the reference to store.
	 * @throws java.io.IOException
	 *             if reftable cannot be written.
	 */
	public void writeRef(Ref ref) throws IOException {
		writeRef(ref, maxUpdateIndex);
	}

	/**
	 * Write one reference to the reftable.
	 * <p>
	 * References must be passed in sorted order.
	 *
	 * @param ref
	 *            the reference to store.
	 * @param updateIndex
	 *            the updateIndex that modified this reference. Must be
	 *            {@code >= minUpdateIndex} for this file.
	 * @throws java.io.IOException
	 *             if reftable cannot be written.
	 */
	public void writeRef(Ref ref, long updateIndex) throws IOException {
		if (updateIndex < minUpdateIndex) {
			throw new IllegalArgumentException();
		}
		long d = updateIndex - minUpdateIndex;
		RefEntry entry = new RefEntry(ref, d);
		if (lastRef != null && Entry.compare(lastRef, entry) >= 0) {
			throwIllegalEntry(lastRef, entry);
		}
		lastRef = entry;

		long blockPos = refs.write(entry);
		indexRef(ref, blockPos);
	}

	private void throwIllegalEntry(Entry last, Entry now) {
		throw new IllegalArgumentException(MessageFormat.format(
				JGitText.get().reftableRecordsMustIncrease,
				new String(last.key, UTF_8), new String(now.key, UTF_8)));
	}

	private void indexRef(Ref ref, long blockPos) {
		if (indexObjects && !ref.isSymbolic()) {
			indexId(ref.getObjectId(), blockPos);
			indexId(ref.getPeeledObjectId(), blockPos);
		}
	}

	private void indexId(ObjectId id, long blockPos) {
		if (id != null) {
			RefList l = obj2ref.get(id);
			if (l == null) {
				l = new RefList(id);
				obj2ref.add(l);
			}
			l.addBlock(blockPos);
		}
	}

	/**
	 * Write one reflog entry to the reftable.
	 * <p>
	 * Reflog entries must be written in reference name and descending
	 * {@code updateIndex} (highest first) order.
	 *
	 * @param ref
	 *            name of the reference.
	 * @param updateIndex
	 *            identifier of the transaction that created the log record. The
	 *            {@code updateIndex} must be unique within the scope of
	 *            {@code ref}, and must be within the bounds defined by
	 *            {@code minUpdateIndex <= updateIndex <= maxUpdateIndex}.
	 * @param who
	 *            committer of the reflog entry.
	 * @param oldId
	 *            prior id; pass {@link org.eclipse.jgit.lib.ObjectId#zeroId()}
	 *            for creations.
	 * @param newId
	 *            new id; pass {@link org.eclipse.jgit.lib.ObjectId#zeroId()}
	 *            for deletions.
	 * @param message
	 *            optional message (may be null).
	 * @throws java.io.IOException
	 *             if reftable cannot be written.
	 */
	public void writeLog(String ref, long updateIndex, PersonIdent who,
			ObjectId oldId, ObjectId newId, @Nullable String message)
					throws IOException {
		String msg = message != null ? message : ""; //$NON-NLS-1$
		beginLog();
		LogEntry entry = new LogEntry(ref, updateIndex, who, oldId, newId, msg);
		if (lastLog != null && Entry.compare(lastLog, entry) >= 0) {
			throwIllegalEntry(lastLog, entry);
		}
		lastLog = entry;
		logs.write(entry);
	}

	/**
	 * Record deletion of one reflog entry in this reftable.
	 *
	 * <p>
	 * The deletion can shadow an entry stored in a lower table in the stack.
	 * This is useful for {@code refs/stash} and dropping an entry from its
	 * reflog.
	 * <p>
	 * Deletion must be properly interleaved in sorted updateIndex order with
	 * any other logs written by
	 * {@link #writeLog(String, long, PersonIdent, ObjectId, ObjectId, String)}.
	 *
	 * @param ref
	 *            the ref to delete (hide) a reflog entry from.
	 * @param updateIndex
	 *            the update index that must be hidden.
	 * @throws java.io.IOException
	 *             if reftable cannot be written.
	 */
	public void deleteLog(String ref, long updateIndex) throws IOException {
		beginLog();
		logs.write(new DeleteLogEntry(ref, updateIndex));
	}

	private void beginLog() throws IOException {
		if (logs == null) {
			finishRefAndObjSections(); // close prior ref blocks and their index, if present.
			out.flushFileHeader();
			out.setBlockSize(logBlockSize);
			logs = new Section(LOG_BLOCK_TYPE);
		}
	}

	/**
	 * Get an estimate of the current size in bytes of the reftable
	 *
	 * @return an estimate of the current size in bytes of the reftable, if it
	 *         was finished right now. Estimate is only accurate if
	 *         {@link org.eclipse.jgit.internal.storage.reftable.ReftableConfig#setIndexObjects(boolean)}
	 *         is {@code false} and
	 *         {@link org.eclipse.jgit.internal.storage.reftable.ReftableConfig#setMaxIndexLevels(int)}
	 *         is {@code 1}.
	 */
	public long estimateTotalBytes() {
		long bytes = out.size();
		if (bytes == 0) {
			bytes += FILE_HEADER_LEN;
		}
		if (cur != null) {
			long curBlockPos = out.size();
			int sz = cur.currentSize();
			bytes += sz;

			IndexBuilder idx = null;
			if (cur.blockType() == REF_BLOCK_TYPE) {
				idx = refs.idx;
			} else if (cur.blockType() == LOG_BLOCK_TYPE) {
				idx = logs.idx;
			}
			if (idx != null && shouldHaveIndex(idx)) {
				if (idx == refs.idx) {
					bytes += out.estimatePadBetweenBlocks(sz);
				}
				bytes += idx.estimateBytes(curBlockPos);
			}
		}
		bytes += FILE_FOOTER_LEN;
		return bytes;
	}

	/**
	 * Finish writing the reftable by writing its trailer.
	 *
	 * @return {@code this}
	 * @throws java.io.IOException
	 *             if reftable cannot be written.
	 */
	public ReftableWriter finish() throws IOException {
		finishRefAndObjSections();
		finishLogSection();
		writeFileFooter();
		out.finishFile();

		stats = new Stats(this, out);
		out = null;
		obj2ref = null;
		cur = null;
		refs = null;
		objs = null;
		logs = null;
		return this;
	}

	private void finishRefAndObjSections() throws IOException {
		if (cur != null && cur.blockType() == REF_BLOCK_TYPE) {
			refs.finishSectionMaybeWriteIndex();
			if (indexObjects && !obj2ref.isEmpty() && refs.idx.bytes > 0) {
				writeObjBlocks();
			}
			obj2ref = null;
		}
	}

	private void writeObjBlocks() throws IOException {
		List<RefList> sorted = sortById(obj2ref);
		obj2ref = null;
		objIdLen = shortestUniqueAbbreviation(sorted);

		out.padBetweenBlocksToNextBlock();
		objs = new Section(OBJ_BLOCK_TYPE);
		objs.entryCnt = sorted.size();
		for (RefList l : sorted) {
			objs.write(new ObjEntry(objIdLen, l, l.blockPos));
		}
		objs.finishSectionMaybeWriteIndex();
	}

	private void finishLogSection() throws IOException {
		if (cur != null && cur.blockType() == LOG_BLOCK_TYPE) {
			logs.finishSectionMaybeWriteIndex();
		}
	}

	private boolean shouldHaveIndex(IndexBuilder idx) {
		int threshold;
		if (idx == refs.idx && alignBlocks) {
			threshold = 4;
		} else {
			threshold = 1;
		}
		return idx.entries.size() + (cur != null ? 1 : 0) > threshold;
	}

	private void writeFileHeader() {
		byte[] hdr = new byte[FILE_HEADER_LEN];
		encodeHeader(hdr);
		out.write(hdr, 0, FILE_HEADER_LEN);
	}

	private void encodeHeader(byte[] hdr) {
		System.arraycopy(FILE_HEADER_MAGIC, 0, hdr, 0, 4);
		int bs = alignBlocks ? refBlockSize : 0;
		NB.encodeInt32(hdr, 4, (VERSION_1 << 24) | bs);
		NB.encodeInt64(hdr, 8, minUpdateIndex);
		NB.encodeInt64(hdr, 16, maxUpdateIndex);
	}

	private void writeFileFooter() {
		int ftrLen = FILE_FOOTER_LEN;
		byte[] ftr = new byte[ftrLen];
		encodeHeader(ftr);

		NB.encodeInt64(ftr, 24, indexPosition(refs));
		NB.encodeInt64(ftr, 32, (firstBlockPosition(objs) << 5) | objIdLen);
		NB.encodeInt64(ftr, 40, indexPosition(objs));
		NB.encodeInt64(ftr, 48, firstBlockPosition(logs));
		NB.encodeInt64(ftr, 56, indexPosition(logs));

		CRC32 crc = new CRC32();
		crc.update(ftr, 0, ftrLen - 4);
		NB.encodeInt32(ftr, ftrLen - 4, (int) crc.getValue());

		out.write(ftr, 0, ftrLen);
	}

	private static long firstBlockPosition(@Nullable Section s) {
		return s != null ? s.firstBlockPosition : 0;
	}

	private static long indexPosition(@Nullable Section s) {
		return s != null && s.idx != null ? s.idx.rootPosition : 0;
	}

	/**
	 * Get statistics of the last written reftable.
	 *
	 * @return statistics of the last written reftable.
	 */
	public Stats getStats() {
		return stats;
	}

	/** Statistics about a written reftable. */
	public static class Stats {
		private final int refBlockSize;
		private final int logBlockSize;
		private final int restartInterval;

		private final long minUpdateIndex;
		private final long maxUpdateIndex;

		private final long refCnt;
		private final long objCnt;
		private final int objIdLen;
		private final long logCnt;
		private final long refBytes;
		private final long objBytes;
		private final long logBytes;
		private final long paddingUsed;
		private final long totalBytes;

		private final int refIndexSize;
		private final int refIndexLevels;
		private final int objIndexSize;
		private final int objIndexLevels;

		Stats(ReftableWriter w, ReftableOutputStream o) {
			refBlockSize = w.refBlockSize;
			logBlockSize = w.logBlockSize;
			restartInterval = w.restartInterval;

			minUpdateIndex = w.minUpdateIndex;
			maxUpdateIndex = w.maxUpdateIndex;
			paddingUsed = o.paddingUsed();
			totalBytes = o.size();

			refCnt = w.refs.entryCnt;
			refBytes = w.refs.bytes;

			objCnt = w.objs != null ? w.objs.entryCnt : 0;
			objBytes = w.objs != null ? w.objs.bytes : 0;
			objIdLen = w.objIdLen;

			logCnt = w.logs != null ? w.logs.entryCnt : 0;
			logBytes = w.logs != null ? w.logs.bytes : 0;

			IndexBuilder refIdx = w.refs.idx;
			refIndexSize = refIdx.bytes;
			refIndexLevels = refIdx.levels;

			IndexBuilder objIdx = w.objs != null ? w.objs.idx : null;
			objIndexSize = objIdx != null ? objIdx.bytes : 0;
			objIndexLevels = objIdx != null ? objIdx.levels : 0;
		}

		/** @return number of bytes in a ref block. */
		public int refBlockSize() {
			return refBlockSize;
		}

		/** @return number of bytes to compress into a log block. */
		public int logBlockSize() {
			return logBlockSize;
		}

		/** @return number of references between binary search markers. */
		public int restartInterval() {
			return restartInterval;
		}

		/** @return smallest update index contained in this reftable. */
		public long minUpdateIndex() {
			return minUpdateIndex;
		}

		/** @return largest update index contained in this reftable. */
		public long maxUpdateIndex() {
			return maxUpdateIndex;
		}

		/** @return total number of references in the reftable. */
		public long refCount() {
			return refCnt;
		}

		/** @return number of unique objects in the reftable. */
		public long objCount() {
			return objCnt;
		}

		/** @return total number of log records in the reftable. */
		public long logCount() {
			return logCnt;
		}

		/** @return number of bytes for references, including ref index. */
		public long refBytes() {
			return refBytes;
		}

		/** @return number of bytes for objects, including object index. */
		public long objBytes() {
			return objBytes;
		}

		/** @return number of bytes for log, including log index. */
		public long logBytes() {
			return logBytes;
		}

		/** @return total number of bytes in the reftable. */
		public long totalBytes() {
			return totalBytes;
		}

		/** @return bytes of padding used to maintain block alignment. */
		public long paddingBytes() {
			return paddingUsed;
		}

		/** @return number of bytes in the ref index; 0 if no index was used. */
		public int refIndexSize() {
			return refIndexSize;
		}

		/** @return number of levels in the ref index. */
		public int refIndexLevels() {
			return refIndexLevels;
		}

		/** @return number of bytes in the object index; 0 if no index. */
		public int objIndexSize() {
			return objIndexSize;
		}

		/** @return number of levels in the object index. */
		public int objIndexLevels() {
			return objIndexLevels;
		}

		/**
		 * @return number of bytes required to uniquely identify all objects in
		 *         the reftable. Unique abbreviations in hex would be
		 *         {@code 2 * objIdLength()}.
		 */
		public int objIdLength() {
			return objIdLen;
		}
	}

	private static List<RefList> sortById(ObjectIdSubclassMap<RefList> m) {
		List<RefList> s = new ArrayList<>(m.size());
		for (RefList l : m) {
			s.add(l);
		}
		Collections.sort(s);
		return s;
	}

	private static int shortestUniqueAbbreviation(List<RefList> in) {
		// Estimate minimum number of bytes necessary for unique abbreviations.
		int bytes = Math.max(2, (int) (log(in.size()) / log(8)));
		Set<AbbreviatedObjectId> tmp = new HashSet<>((int) (in.size() * 0.75f));
		retry: for (;;) {
			int hexLen = bytes * 2;
			for (ObjectId id : in) {
				AbbreviatedObjectId a = id.abbreviate(hexLen);
				if (!tmp.add(a)) {
					if (++bytes >= OBJECT_ID_LENGTH) {
						return OBJECT_ID_LENGTH;
					}
					tmp.clear();
					continue retry;
				}
			}
			return bytes;
		}
	}

	private static class RefList extends ObjectIdOwnerMap.Entry {
		final LongList blockPos = new LongList(2);

		RefList(AnyObjectId id) {
			super(id);
		}

		void addBlock(long pos) {
			if (!blockPos.contains(pos)) {
				blockPos.add(pos);
			}
		}
	}

	private class Section {
		final IndexBuilder idx;
		final long firstBlockPosition;

		long entryCnt;
		long bytes;

		Section(byte keyType) {
			idx = new IndexBuilder(keyType);
			firstBlockPosition = out.size();
		}

		long write(BlockWriter.Entry entry) throws IOException {
			if (cur == null) {
				beginBlock(entry);
			} else if (!cur.tryAdd(entry)) {
				flushCurBlock();
				if (cur.padBetweenBlocks()) {
					out.padBetweenBlocksToNextBlock();
				}
				beginBlock(entry);
			}
			entryCnt++;
			return out.size();
		}

		private void beginBlock(BlockWriter.Entry entry)
				throws BlockSizeTooSmallException {
			byte blockType = entry.blockType();
			int bs = out.bytesAvailableInBlock();
			cur = new BlockWriter(blockType, idx.keyType, bs, restartInterval);
			cur.mustAdd(entry);
		}

		void flushCurBlock() throws IOException {
			idx.entries.add(new IndexEntry(cur.lastKey(), out.size()));
			cur.writeTo(out);
		}

		void finishSectionMaybeWriteIndex() throws IOException {
			flushCurBlock();
			cur = null;
			if (shouldHaveIndex(idx)) {
				idx.writeIndex();
			}
			bytes = out.size() - firstBlockPosition;
		}
	}

	private class IndexBuilder {
		final byte keyType;
		List<IndexEntry> entries = new ArrayList<>();
		long rootPosition;
		int bytes;
		int levels;

		IndexBuilder(byte kt) {
			keyType = kt;
		}

		int estimateBytes(long curBlockPos) {
			BlockWriter b = new BlockWriter(
					INDEX_BLOCK_TYPE, keyType,
					MAX_BLOCK_SIZE,
					Math.max(restartInterval, entries.size() / MAX_RESTARTS));
			try {
				for (Entry e : entries) {
					b.mustAdd(e);
				}
				if (cur != null) {
					b.mustAdd(new IndexEntry(cur.lastKey(), curBlockPos));
				}
			} catch (BlockSizeTooSmallException e) {
				return b.currentSize();
			}
			return b.currentSize();
		}

		void writeIndex() throws IOException {
			if (padBetweenBlocks(keyType)) {
				out.padBetweenBlocksToNextBlock();
			}
			long startPos = out.size();
			writeMultiLevelIndex(entries);
			bytes = (int) (out.size() - startPos);
			entries = null;
		}

		private void writeMultiLevelIndex(List<IndexEntry> keys)
				throws IOException {
			levels = 1;
			while (maxIndexLevels == 0 || levels < maxIndexLevels) {
				keys = writeOneLevel(keys);
				if (keys == null) {
					return;
				}
				levels++;
			}

			// When maxIndexLevels has restricted the writer, write one
			// index block with the entire remaining set of keys.
			BlockWriter b = new BlockWriter(
					INDEX_BLOCK_TYPE, keyType,
					MAX_BLOCK_SIZE,
					Math.max(restartInterval, keys.size() / MAX_RESTARTS));
			for (Entry e : keys) {
				b.mustAdd(e);
			}
			rootPosition = out.size();
			b.writeTo(out);
		}

		private List<IndexEntry> writeOneLevel(List<IndexEntry> keys)
				throws IOException {
			Section thisLevel = new Section(keyType);
			for (Entry e : keys) {
				thisLevel.write(e);
			}
			if (!thisLevel.idx.entries.isEmpty()) {
				thisLevel.flushCurBlock();
				if (cur.padBetweenBlocks()) {
					out.padBetweenBlocksToNextBlock();
				}
				cur = null;
				return thisLevel.idx.entries;
			}

			// The current block fit entire level; make it the root.
			rootPosition = out.size();
			cur.writeTo(out);
			cur = null;
			return null;
		}
	}
}

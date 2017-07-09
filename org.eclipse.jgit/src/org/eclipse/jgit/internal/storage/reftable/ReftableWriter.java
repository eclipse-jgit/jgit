/*
 * Copyright (C) 2017, Google Inc.
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

package org.eclipse.jgit.internal.storage.reftable;

import static java.lang.Math.log;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.FILE_FOOTER_LEN;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.FILE_HEADER_LEN;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.FILE_HEADER_MAGIC;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.INDEX_BLOCK_TYPE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.LOG_BLOCK_TYPE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.MAX_BLOCK_SIZE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.REF_BLOCK_TYPE;
import static org.eclipse.jgit.internal.storage.reftable.ReftableConstants.VERSION_1;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.reftable.BlockWriter.Entry;
import org.eclipse.jgit.internal.storage.reftable.BlockWriter.LogEntry;
import org.eclipse.jgit.internal.storage.reftable.BlockWriter.ObjEntry;
import org.eclipse.jgit.internal.storage.reftable.BlockWriter.RefEntry;
import org.eclipse.jgit.internal.storage.reftable.BlockWriter.TextEntry;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.lib.ObjectIdSubclassMap;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.util.IntList;
import org.eclipse.jgit.util.NB;

/**
 * Writes a reftable formatted file.
 * <p>
 * A reftable can be written in a streaming fashion, provided the caller sorts
 * all references. A {@link ReftableWriter} is single-use, and not thread-safe.
 */
public class ReftableWriter {
	private ReftableConfig config;
	private int refBlockSize;
	private int logBlockSize;
	private int restartInterval;
	private boolean indexObjects;

	private long minUpdateIndex;
	private long maxUpdateIndex;

	private ReftableOutputStream out;
	private ObjectIdSubclassMap<RefList> obj2ref;

	private BlockWriter refIndex;
	private BlockWriter objIndex;
	private BlockWriter logIndex;
	private BlockWriter cur;

	private long objOffset;
	private long logOffset;
	private long refIndexOffset;
	private long objIndexOffset;
	private long logIndexOffset;

	private long refCnt;
	private int objCnt;
	private long logCnt;
	private long refBytes;
	private long objBytes;
	private long logBytes;
	private int refBlocks;
	private int objBlocks;
	private int logBlocks;
	private int refIndexSize;
	private int objIndexSize;
	private int objIdLen;
	private Stats stats;

	/** Initialize a writer with a default configuration. */
	public ReftableWriter() {
		this(new ReftableConfig());
	}

	/**
	 * Initialize a writer with a specific configuration.
	 *
	 * @param cfg
	 *            configuration for the writer.
	 */
	public ReftableWriter(ReftableConfig cfg) {
		config = cfg;
	}

	/**
	 * @param cfg
	 *            configuration for the writer.
	 * @return {@code this}
	 */
	public ReftableWriter setConfig(ReftableConfig cfg) {
		this.config = cfg != null ? cfg : new ReftableConfig();
		return this;
	}

	/**
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
	 * Begin writing the reftable.
	 *
	 * @param os
	 *            stream to write the table to. Caller is responsible for
	 *            closing the stream after invoking {@link #finish()}.
	 * @return {@code this}
	 * @throws IOException
	 *             if reftable header cannot be written.
	 */
	public ReftableWriter begin(OutputStream os) throws IOException {
		refBlockSize = config.getRefBlockSize();
		logBlockSize = config.getLogBlockSize();
		restartInterval = config.getRestartInterval();
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

		refIndex = newIndex(refBlockSize);
		out = new ReftableOutputStream(os, refBlockSize);
		if (indexObjects) {
			obj2ref = new ObjectIdSubclassMap<>();
		}
		writeFileHeader();
		return this;
	}

	/**
	 * Sort a collection of references and write them to the reftable.
	 *
	 * @param refs
	 *            references to sort and write.
	 * @return {@code this}
	 * @throws IOException
	 *             reftable cannot be written.
	 */
	public ReftableWriter sortAndWriteRefs(Collection<Ref> refs)
			throws IOException {
		Iterator<RefEntry> itr = refs.stream()
				.map(RefEntry::new)
				.sorted(Entry::compare)
				.iterator();
		while (itr.hasNext()) {
			RefEntry entry = itr.next();
			int blockId = write(refIndex, entry);
			indexRef(entry.ref, blockId);
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
	 * @throws IOException
	 *             reftable cannot be written.
	 */
	public void writeRef(Ref ref) throws IOException {
		int blockId = write(refIndex, new RefEntry(ref));
		indexRef(ref, blockId);
	}

	void writeText(String name, String content) throws IOException {
		write(refIndex, new TextEntry(name, content));
	}

	private void indexRef(Ref ref, int blockId) {
		if (indexObjects && !ref.isSymbolic()) {
			indexId(ref.getObjectId(), blockId);
			indexId(ref.getPeeledObjectId(), blockId);
		}
		refCnt++;
	}

	private void indexId(ObjectId id, int blockId) {
		if (id != null) {
			RefList l = obj2ref.get(id);
			if (l == null) {
				l = new RefList(id);
				obj2ref.add(l);
			}
			l.addBlock(blockId);
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
	 *            prior id; pass {@link ObjectId#zeroId()} for creations.
	 * @param newId
	 *            new id; pass {@link ObjectId#zeroId()} for deletions.
	 * @param message
	 *            optional message (may be null).
	 * @throws IOException
	 *             reftable cannot be written.
	 */
	public void writeLog(String ref, long updateIndex, PersonIdent who,
			ObjectId oldId, ObjectId newId, @Nullable String message)
					throws IOException {
		String msg = message != null ? message : ""; //$NON-NLS-1$
		beginLog();
		logCnt++;
		write(logIndex, new LogEntry(ref, updateIndex, who, oldId, newId, msg));
	}

	private void beginLog() throws IOException {
		if (logOffset == 0) {
			finishRef(); // close prior ref blocks and their index, if present.
			out.flushFileHeader();

			if (logBlockSize == 0) {
				logBlockSize = refBlockSize * 2;
			}
			logIndex = newIndex(logBlockSize);
			out.setBlockSize(logBlockSize);
			logOffset = out.size();
		}
	}

	private int write(BlockWriter idx, BlockWriter.Entry entry)
			throws IOException {
		if (cur == null) {
			beginBlock(entry);
		} else if (!cur.tryAdd(entry)) {
			idx.addIndex(cur.lastKey(), out.size());
			cur.writeTo(out);
			if (cur.padBetweenBlocks()) {
				out.padBetweenBlocksToNextBlock();
			}
			beginBlock(entry);
		}
		return out.blockCount();
	}

	private void beginBlock(BlockWriter.Entry entry)
			throws BlockSizeTooSmallException {
		byte type = entry.blockType();
		int bs = out.bytesAvailableInBlock();
		cur = new BlockWriter(type, bs, restartInterval);
		cur.addFirst(entry);
	}

	private BlockWriter newIndex(int bs) {
		return new BlockWriter(INDEX_BLOCK_TYPE, bs, restartInterval);
	}

	/**
	 * @return an estimate of the current size in bytes of the reftable, if it
	 *         was finished right now. The estimate is only accurate if the
	 *         configuration has {@link ReftableConfig#setIndexObjects(boolean)}
	 *         to {@code false}.
	 */
	public long estimateTotalBytes() {
		long bytes = out.size();
		if (bytes == 0) {
			bytes += FILE_HEADER_LEN;
		}
		if (cur != null) {
			long offset = out.size();
			int sz = cur.currentSize();
			bytes += sz;

			BlockWriter idx = null;
			if (cur.blockType() == REF_BLOCK_TYPE) {
				idx = refIndex;
			} else if (cur.blockType() == LOG_BLOCK_TYPE) {
				idx = logIndex;
			}
			if (idx != null && shouldHaveIndex(idx)) {
				if (idx == refIndex) {
					bytes += out.estimatePadBetweenBlocks(sz);
				}
				bytes += idx.estimateIndexSizeIfAdding(cur.lastKey(), offset);
			}
		}
		bytes += FILE_FOOTER_LEN;
		return bytes;
	}

	/**
	 * Finish writing the reftable by writing its trailer.
	 *
	 * @return {@code this}
	 * @throws IOException
	 *             reftable cannot be written.
	 */
	public ReftableWriter finish() throws IOException {
		finishRef();
		finishLog();
		writeFileFooter();

		stats = new Stats(this, out, refIndex);
		refIndex = null;
		logIndex = null;
		cur = null;
		out = null;
		obj2ref = null;
		return this;
	}

	private void finishRef() throws IOException {
		if (cur != null && cur.blockType() == REF_BLOCK_TYPE) {
			refBlocks = out.blockCount() + 1;
			refIndexOffset = finishBlockMaybeWriteIndex(refIndex);
			if (refIndexOffset > 0) {
				refIndexSize = (int) (out.size() - refIndexOffset);
			}
			refBytes = out.size();

			if (indexObjects && !obj2ref.isEmpty() && refIndexOffset > 0) {
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
		objCnt = sorted.size();
		objOffset = out.size();
		objIndex = newIndex(refBlockSize);
		for (RefList l : sorted) {
			write(objIndex, new ObjEntry(objIdLen, l, l.blockIds));
		}
		objBlocks = (out.blockCount() + 1) - refBlocks;
		objIndexOffset = finishBlockMaybeWriteIndex(objIndex);
		if (objIndexOffset > 0) {
			objIndexSize = (int) (out.size() - objIndexOffset);
		}
		objBytes = out.size() - objOffset;
	}

	private void finishLog() throws IOException {
		if (cur != null && cur.blockType() == LOG_BLOCK_TYPE) {
			logBlocks = (out.blockCount() + 1) - (refBlocks + objBlocks);
			logIndexOffset = finishBlockMaybeWriteIndex(logIndex);
			logBytes = out.size() - logOffset;
		}
	}

	private long finishBlockMaybeWriteIndex(BlockWriter idx)
			throws IOException {
		idx.addIndex(cur.lastKey(), out.size());
		cur.writeTo(out);
		cur = null;

		if (shouldHaveIndex(idx)) {
			if (idx == refIndex || idx == objIndex) {
				out.padBetweenBlocksToNextBlock();
			}
			long offset = out.size();
			idx.writeTo(out);
			return offset;
		} else {
			return 0;
		}
	}

	private boolean shouldHaveIndex(BlockWriter idx) {
		int threshold = idx == refIndex ? 4 : 1;
		return idx.entryCount() + (cur != null ? 1 : 0) > threshold;
	}

	private void writeFileHeader() throws IOException {
		byte[] hdr = new byte[FILE_HEADER_LEN];
		encodeHeader(hdr);
		out.write(hdr);
	}

	private void encodeHeader(byte[] hdr) {
		System.arraycopy(FILE_HEADER_MAGIC, 0, hdr, 0, 4);
		NB.encodeInt32(hdr, 4, (VERSION_1 << 24) | refBlockSize);
		NB.encodeInt64(hdr, 8, minUpdateIndex);
		NB.encodeInt64(hdr, 16, maxUpdateIndex);
	}

	private void writeFileFooter() throws IOException {
		int ftrLen = FILE_FOOTER_LEN;
		byte[] ftr = new byte[ftrLen];
		encodeHeader(ftr);

		NB.encodeInt64(ftr, 24, refIndexOffset);
		NB.encodeInt64(ftr, 32, objOffset);
		NB.encodeInt64(ftr, 40, objIndexOffset);
		NB.encodeInt64(ftr, 48, logOffset);
		NB.encodeInt64(ftr, 56, logIndexOffset);

		CRC32 crc = new CRC32();
		crc.update(ftr, 0, ftrLen - 4);
		NB.encodeInt32(ftr, ftrLen - 4, (int) crc.getValue());

		out.write(ftr);
		out.finishFile();
	}

	/** @return statistics of the last written reftable. */
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
		private final int objCnt;
		private final int objIdLen;
		private final long logCnt;
		private final long refBytes;
		private final long objBytes;
		private final long logBytes;
		private final long paddingUsed;
		private final long totalBytes;
		private final int refBlocks;
		private final int objBlocks;
		private final int logBlocks;

		private final int refIndexKeys;
		private final int refIndexSize;
		private final int objIndexSize;

		Stats(ReftableWriter w, ReftableOutputStream o, BlockWriter refIdx) {
			refBlockSize = w.refBlockSize;
			logBlockSize = w.logBlockSize;
			restartInterval = w.restartInterval;

			minUpdateIndex = w.minUpdateIndex;
			maxUpdateIndex = w.maxUpdateIndex;

			refCnt = w.refCnt;
			objCnt = w.objCnt;
			objIdLen = w.objIdLen;
			logCnt = w.logCnt;
			refBytes = w.refBytes;
			objBytes = w.objBytes;
			logBytes = w.logBytes;
			paddingUsed = o.paddingUsed();
			totalBytes = o.size();
			refBlocks = w.refBlocks;
			objBlocks = w.objBlocks;
			logBlocks = w.logBlocks;

			refIndexKeys = w.refIndexOffset > 0 ? refIdx.entryCount() : 0;
			refIndexSize = w.refIndexSize;
			objIndexSize = w.objIndexSize;
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

		/** @return number of ref blocks in the output, excluding index. */
		public int refBlockCount() {
			return refBlocks;
		}

		/** @return number of object blocks in the output, excluding index. */
		public int objBlockCount() {
			return objBlocks;
		}

		/** @return number of log blocks in the output, excluding index. */
		public int logBlockCount() {
			return logBlocks;
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

		/** @return number of keys in the ref index; 0 if no index was used. */
		public int refIndexKeys() {
			return refIndexKeys;
		}

		/** @return number of bytes in the ref index; 0 if no index was used. */
		public int refIndexSize() {
			return refIndexSize;
		}

		/** @return number of bytes in the object index; 0 if no index. */
		public int objIndexSize() {
			return objIndexSize;
		}

		/**
		 * @return number of bytes required to uniquely identify all objects in
		 *         the reftable. Unique abbreviations in hex would be
		 *         {@code 2 * objIdLength()}.
		 */
		public int objIdLength() {
			return objIdLen;
		}

		/** @return estimated number of disk seeks per ref read. */
		public double diskSeeksPerRead() {
			if (refIndexKeys() > 0) {
				return 1;
			}
			return log(refBlockCount()) / log(2);
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
		Set<AbbreviatedObjectId> tmp = new HashSet<>((int) (in.size() * 0.75f));
		int bytes = 2;
		retry: for (;;) {
			int hexLen = bytes * 2;
			for (ObjectId id : in) {
				AbbreviatedObjectId a = id.abbreviate(hexLen);
				if (!tmp.add(a)) {
					if (++bytes >= Constants.OBJECT_ID_LENGTH) {
						return Constants.OBJECT_ID_LENGTH;
					}
					tmp.clear();
					continue retry;
				}
			}
			return bytes;
		}
	}

	private static class RefList extends ObjectIdOwnerMap.Entry {
		final IntList blockIds = new IntList(2);

		RefList(AnyObjectId id) {
			super(id);
		}

		void addBlock(int id) {
			if (!blockIds.contains(id)) {
				blockIds.add(id);
			}
		}
	}
}

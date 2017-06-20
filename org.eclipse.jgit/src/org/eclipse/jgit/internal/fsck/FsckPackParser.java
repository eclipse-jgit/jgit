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

package org.eclipse.jgit.internal.fsck;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.CorruptPackIndexException;
import org.eclipse.jgit.errors.CorruptPackIndexException.ErrorType;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.fsck.FsckError.CorruptObject;
import org.eclipse.jgit.internal.storage.dfs.ReadableChannel;
import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.file.PackIndex.MutableEntry;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.transport.PackedObjectInfo;

/** A read-only pack parser for object validity checking. */
public class FsckPackParser extends PackParser {
	private final CRC32 crc;

	private final ReadableChannel channel;

	private final Set<CorruptObject> corruptObjects = new HashSet<>();

	private long expectedObjectCount = -1L;

	private long offset;

	private int blockSize;

	/**
	 * @param db
	 *            the object database which stores repository's data.
	 * @param channel
	 *            readable channel of the pack file.
	 */
	public FsckPackParser(ObjectDatabase db, ReadableChannel channel) {
		super(db, Channels.newInputStream(channel));
		this.channel = channel;
		setCheckObjectCollisions(false);
		this.crc = new CRC32();
		this.blockSize = channel.blockSize() > 0 ? channel.blockSize() : 65536;
	}

	@Override
	protected void onPackHeader(long objCnt) throws IOException {
		if (expectedObjectCount >= 0) {
			// Some DFS pack files don't contain the correct object count, e.g.
			// INSERT/RECEIVE packs don't always contain the correct object
			// count in their headers. Overwrite the expected object count
			// after parsing the pack header.
			setExpectedObjectCount(expectedObjectCount);
		}
	}

	@Override
	protected void onBeginWholeObject(long streamPosition, int type,
			long inflatedSize) throws IOException {
		crc.reset();
	}

	@Override
	protected void onObjectHeader(Source src, byte[] raw, int pos, int len)
			throws IOException {
		crc.update(raw, pos, len);
	}

	@Override
	protected void onObjectData(Source src, byte[] raw, int pos, int len)
			throws IOException {
		crc.update(raw, pos, len);
	}

	@Override
	protected void onEndWholeObject(PackedObjectInfo info) throws IOException {
		info.setCRC((int) crc.getValue());
	}

	@Override
	protected void onBeginOfsDelta(long deltaStreamPosition,
			long baseStreamPosition, long inflatedSize) throws IOException {
		crc.reset();
	}

	@Override
	protected void onBeginRefDelta(long deltaStreamPosition, AnyObjectId baseId,
			long inflatedSize) throws IOException {
		crc.reset();
	}

	@Override
	protected UnresolvedDelta onEndDelta() throws IOException {
		UnresolvedDelta delta = new UnresolvedDelta();
		delta.setCRC((int) crc.getValue());
		return delta;
	}

	@Override
	protected void onInflatedObjectData(PackedObjectInfo obj, int typeCode,
			byte[] data) throws IOException {
		// FsckPackParser ignores this event.
	}

	@Override
	protected void verifySafeObject(final AnyObjectId id, final int type,
			final byte[] data) {
		try {
			super.verifySafeObject(id, type, data);
		} catch (CorruptObjectException e) {
			// catch the exception and continue parse the pack file
			CorruptObject o = new CorruptObject(id.toObjectId(), type);
			if (e.getErrorType() != null) {
				o.setErrorType(e.getErrorType());
			}
			corruptObjects.add(o);
		}
	}

	@Override
	protected void onPackFooter(byte[] hash) throws IOException {
	}

	@Override
	protected boolean onAppendBase(int typeCode, byte[] data,
			PackedObjectInfo info) throws IOException {
		// Do nothing.
		return false;
	}

	@Override
	protected void onEndThinPack() throws IOException {
	}

	@Override
	protected ObjectTypeAndSize seekDatabase(PackedObjectInfo obj,
			ObjectTypeAndSize info) throws IOException {
		crc.reset();
		offset = obj.getOffset();
		return readObjectHeader(info);
	}

	@Override
	protected ObjectTypeAndSize seekDatabase(UnresolvedDelta delta,
			ObjectTypeAndSize info) throws IOException {
		crc.reset();
		offset = delta.getOffset();
		return readObjectHeader(info);
	}

	@Override
	protected int readDatabase(byte[] dst, int pos, int cnt)
			throws IOException {
		// read from input instead of database.
		int n = read(offset, dst, pos, cnt);
		if (n > 0) {
			offset += n;
		}
		return n;
	}

	int read(long channelPosition, byte[] dst, int pos, int cnt)
			throws IOException {
		long block = channelPosition / blockSize;
		byte[] bytes = readFromChannel(block);
		if (bytes == null) {
			return -1;
		}
		int offset = (int) (channelPosition - block * blockSize);
		int bytesToCopy = Math.min(cnt, bytes.length - offset);
		if (bytesToCopy < 1) {
			return -1;
		}
		System.arraycopy(bytes, offset, dst, pos, bytesToCopy);
		return bytesToCopy;
	}

	private byte[] readFromChannel(long block) throws IOException {
		channel.position(block * blockSize);
		ByteBuffer buf = ByteBuffer.allocate(blockSize);
		int totalBytesRead = 0;
		while (totalBytesRead < blockSize) {
			int bytesRead = channel.read(buf);
			if (bytesRead == -1) {
				if (totalBytesRead == 0) {
					return null;
				}
				return Arrays.copyOf(buf.array(), totalBytesRead);
			}
			totalBytesRead += bytesRead;
		}
		return buf.array();
	}

	@Override
	protected boolean checkCRC(int oldCRC) {
		return oldCRC == (int) crc.getValue();
	}

	@Override
	protected void onStoreStream(byte[] raw, int pos, int len)
			throws IOException {
	}

	/**
	 * @return corrupt objects that reported by {@link ObjectChecker}.
	 */
	public Set<CorruptObject> getCorruptObjects() {
		return corruptObjects;
	}

	/**
	 * Verify the existing index file with all objects from the pack.
	 *
	 * @param entries
	 *            all the entries that are expected in the index file
	 * @param idx
	 *            index file associate with the pack
	 * @throws CorruptPackIndexException
	 *             when the index file is corrupt.
	 */
	public void verifyIndex(List<PackedObjectInfo> entries, PackIndex idx)
			throws CorruptPackIndexException {
		Set<String> all = new HashSet<>();
		for (PackedObjectInfo entry : entries) {
			all.add(entry.getName());
			long offset = idx.findOffset(entry);
			if (offset == -1) {
				throw new CorruptPackIndexException(
						MessageFormat.format(JGitText.get().missingObject,
								entry.getType(), entry.getName()),
						ErrorType.MISSING_OBJ);
			} else if (offset != entry.getOffset()) {
				throw new CorruptPackIndexException(MessageFormat
						.format(JGitText.get().mismatchOffset, entry.getName()),
						ErrorType.MISMATCH_OFFSET);
			}

			try {
				if (idx.hasCRC32Support()
						&& (int) idx.findCRC32(entry) != entry.getCRC()) {
					throw new CorruptPackIndexException(
							MessageFormat.format(JGitText.get().mismatchCRC,
									entry.getName()),
							ErrorType.MISMATCH_CRC);
				}
			} catch (MissingObjectException e) {
				throw new CorruptPackIndexException(MessageFormat
						.format(JGitText.get().missingCRC, entry.getName()),
						ErrorType.MISSING_CRC);
			}
		}

		for (MutableEntry entry : idx) {
			if (!all.contains(entry.name())) {
				throw new CorruptPackIndexException(MessageFormat.format(
						JGitText.get().unknownObjectInIndex, entry.name()),
						ErrorType.UNKNOWN_OBJ);
			}
		}
	}

	/**
	 * Set the object count for overwriting the expected object count from pack
	 * header.
	 *
	 * @param expectedObjectCount
	 *            the actual expected object count.
	 */
	public void overwriteObjectCount(long expectedObjectCount) {
		this.expectedObjectCount = expectedObjectCount;
	}
}

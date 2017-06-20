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

package org.eclipse.jgit.internal.storage.pack;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.file.PackIndex.MutableEntry;
import org.eclipse.jgit.internal.storage.file.PackLock;
import org.eclipse.jgit.internal.storage.pack.CorruptPackIndexException.ErrorType;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.transport.PackedObjectInfo;

/**
 * A read only pack parser for fsck purpose.
 */
public class FsckPackParser extends PackParser {
	private final ObjectChecker objCheck;

	private final CRC32 crc;

	private final InputStream in;

	private final Set<CorruptObject> corruptObjects = new HashSet<>();

	private boolean overwriteObjectCount;

	private long expectedObjectCount;

	public FsckPackParser(Repository git, InputStream in,
			ObjectChecker objCheck) {
		super(git.getObjectDatabase(), in);
		this.in = in;
		setCheckObjectCollisions(false);
		this.objCheck = objCheck;
		setObjectChecker(objCheck);
		this.crc = new CRC32();
	}

	public FsckPackParser(Repository git, InputStream in,
			ObjectChecker objCheck, long expectedObjectCount) {
		super(git.getObjectDatabase(), in);
		this.in = in;
		setCheckObjectCollisions(false);
		this.objCheck = objCheck;
		setObjectChecker(objCheck);
		this.crc = new CRC32();
		this.overwriteObjectCount = true;
		this.expectedObjectCount = expectedObjectCount;
	}

	@Override
	public PackLock parse(ProgressMonitor receiving, ProgressMonitor resolving)
			throws IOException {
		PackLock packLock = super.parse(receiving, resolving);
		return packLock;
	}

	@Override
	protected void onPackHeader(long objCnt) throws IOException {
		if (overwriteObjectCount) {
			// Most DFS pack file doesn't contain the correct object count, e.g.
			// INSERT/RECEIVE packs. Need overwrite the expected object count
			// after parsing pack header.
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
		// ignore.
	}

	@Override
	protected void verifySafeObject(final AnyObjectId id, final int type,
			final byte[] data) {
		if (objCheck != null) {
			try {
				objCheck.check(id, type, data);
			} catch (CorruptObjectException e) {
				// catch the exception and continue parse the pack file
				CorruptObject o = new CorruptObject(id, type);
				if (e.getErrorType() != null) {
					o.setErrorType(e.getErrorType());
				}
				corruptObjects.add(o);
			}
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
		return readObjectHeader(info);
	}

	@Override
	protected ObjectTypeAndSize seekDatabase(UnresolvedDelta delta,
			ObjectTypeAndSize info) throws IOException {
		crc.reset();
		return readObjectHeader(info);
	}

	@Override
	protected int readDatabase(byte[] dst, int pos, int cnt)
			throws IOException {
		// since this pack parse doesn't write into anywhere, and read from
		// input instead.
		return in.read(dst, pos, cnt);
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
	 * @return corrupted objects that reported by {@link ObjectChecker}.
	 * @since 4.9
	 */
	public Set<CorruptObject> getCorruptObjects() {
		return corruptObjects;
	}

	/**
	 * Verify the existing index file with all objects from the pack.
	 *
	 * @param entries all the entries that expect in the index file
	 * @param idx index file associate with the pack
	 * @throws CorruptPackIndexException
	 *             when the index file is corrupted.
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
				throw new CorruptPackIndexException(
						MessageFormat
								.format(JGitText.get().mismatchOffset,
										entry.getName()),
						ErrorType.MISMATCH_OFFSET);
			}

			try {
				if (idx.hasCRC32Support() && (int) idx.findCRC32(entry) != entry
						.getCRC()) {
					throw new CorruptPackIndexException(
							MessageFormat
									.format(JGitText.get().mismatchCRC,
											entry.getName()),
							ErrorType.MISMATCH_CRC);
				}
			} catch (MissingObjectException e) {
				throw new CorruptPackIndexException(
						MessageFormat.format(JGitText.get().missingCRC,
								entry.getName()),
						ErrorType.MISSING_CRC);
			}
		}

		for (MutableEntry entry : idx) {
			if (!all.contains(entry.name())) {
				throw new CorruptPackIndexException(
						MessageFormat
								.format(JGitText.get().unknownObjectInIndex,
										entry.name()),
						ErrorType.UNKNOWN_OBJ);
			}
		}
	}

	public static class CorruptObject {
		final AnyObjectId id;

		final int type;

		ObjectChecker.ErrorType errorType;

		public CorruptObject(AnyObjectId id, int type) {
			this.id = id;
			this.type = type;
		}

		void setErrorType(ObjectChecker.ErrorType errorType) {
			this.errorType = errorType;
		}
	}

	public static class CorruptIndex {
		String packName;
		CorruptPackIndexException.ErrorType errorType;

		public CorruptIndex(String packName,
				ErrorType errorType) {
			this.packName = packName;
			this.errorType = errorType;
		}
	}
}


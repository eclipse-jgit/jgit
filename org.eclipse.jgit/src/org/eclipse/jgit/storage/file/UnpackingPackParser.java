/*
 * Copyright (C) 2011, Google Inc.
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

package org.eclipse.jgit.storage.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.util.zip.DeflaterOutputStream;

import org.eclipse.jgit.errors.ObjectWritingException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.TemporaryBuffer;

/** Unpacks a pack stream into loose objects. */
class UnpackingPackParser extends PackParser {
	private final FileObjectDatabase db;

	private final ObjectDirectoryInserter inserter;

	private final boolean fsync;

	private File objTmp;

	private FileOutputStream objOut;

	private DeflaterOutputStream objDeflate;

	private boolean inDelta;

	private TemporaryBuffer.LocalFile deltaBuf;

	private long deltaBufSize;

	private long deltaOffset;

	private InputStream deltaIn;

	UnpackingPackParser(FileObjectDatabase db,
			ObjectDirectoryInserter inserter, InputStream src) {
		super(db, src);
		setCheckObjectCollisions(false);

		this.db = db;
		this.inserter = inserter;
		this.fsync = inserter.getFSyncObjectFiles();
	}

	@Override
	public PackLock parse(ProgressMonitor progress) throws IOException {
		try {
			return super.parse(progress);

		} finally {
			if (deltaIn != null)
				deltaIn.close();

			if (deltaBuf != null) {
				deltaBuf.close();
				deltaBuf.destroy();
			}

			if (objOut != null)
				objOut.close();

			if (objTmp != null)
				objTmp.delete();
		}
	}

	@Override
	protected void onBeginWholeObject(long streamPosition, int type,
			long inflatedSize) throws IOException {
		beginObject(type, inflatedSize);
	}

	@Override
	protected void onEndWholeObject(PackedObjectInfo info) throws IOException {
		endObject(info);
	}

	private void beginObject(int type, long inflatedSize) throws IOException,
			FileNotFoundException {
		objTmp = inserter.newTempFile();
		objOut = new FileOutputStream(objTmp);
		OutputStream out = objOut;
		if (fsync)
			out = Channels.newOutputStream(objOut.getChannel());
		objDeflate = inserter.compress(out);
		inserter.writeHeader(objDeflate, type, inflatedSize);
	}

	private void endObject(AnyObjectId objectId) throws IOException,
			ObjectWritingException {
		objDeflate.finish();
		objDeflate.flush();
		if (fsync)
			objOut.getChannel().force(true);
		objOut.close();

		objDeflate = null;
		objOut = null;

		ObjectId id = objectId.copy();
		switch (db.insertUnpackedObject(objTmp, id, false /* no duplicate */)) {
		case INSERTED:
		case EXISTS_PACKED:
		case EXISTS_LOOSE:
			objTmp = null;
			break;

		case FAILURE:
		default:
			objTmp = null;
			throw new ObjectWritingException("Unable to create new object: "
					+ db.fileFor(id));
		}
	}

	@Override
	protected void onBeginRefDelta(long deltaStreamPosition,
			AnyObjectId baseId, long inflatedSize) throws IOException {
		onBeginDelta();
	}

	@Override
	protected void onBeginOfsDelta(long deltaStreamPosition,
			long baseStreamPosition, long inflatedSize) throws IOException {
		onBeginDelta();
	}

	private void onBeginDelta() {
		if (deltaBuf == null) {
			File dir = db.getDirectory();
			int limit = 1024 * 1024;
			deltaBuf = new TemporaryBuffer.LocalFile(dir, limit);
		}
		deltaOffset = deltaBufSize;
		inDelta = true;
	}

	@Override
	protected void onInflatedData(byte[] raw, int pos, int len)
			throws IOException {
		if (objDeflate != null)
			objDeflate.write(raw, pos, len);
	}

	@Override
	protected void onObjectHeader(Source src, byte[] raw, int pos, int len)
			throws IOException {
		if (inDelta && src == Source.INPUT) {
			deltaBuf.write(raw, pos, len);
			deltaBufSize += len;
		}
	}

	@Override
	protected void onObjectData(Source src, byte[] raw, int pos, int len)
			throws IOException {
		if (inDelta && src == Source.INPUT) {
			deltaBuf.write(raw, pos, len);
			deltaBufSize += len;
		}
	}

	@Override
	protected UnresolvedDelta onEndDelta() throws IOException {
		inDelta = false;
		return new DeferredDelta(deltaOffset);
	}

	@Override
	protected void onResolvedDelta(AnyObjectId id, int type, byte[] data)
			throws IOException {
		beginObject(type, data.length);
		objDeflate.write(data);
		endObject(id);
	}

	@Override
	protected void onPackFooter(byte[] hash) throws IOException {
		if (deltaBuf != null)
			deltaBuf.close();
	}

	private static class DeferredDelta extends UnresolvedDelta {
		final long bufferOffset;

		DeferredDelta(long bufferOffset) {
			this.bufferOffset = bufferOffset;
		}
	}

	@Override
	protected ObjectTypeAndSize seekDatabase(UnresolvedDelta delta,
			ObjectTypeAndSize info) throws IOException {
		DeferredDelta d = (DeferredDelta) delta;
		if (deltaIn instanceof FileInputStream) {
			((FileInputStream) deltaIn).getChannel().position(d.bufferOffset);
		} else {
			if (deltaIn != null)
				deltaIn.close();
			deltaIn = deltaBuf.openInputStream();
			IO.skipFully(deltaIn, d.bufferOffset);
		}
		return readObjectHeader(info);
	}

	@Override
	protected int readDatabase(byte[] dst, int pos, int cnt) throws IOException {
		return deltaIn.read(dst, pos, cnt);
	}

	@Override
	protected boolean checkCRC(int oldCRC) {
		return true; // Assume the CRC is OK.
	}

	@Override
	protected ObjectTypeAndSize seekDatabase(PackedObjectInfo obj,
			ObjectTypeAndSize info) throws IOException {
		// Return null to request opening the object from the
		// parser's reader.
		return null;
	}

	@Override
	protected void onStoreStream(byte[] raw, int pos, int len)
			throws IOException {
		// Do nothing, this implementation does not archive the pack.
	}

	@Override
	protected boolean onAppendBase(int typeCode, byte[] data,
			PackedObjectInfo info) throws IOException {
		return false; // Do not store the base.
	}

	@Override
	protected void onEndThinPack() throws IOException {
		// Do nothing.
	}
}

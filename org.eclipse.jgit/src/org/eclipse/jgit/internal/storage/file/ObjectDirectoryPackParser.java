/*
 * Copyright (C) 2008-2011, Google Inc.
 * Copyright (C) 2007-2008, Robin Rosenberg <robin.rosenberg@dewire.com>
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

package org.eclipse.jgit.internal.storage.file;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import org.eclipse.jgit.errors.LockFailedException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.NB;

/**
 * Consumes a pack stream and stores as a pack file in
 * {@link org.eclipse.jgit.internal.storage.file.ObjectDirectory}.
 * <p>
 * To obtain an instance of a parser, applications should use
 * {@link org.eclipse.jgit.lib.ObjectInserter#newPackParser(InputStream)}.
 */
public class ObjectDirectoryPackParser extends PackParser {
	private final FileObjectDatabase db;

	/** CRC-32 computation for objects that are appended onto the pack. */
	private final CRC32 crc;

	/** Running SHA-1 of any base objects appended after {@link #origEnd}. */
	private final MessageDigest tailDigest;

	/** Preferred format version of the pack-*.idx file to generate. */
	private int indexVersion;

	/** If true, pack with 0 objects will be stored. Usually these are deleted. */
	private boolean keepEmpty;

	/** Path of the temporary file holding the pack data. */
	private File tmpPack;

	/**
	 * Path of the index created for the pack, to find objects quickly at read
	 * time.
	 */
	private File tmpIdx;

	/** Read/write handle to {@link #tmpPack} while it is being parsed. */
	private RandomAccessFile out;

	/** Length of the original pack stream, before missing bases were appended. */
	private long origEnd;

	/** The original checksum of data up to {@link #origEnd}. */
	private byte[] origHash;

	/** Current end of the pack file. */
	private long packEnd;

	/** Checksum of the entire pack file. */
	private byte[] packHash;

	/** Compresses delta bases when completing a thin pack. */
	private Deflater def;

	/** The pack that was created, if parsing was successful. */
	private PackFile newPack;

	private PackConfig pconfig;

	ObjectDirectoryPackParser(FileObjectDatabase odb, InputStream src) {
		super(odb, src);
		this.db = odb;
		this.pconfig = new PackConfig(odb.getConfig());
		this.crc = new CRC32();
		this.tailDigest = Constants.newMessageDigest();

		indexVersion = db.getConfig().get(CoreConfig.KEY).getPackIndexVersion();
	}

	/**
	 * Set the pack index file format version this instance will create.
	 *
	 * @param version
	 *            the version to write. The special version 0 designates the
	 *            oldest (most compatible) format available for the objects.
	 * @see PackIndexWriter
	 */
	public void setIndexVersion(int version) {
		indexVersion = version;
	}

	/**
	 * Configure this index pack instance to keep an empty pack.
	 * <p>
	 * By default an empty pack (a pack with no objects) is not kept, as doi so
	 * is completely pointless. With no objects in the pack there is no d stored
	 * by it, so the pack is unnecessary.
	 *
	 * @param empty
	 *            true to enable keeping an empty pack.
	 */
	public void setKeepEmpty(boolean empty) {
		keepEmpty = empty;
	}

	/**
	 * Get the imported {@link org.eclipse.jgit.internal.storage.file.PackFile}.
	 * <p>
	 * This method is supplied only to support testing; applications shouldn't
	 * be using it directly to access the imported data.
	 *
	 * @return the imported PackFile, if parsing was successful.
	 */
	public PackFile getPackFile() {
		return newPack;
	}

	/** {@inheritDoc} */
	@Override
	public long getPackSize() {
		if (newPack == null)
			return super.getPackSize();

		File pack = newPack.getPackFile();
		long size = pack.length();
		String p = pack.getAbsolutePath();
		String i = p.substring(0, p.length() - ".pack".length()) + ".idx"; //$NON-NLS-1$ //$NON-NLS-2$
		File idx = new File(i);
		if (idx.exists() && idx.isFile())
			size += idx.length();
		return size;
	}

	/** {@inheritDoc} */
	@Override
	public PackLock parse(ProgressMonitor receiving, ProgressMonitor resolving)
			throws IOException {
		tmpPack = File.createTempFile("incoming_", ".pack", db.getDirectory()); //$NON-NLS-1$ //$NON-NLS-2$
		tmpIdx = new File(db.getDirectory(), baseName(tmpPack) + ".idx"); //$NON-NLS-1$
		try {
			out = new RandomAccessFile(tmpPack, "rw"); //$NON-NLS-1$

			super.parse(receiving, resolving);

			out.seek(packEnd);
			out.write(packHash);
			out.getChannel().force(true);
			out.close();

			writeIdx();

			tmpPack.setReadOnly();
			tmpIdx.setReadOnly();

			return renameAndOpenPack(getLockMessage());
		} finally {
			if (def != null)
				def.end();
			try {
				if (out != null && out.getChannel().isOpen())
					out.close();
			} catch (IOException closeError) {
				// Ignored. We want to delete the file.
			}
			cleanupTemporaryFiles();
		}
	}

	/** {@inheritDoc} */
	@Override
	protected void onPackHeader(long objectCount) throws IOException {
		// Ignored, the count is not required.
	}

	/** {@inheritDoc} */
	@Override
	protected void onBeginWholeObject(long streamPosition, int type,
			long inflatedSize) throws IOException {
		crc.reset();
	}

	/** {@inheritDoc} */
	@Override
	protected void onEndWholeObject(PackedObjectInfo info) throws IOException {
		info.setCRC((int) crc.getValue());
	}

	/** {@inheritDoc} */
	@Override
	protected void onBeginOfsDelta(long streamPosition,
			long baseStreamPosition, long inflatedSize) throws IOException {
		crc.reset();
	}

	/** {@inheritDoc} */
	@Override
	protected void onBeginRefDelta(long streamPosition, AnyObjectId baseId,
			long inflatedSize) throws IOException {
		crc.reset();
	}

	/** {@inheritDoc} */
	@Override
	protected UnresolvedDelta onEndDelta() throws IOException {
		UnresolvedDelta delta = new UnresolvedDelta();
		delta.setCRC((int) crc.getValue());
		return delta;
	}

	/** {@inheritDoc} */
	@Override
	protected void onInflatedObjectData(PackedObjectInfo obj, int typeCode,
			byte[] data) throws IOException {
		// ObjectDirectory ignores this event.
	}

	/** {@inheritDoc} */
	@Override
	protected void onObjectHeader(Source src, byte[] raw, int pos, int len)
			throws IOException {
		crc.update(raw, pos, len);
	}

	/** {@inheritDoc} */
	@Override
	protected void onObjectData(Source src, byte[] raw, int pos, int len)
			throws IOException {
		crc.update(raw, pos, len);
	}

	/** {@inheritDoc} */
	@Override
	protected void onStoreStream(byte[] raw, int pos, int len)
			throws IOException {
		out.write(raw, pos, len);
	}

	/** {@inheritDoc} */
	@Override
	protected void onPackFooter(byte[] hash) throws IOException {
		packEnd = out.getFilePointer();
		origEnd = packEnd;
		origHash = hash;
		packHash = hash;
	}

	/** {@inheritDoc} */
	@Override
	protected ObjectTypeAndSize seekDatabase(UnresolvedDelta delta,
			ObjectTypeAndSize info) throws IOException {
		out.seek(delta.getOffset());
		crc.reset();
		return readObjectHeader(info);
	}

	/** {@inheritDoc} */
	@Override
	protected ObjectTypeAndSize seekDatabase(PackedObjectInfo obj,
			ObjectTypeAndSize info) throws IOException {
		out.seek(obj.getOffset());
		crc.reset();
		return readObjectHeader(info);
	}

	/** {@inheritDoc} */
	@Override
	protected int readDatabase(byte[] dst, int pos, int cnt) throws IOException {
		return out.read(dst, pos, cnt);
	}

	/** {@inheritDoc} */
	@Override
	protected boolean checkCRC(int oldCRC) {
		return oldCRC == (int) crc.getValue();
	}

	private static String baseName(File tmpPack) {
		String name = tmpPack.getName();
		return name.substring(0, name.lastIndexOf('.'));
	}

	private void cleanupTemporaryFiles() {
		if (tmpIdx != null && !tmpIdx.delete() && tmpIdx.exists())
			tmpIdx.deleteOnExit();
		if (tmpPack != null && !tmpPack.delete() && tmpPack.exists())
			tmpPack.deleteOnExit();
	}

	/** {@inheritDoc} */
	@Override
	protected boolean onAppendBase(final int typeCode, final byte[] data,
			final PackedObjectInfo info) throws IOException {
		info.setOffset(packEnd);

		final byte[] buf = buffer();
		int sz = data.length;
		int len = 0;
		buf[len++] = (byte) ((typeCode << 4) | sz & 15);
		sz >>>= 4;
		while (sz > 0) {
			buf[len - 1] |= 0x80;
			buf[len++] = (byte) (sz & 0x7f);
			sz >>>= 7;
		}

		tailDigest.update(buf, 0, len);
		crc.reset();
		crc.update(buf, 0, len);
		out.seek(packEnd);
		out.write(buf, 0, len);
		packEnd += len;

		if (def == null)
			def = new Deflater(Deflater.DEFAULT_COMPRESSION, false);
		else
			def.reset();
		def.setInput(data);
		def.finish();

		while (!def.finished()) {
			len = def.deflate(buf);
			tailDigest.update(buf, 0, len);
			crc.update(buf, 0, len);
			out.write(buf, 0, len);
			packEnd += len;
		}

		info.setCRC((int) crc.getValue());
		return true;
	}

	/** {@inheritDoc} */
	@Override
	protected void onEndThinPack() throws IOException {
		final byte[] buf = buffer();

		final MessageDigest origDigest = Constants.newMessageDigest();
		final MessageDigest tailDigest2 = Constants.newMessageDigest();
		final MessageDigest packDigest = Constants.newMessageDigest();

		long origRemaining = origEnd;
		out.seek(0);
		out.readFully(buf, 0, 12);
		origDigest.update(buf, 0, 12);
		origRemaining -= 12;

		NB.encodeInt32(buf, 8, getObjectCount());
		out.seek(0);
		out.write(buf, 0, 12);
		packDigest.update(buf, 0, 12);

		for (;;) {
			final int n = out.read(buf);
			if (n < 0)
				break;
			if (origRemaining != 0) {
				final int origCnt = (int) Math.min(n, origRemaining);
				origDigest.update(buf, 0, origCnt);
				origRemaining -= origCnt;
				if (origRemaining == 0)
					tailDigest2.update(buf, origCnt, n - origCnt);
			} else
				tailDigest2.update(buf, 0, n);

			packDigest.update(buf, 0, n);
		}

		if (!Arrays.equals(origDigest.digest(), origHash) || !Arrays
				.equals(tailDigest2.digest(), this.tailDigest.digest()))
			throw new IOException(
					JGitText.get().packCorruptedWhileWritingToFilesystem);

		packHash = packDigest.digest();
	}

	private void writeIdx() throws IOException {
		List<PackedObjectInfo> list = getSortedObjectList(null /* by ObjectId */);
		try (FileOutputStream os = new FileOutputStream(tmpIdx)) {
			final PackIndexWriter iw;
			if (indexVersion <= 0)
				iw = PackIndexWriter.createOldestPossible(os, list);
			else
				iw = PackIndexWriter.createVersion(os, indexVersion);
			iw.write(list, packHash);
			os.getChannel().force(true);
		}
	}

	private PackLock renameAndOpenPack(String lockMessage)
			throws IOException {
		if (!keepEmpty && getObjectCount() == 0) {
			cleanupTemporaryFiles();
			return null;
		}

		final MessageDigest d = Constants.newMessageDigest();
		final byte[] oeBytes = new byte[Constants.OBJECT_ID_LENGTH];
		for (int i = 0; i < getObjectCount(); i++) {
			final PackedObjectInfo oe = getObject(i);
			oe.copyRawTo(oeBytes, 0);
			d.update(oeBytes);
		}

		final String name = ObjectId.fromRaw(d.digest()).name();
		final File packDir = new File(db.getDirectory(), "pack"); //$NON-NLS-1$
		final File finalPack = new File(packDir, "pack-" + name + ".pack"); //$NON-NLS-1$ //$NON-NLS-2$
		final File finalIdx = new File(packDir, "pack-" + name + ".idx"); //$NON-NLS-1$ //$NON-NLS-2$
		final PackLock keep = new PackLock(finalPack, db.getFS());

		if (!packDir.exists() && !packDir.mkdir() && !packDir.exists()) {
			// The objects/pack directory isn't present, and we are unable
			// to create it. There is no way to move this pack in.
			//
			cleanupTemporaryFiles();
			throw new IOException(MessageFormat.format(
					JGitText.get().cannotCreateDirectory, packDir
							.getAbsolutePath()));
		}

		if (finalPack.exists()) {
			// If the pack is already present we should never replace it.
			//
			cleanupTemporaryFiles();
			return null;
		}

		if (lockMessage != null) {
			// If we have a reason to create a keep file for this pack, do
			// so, or fail fast and don't put the pack in place.
			//
			try {
				if (!keep.lock(lockMessage))
					throw new LockFailedException(finalPack,
							MessageFormat.format(
									JGitText.get().cannotLockPackIn, finalPack));
			} catch (IOException e) {
				cleanupTemporaryFiles();
				throw e;
			}
		}

		try {
			FileUtils.rename(tmpPack, finalPack,
					StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			cleanupTemporaryFiles();
			keep.unlock();
			throw new IOException(MessageFormat.format(
					JGitText.get().cannotMovePackTo, finalPack), e);
		}

		try {
			FileUtils.rename(tmpIdx, finalIdx, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			cleanupTemporaryFiles();
			keep.unlock();
			if (!finalPack.delete())
				finalPack.deleteOnExit();
			throw new IOException(MessageFormat.format(
					JGitText.get().cannotMoveIndexTo, finalIdx), e);
		}

		boolean interrupted = false;
		try {
			FileSnapshot snapshot = FileSnapshot.save(finalPack);
			if (pconfig.doWaitPreventRacyPack(snapshot.size())) {
				snapshot.waitUntilNotRacy();
			}
		} catch (InterruptedException e) {
			interrupted = true;
		}
		try {
			newPack = db.openPack(finalPack);
		} catch (IOException err) {
			keep.unlock();
			if (finalPack.exists())
				FileUtils.delete(finalPack);
			if (finalIdx.exists())
				FileUtils.delete(finalIdx);
			throw err;
		} finally {
			if (interrupted) {
				// Re-set interrupted flag
				Thread.currentThread().interrupt();
			}
		}

		return lockMessage != null ? keep : null;
	}
}

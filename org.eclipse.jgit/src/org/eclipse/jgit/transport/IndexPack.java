/*
 * Copyright (C) 2008-2010, Google Inc.
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

package org.eclipse.jgit.transport;

import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig;
import org.eclipse.jgit.lib.InflaterCache;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdSubclassMap;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.storage.file.PackIndexWriter;
import org.eclipse.jgit.storage.file.PackLock;
import org.eclipse.jgit.storage.pack.BinaryDelta;
import org.eclipse.jgit.util.NB;

/** Indexes Git pack files for local use. */
public class IndexPack {
	/** Progress message when reading raw data from the pack. */
	public static final String PROGRESS_DOWNLOAD = JGitText.get().receivingObjects;

	/** Progress message when computing names of delta compressed objects. */
	public static final String PROGRESS_RESOLVE_DELTA = JGitText.get().resolvingDeltas;

	/**
	 * Size of the internal stream buffer.
	 * <p>
	 * If callers are going to be supplying IndexPack a BufferedInputStream they
	 * should use this buffer size as the size of the buffer for that
	 * BufferedInputStream, and any other its may be wrapping. This way the
	 * buffers will cascade efficiently and only the IndexPack buffer will be
	 * receiving the bulk of the data stream.
	 */
	public static final int BUFFER_SIZE = 8192;

	/**
	 * Create an index pack instance to load a new pack into a repository.
	 * <p>
	 * The received pack data and generated index will be saved to temporary
	 * files within the repository's <code>objects</code> directory. To use the
	 * data contained within them call {@link #renameAndOpenPack()} once the
	 * indexing is complete.
	 *
	 * @param db
	 *            the repository that will receive the new pack.
	 * @param is
	 *            stream to read the pack data from. If the stream is buffered
	 *            use {@link #BUFFER_SIZE} as the buffer size for the stream.
	 * @return a new index pack instance.
	 * @throws IOException
	 *             a temporary file could not be created.
	 */
	public static IndexPack create(final Repository db, final InputStream is)
			throws IOException {
		final String suffix = ".pack";
		final File objdir = db.getObjectsDirectory();
		final File tmp = File.createTempFile("incoming_", suffix, objdir);
		final String n = tmp.getName();
		final File base;

		base = new File(objdir, n.substring(0, n.length() - suffix.length()));
		final IndexPack ip = new IndexPack(db, is, base);
		ip.setIndexVersion(db.getConfig().get(CoreConfig.KEY)
				.getPackIndexVersion());
		return ip;
	}

	private static enum Source {
		/** Data is read from the incoming stream. */
		INPUT,

		/**
		 * Data is read from the spooled pack file.
		 * <p>
		 * During streaming, some (or all) data might be saved into the spooled
		 * pack file so it can be randomly accessed later.
		 */
		FILE;
	}

	private final Repository repo;

	/**
	 * Object database used for loading existing objects
	 */
	private final ObjectDatabase objectDatabase;

	private Inflater inflater;

	private final MessageDigest objectDigest;

	private final MutableObjectId tempObjectId;

	private InputStream in;

	private byte[] buf;

	private long bBase;

	private int bOffset;

	private int bAvail;

	private ObjectChecker objCheck;

	private boolean fixThin;

	private boolean keepEmpty;

	private boolean needBaseObjectIds;

	private int outputVersion;

	private final File dstPack;

	private final File dstIdx;

	private long objectCount;

	private PackedObjectInfo[] entries;

	/**
	 * Every object contained within the incoming pack.
	 * <p>
	 * This is a subset of {@link #entries}, as thin packs can add additional
	 * objects to {@code entries} by copying already existing objects from the
	 * repository onto the end of the thin pack to make it self-contained.
	 */
	private ObjectIdSubclassMap<ObjectId> newObjectIds;

	private int deltaCount;

	private int entryCount;

	private final CRC32 crc = new CRC32();

	private ObjectIdSubclassMap<DeltaChain> baseById;

	/**
	 * Objects referenced by their name from deltas, that aren't in this pack.
	 * <p>
	 * This is the set of objects that were copied onto the end of this pack to
	 * make it complete. These objects were not transmitted by the remote peer,
	 * but instead were assumed to already exist in the local repository.
	 */
	private ObjectIdSubclassMap<ObjectId> baseObjectIds;

	private LongMap<UnresolvedDelta> baseByPos;

	private byte[] skipBuffer;

	private MessageDigest packDigest;

	private RandomAccessFile packOut;

	private byte[] packcsum;

	/** If {@link #fixThin} this is the last byte of the original checksum. */
	private long originalEOF;

	private ObjectReader readCurs;

	/**
	 * Create a new pack indexer utility.
	 *
	 * @param db
	 * @param src
	 *            stream to read the pack data from. If the stream is buffered
	 *            use {@link #BUFFER_SIZE} as the buffer size for the stream.
	 * @param dstBase
	 * @throws IOException
	 *             the output packfile could not be created.
	 */
	public IndexPack(final Repository db, final InputStream src,
			final File dstBase) throws IOException {
		repo = db;
		objectDatabase = db.getObjectDatabase().newCachedDatabase();
		in = src;
		inflater = InflaterCache.get();
		readCurs = objectDatabase.newReader();
		buf = new byte[BUFFER_SIZE];
		skipBuffer = new byte[512];
		objectDigest = Constants.newMessageDigest();
		tempObjectId = new MutableObjectId();
		packDigest = Constants.newMessageDigest();

		if (dstBase != null) {
			final File dir = dstBase.getParentFile();
			final String nam = dstBase.getName();
			dstPack = new File(dir, nam + ".pack");
			dstIdx = new File(dir, nam + ".idx");
			packOut = new RandomAccessFile(dstPack, "rw");
			packOut.setLength(0);
		} else {
			dstPack = null;
			dstIdx = null;
		}
	}

	/**
	 * Set the pack index file format version this instance will create.
	 *
	 * @param version
	 *            the version to write. The special version 0 designates the
	 *            oldest (most compatible) format available for the objects.
	 * @see PackIndexWriter
	 */
	public void setIndexVersion(final int version) {
		outputVersion = version;
	}

	/**
	 * Configure this index pack instance to make a thin pack complete.
	 * <p>
	 * Thin packs are sometimes used during network transfers to allow a delta
	 * to be sent without a base object. Such packs are not permitted on disk.
	 * They can be fixed by copying the base object onto the end of the pack.
	 *
	 * @param fix
	 *            true to enable fixing a thin pack.
	 */
	public void setFixThin(final boolean fix) {
		fixThin = fix;
	}

	/**
	 * Configure this index pack instance to keep an empty pack.
	 * <p>
	 * By default an empty pack (a pack with no objects) is not kept, as doing
	 * so is completely pointless. With no objects in the pack there is no data
	 * stored by it, so the pack is unnecessary.
	 *
	 * @param empty true to enable keeping an empty pack.
	 */
	public void setKeepEmpty(final boolean empty) {
		keepEmpty = empty;
	}

	/**
	 * Configure this index pack instance to keep track of new objects.
	 * <p>
	 * By default an index pack doesn't save the new objects that were created
	 * when it was instantiated. Setting this flag to {@code true} allows the
	 * caller to use {@link #getNewObjectIds()} to retrieve that list.
	 *
	 * @param b {@code true} to enable keeping track of new objects.
	 */
	public void setNeedNewObjectIds(boolean b) {
		if (b)
			newObjectIds = new ObjectIdSubclassMap<ObjectId>();
		else
			newObjectIds = null;
	}

	private boolean needNewObjectIds() {
		return newObjectIds != null;
	}

	/**
	 * Configure this index pack instance to keep track of the objects assumed
	 * for delta bases.
	 * <p>
	 * By default an index pack doesn't save the objects that were used as delta
	 * bases. Setting this flag to {@code true} will allow the caller to
	 * use {@link #getBaseObjectIds()} to retrieve that list.
	 *
	 * @param b {@code true} to enable keeping track of delta bases.
	 */
	public void setNeedBaseObjectIds(boolean b) {
		this.needBaseObjectIds = b;
	}

	/** @return the new objects that were sent by the user */
	public ObjectIdSubclassMap<ObjectId> getNewObjectIds() {
		if (newObjectIds != null)
			return newObjectIds;
		return new ObjectIdSubclassMap<ObjectId>();
	}

	/** @return set of objects the incoming pack assumed for delta purposes */
	public ObjectIdSubclassMap<ObjectId> getBaseObjectIds() {
		if (baseObjectIds != null)
			return baseObjectIds;
		return new ObjectIdSubclassMap<ObjectId>();
	}

	/**
	 * Configure the checker used to validate received objects.
	 * <p>
	 * Usually object checking isn't necessary, as Git implementations only
	 * create valid objects in pack files. However, additional checking may be
	 * useful if processing data from an untrusted source.
	 *
	 * @param oc
	 *            the checker instance; null to disable object checking.
	 */
	public void setObjectChecker(final ObjectChecker oc) {
		objCheck = oc;
	}

	/**
	 * Configure the checker used to validate received objects.
	 * <p>
	 * Usually object checking isn't necessary, as Git implementations only
	 * create valid objects in pack files. However, additional checking may be
	 * useful if processing data from an untrusted source.
	 * <p>
	 * This is shorthand for:
	 *
	 * <pre>
	 * setObjectChecker(on ? new ObjectChecker() : null);
	 * </pre>
	 *
	 * @param on
	 *            true to enable the default checker; false to disable it.
	 */
	public void setObjectChecking(final boolean on) {
		setObjectChecker(on ? new ObjectChecker() : null);
	}

	/**
	 * Consume data from the input stream until the packfile is indexed.
	 *
	 * @param progress
	 *            progress feedback
	 *
	 * @throws IOException
	 */
	public void index(final ProgressMonitor progress) throws IOException {
		progress.start(2 /* tasks */);
		try {
			try {
				readPackHeader();

				entries = new PackedObjectInfo[(int) objectCount];
				baseById = new ObjectIdSubclassMap<DeltaChain>();
				baseByPos = new LongMap<UnresolvedDelta>();

				progress.beginTask(PROGRESS_DOWNLOAD, (int) objectCount);
				for (int done = 0; done < objectCount; done++) {
					indexOneObject();
					progress.update(1);
					if (progress.isCancelled())
						throw new IOException(JGitText.get().downloadCancelled);
				}
				readPackFooter();
				endInput();
				progress.endTask();
				if (deltaCount > 0) {
					if (packOut == null)
						throw new IOException(JGitText.get().needPackOut);
					resolveDeltas(progress);
					if (entryCount < objectCount) {
						if (!fixThin) {
							throw new IOException(MessageFormat.format(
									JGitText.get().packHasUnresolvedDeltas, (objectCount - entryCount)));
						}
						fixThinPack(progress);
					}
				}
				if (packOut != null && (keepEmpty || entryCount > 0))
					packOut.getChannel().force(true);

				packDigest = null;
				baseById = null;
				baseByPos = null;

				if (dstIdx != null && (keepEmpty || entryCount > 0))
					writeIdx();

			} finally {
				try {
					if (readCurs != null)
						readCurs.release();
				} finally {
					readCurs = null;
				}

				try {
					InflaterCache.release(inflater);
				} finally {
					inflater = null;
					objectDatabase.close();
				}

				progress.endTask();
				if (packOut != null)
					packOut.close();
			}

			if (keepEmpty || entryCount > 0) {
				if (dstPack != null)
					dstPack.setReadOnly();
				if (dstIdx != null)
					dstIdx.setReadOnly();
			}
		} catch (IOException err) {
			if (dstPack != null)
				dstPack.delete();
			if (dstIdx != null)
				dstIdx.delete();
			throw err;
		}
	}

	private void resolveDeltas(final ProgressMonitor progress)
			throws IOException {
		progress.beginTask(PROGRESS_RESOLVE_DELTA, deltaCount);
		final int last = entryCount;
		for (int i = 0; i < last; i++) {
			final int before = entryCount;
			resolveDeltas(entries[i]);
			progress.update(entryCount - before);
			if (progress.isCancelled())
				throw new IOException(JGitText.get().downloadCancelledDuringIndexing);
		}
		progress.endTask();
	}

	private void resolveDeltas(final PackedObjectInfo oe) throws IOException {
		final int oldCRC = oe.getCRC();
		if (baseById.get(oe) != null || baseByPos.containsKey(oe.getOffset()))
			resolveDeltas(oe.getOffset(), oldCRC, Constants.OBJ_BAD, null, oe);
	}

	private void resolveDeltas(final long pos, final int oldCRC, int type,
			byte[] data, PackedObjectInfo oe) throws IOException {
		crc.reset();
		position(pos);
		int c = readFrom(Source.FILE);
		final int typeCode = (c >> 4) & 7;
		long sz = c & 15;
		int shift = 4;
		while ((c & 0x80) != 0) {
			c = readFrom(Source.FILE);
			sz += (c & 0x7f) << shift;
			shift += 7;
		}

		switch (typeCode) {
		case Constants.OBJ_COMMIT:
		case Constants.OBJ_TREE:
		case Constants.OBJ_BLOB:
		case Constants.OBJ_TAG:
			type = typeCode;
			data = inflateAndReturn(Source.FILE, sz);
			break;
		case Constants.OBJ_OFS_DELTA: {
			c = readFrom(Source.FILE) & 0xff;
			while ((c & 128) != 0)
				c = readFrom(Source.FILE) & 0xff;
			data = BinaryDelta.apply(data, inflateAndReturn(Source.FILE, sz));
			break;
		}
		case Constants.OBJ_REF_DELTA: {
			crc.update(buf, fill(Source.FILE, 20), 20);
			use(20);
			data = BinaryDelta.apply(data, inflateAndReturn(Source.FILE, sz));
			break;
		}
		default:
			throw new IOException(MessageFormat.format(JGitText.get().unknownObjectType, typeCode));
		}

		final int crc32 = (int) crc.getValue();
		if (oldCRC != crc32)
			throw new IOException(MessageFormat.format(JGitText.get().corruptionDetectedReReadingAt, pos));
		if (oe == null) {
			objectDigest.update(Constants.encodedTypeString(type));
			objectDigest.update((byte) ' ');
			objectDigest.update(Constants.encodeASCII(data.length));
			objectDigest.update((byte) 0);
			objectDigest.update(data);
			tempObjectId.fromRaw(objectDigest.digest(), 0);

			verifySafeObject(tempObjectId, type, data);
			oe = new PackedObjectInfo(pos, crc32, tempObjectId);
			addObjectAndTrack(oe);
		}

		resolveChildDeltas(pos, type, data, oe);
	}

	private UnresolvedDelta removeBaseById(final AnyObjectId id){
		final DeltaChain d = baseById.get(id);
		return d != null ? d.remove() : null;
	}

	private static UnresolvedDelta reverse(UnresolvedDelta c) {
		UnresolvedDelta tail = null;
		while (c != null) {
			final UnresolvedDelta n = c.next;
			c.next = tail;
			tail = c;
			c = n;
		}
		return tail;
	}

	private void resolveChildDeltas(final long pos, int type, byte[] data,
			PackedObjectInfo oe) throws IOException {
		UnresolvedDelta a = reverse(removeBaseById(oe));
		UnresolvedDelta b = reverse(baseByPos.remove(pos));
		while (a != null && b != null) {
			if (a.position < b.position) {
				resolveDeltas(a.position, a.crc, type, data, null);
				a = a.next;
			} else {
				resolveDeltas(b.position, b.crc, type, data, null);
				b = b.next;
			}
		}
		resolveChildDeltaChain(type, data, a);
		resolveChildDeltaChain(type, data, b);
	}

	private void resolveChildDeltaChain(final int type, final byte[] data,
			UnresolvedDelta a) throws IOException {
		while (a != null) {
			resolveDeltas(a.position, a.crc, type, data, null);
			a = a.next;
		}
	}

	private void fixThinPack(final ProgressMonitor progress) throws IOException {
		growEntries();

		if (needBaseObjectIds)
			baseObjectIds = new ObjectIdSubclassMap<ObjectId>();

		packDigest.reset();
		originalEOF = packOut.length() - 20;
		final Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, false);
		final List<DeltaChain> missing = new ArrayList<DeltaChain>(64);
		long end = originalEOF;
		for (final DeltaChain baseId : baseById) {
			if (baseId.head == null)
				continue;
			if (needBaseObjectIds)
				baseObjectIds.add(baseId);
			final ObjectLoader ldr;
			try {
				ldr = readCurs.open(baseId);
			} catch (MissingObjectException notFound) {
				missing.add(baseId);
				continue;
			}
			final byte[] data = ldr.getCachedBytes(Integer.MAX_VALUE);
			final int typeCode = ldr.getType();
			final PackedObjectInfo oe;

			crc.reset();
			packOut.seek(end);
			writeWhole(def, typeCode, data);
			oe = new PackedObjectInfo(end, (int) crc.getValue(), baseId);
			entries[entryCount++] = oe;
			end = packOut.getFilePointer();

			resolveChildDeltas(oe.getOffset(), typeCode, data, oe);
			if (progress.isCancelled())
				throw new IOException(JGitText.get().downloadCancelledDuringIndexing);
		}
		def.end();

		for (final DeltaChain base : missing) {
			if (base.head != null)
				throw new MissingObjectException(base, "delta base");
		}

		if (end - originalEOF < 20) {
			// Ugly corner case; if what we appended on to complete deltas
			// doesn't completely cover the SHA-1 we have to truncate off
			// we need to shorten the file, otherwise we will include part
			// of the old footer as object content.
			packOut.setLength(end);
		}

		fixHeaderFooter(packcsum, packDigest.digest());
	}

	private void writeWhole(final Deflater def, final int typeCode,
			final byte[] data) throws IOException {
		int sz = data.length;
		int hdrlen = 0;
		buf[hdrlen++] = (byte) ((typeCode << 4) | sz & 15);
		sz >>>= 4;
		while (sz > 0) {
			buf[hdrlen - 1] |= 0x80;
			buf[hdrlen++] = (byte) (sz & 0x7f);
			sz >>>= 7;
		}
		packDigest.update(buf, 0, hdrlen);
		crc.update(buf, 0, hdrlen);
		packOut.write(buf, 0, hdrlen);
		def.reset();
		def.setInput(data);
		def.finish();
		while (!def.finished()) {
			final int datlen = def.deflate(buf);
			packDigest.update(buf, 0, datlen);
			crc.update(buf, 0, datlen);
			packOut.write(buf, 0, datlen);
		}
	}

	private void fixHeaderFooter(final byte[] origcsum, final byte[] tailcsum)
			throws IOException {
		final MessageDigest origDigest = Constants.newMessageDigest();
		final MessageDigest tailDigest = Constants.newMessageDigest();
		long origRemaining = originalEOF;

		packOut.seek(0);
		bAvail = 0;
		bOffset = 0;
		fill(Source.FILE, 12);

		{
			final int origCnt = (int) Math.min(bAvail, origRemaining);
			origDigest.update(buf, 0, origCnt);
			origRemaining -= origCnt;
			if (origRemaining == 0)
				tailDigest.update(buf, origCnt, bAvail - origCnt);
		}

		NB.encodeInt32(buf, 8, entryCount);
		packOut.seek(0);
		packOut.write(buf, 0, 12);
		packOut.seek(bAvail);

		packDigest.reset();
		packDigest.update(buf, 0, bAvail);
		for (;;) {
			final int n = packOut.read(buf);
			if (n < 0)
				break;
			if (origRemaining != 0) {
				final int origCnt = (int) Math.min(n, origRemaining);
				origDigest.update(buf, 0, origCnt);
				origRemaining -= origCnt;
				if (origRemaining == 0)
					tailDigest.update(buf, origCnt, n - origCnt);
			} else
				tailDigest.update(buf, 0, n);

			packDigest.update(buf, 0, n);
		}

		if (!Arrays.equals(origDigest.digest(), origcsum)
				|| !Arrays.equals(tailDigest.digest(), tailcsum))
			throw new IOException(JGitText.get().packCorruptedWhileWritingToFilesystem);

		packcsum = packDigest.digest();
		packOut.write(packcsum);
	}

	private void growEntries() {
		final PackedObjectInfo[] ne;

		ne = new PackedObjectInfo[(int) objectCount + baseById.size()];
		System.arraycopy(entries, 0, ne, 0, entryCount);
		entries = ne;
	}

	private void writeIdx() throws IOException {
		Arrays.sort(entries, 0, entryCount);
		List<PackedObjectInfo> list = Arrays.asList(entries);
		if (entryCount < entries.length)
			list = list.subList(0, entryCount);

		final FileOutputStream os = new FileOutputStream(dstIdx);
		try {
			final PackIndexWriter iw;
			if (outputVersion <= 0)
				iw = PackIndexWriter.createOldestPossible(os, list);
			else
				iw = PackIndexWriter.createVersion(os, outputVersion);
			iw.write(list, packcsum);
			os.getChannel().force(true);
		} finally {
			os.close();
		}
	}

	private void readPackHeader() throws IOException {
		final int hdrln = Constants.PACK_SIGNATURE.length + 4 + 4;
		final int p = fill(Source.INPUT, hdrln);
		for (int k = 0; k < Constants.PACK_SIGNATURE.length; k++)
			if (buf[p + k] != Constants.PACK_SIGNATURE[k])
				throw new IOException(JGitText.get().notAPACKFile);

		final long vers = NB.decodeUInt32(buf, p + 4);
		if (vers != 2 && vers != 3)
			throw new IOException(MessageFormat.format(JGitText.get().unsupportedPackVersion, vers));
		objectCount = NB.decodeUInt32(buf, p + 8);
		use(hdrln);
	}

	private void readPackFooter() throws IOException {
		sync();
		final byte[] cmpcsum = packDigest.digest();
		final int c = fill(Source.INPUT, 20);
		packcsum = new byte[20];
		System.arraycopy(buf, c, packcsum, 0, 20);
		use(20);
		if (packOut != null)
			packOut.write(packcsum);

		if (!Arrays.equals(cmpcsum, packcsum))
			throw new CorruptObjectException(JGitText.get().corruptObjectPackfileChecksumIncorrect);
	}

	// Cleanup all resources associated with our input parsing.
	private void endInput() {
		in = null;
		skipBuffer = null;
	}

	// Read one entire object or delta from the input.
	private void indexOneObject() throws IOException {
		final long pos = position();

		crc.reset();
		int c = readFrom(Source.INPUT);
		final int typeCode = (c >> 4) & 7;
		long sz = c & 15;
		int shift = 4;
		while ((c & 0x80) != 0) {
			c = readFrom(Source.INPUT);
			sz += (c & 0x7f) << shift;
			shift += 7;
		}

		switch (typeCode) {
		case Constants.OBJ_COMMIT:
		case Constants.OBJ_TREE:
		case Constants.OBJ_BLOB:
		case Constants.OBJ_TAG:
			whole(typeCode, pos, sz);
			break;
		case Constants.OBJ_OFS_DELTA: {
			c = readFrom(Source.INPUT);
			long ofs = c & 127;
			while ((c & 128) != 0) {
				ofs += 1;
				c = readFrom(Source.INPUT);
				ofs <<= 7;
				ofs += (c & 127);
			}
			final long base = pos - ofs;
			final UnresolvedDelta n;
			inflateAndSkip(Source.INPUT, sz);
			n = new UnresolvedDelta(pos, (int) crc.getValue());
			n.next = baseByPos.put(base, n);
			deltaCount++;
			break;
		}
		case Constants.OBJ_REF_DELTA: {
			c = fill(Source.INPUT, 20);
			crc.update(buf, c, 20);
			final ObjectId base = ObjectId.fromRaw(buf, c);
			use(20);
			DeltaChain r = baseById.get(base);
			if (r == null) {
				r = new DeltaChain(base);
				baseById.add(r);
			}
			inflateAndSkip(Source.INPUT, sz);
			r.add(new UnresolvedDelta(pos, (int) crc.getValue()));
			deltaCount++;
			break;
		}
		default:
			throw new IOException(MessageFormat.format(JGitText.get().unknownObjectType, typeCode));
		}
	}

	private void whole(final int type, final long pos, final long sz)
			throws IOException {
		final byte[] data = inflateAndReturn(Source.INPUT, sz);
		objectDigest.update(Constants.encodedTypeString(type));
		objectDigest.update((byte) ' ');
		objectDigest.update(Constants.encodeASCII(sz));
		objectDigest.update((byte) 0);
		objectDigest.update(data);
		tempObjectId.fromRaw(objectDigest.digest(), 0);

		verifySafeObject(tempObjectId, type, data);
		final int crc32 = (int) crc.getValue();
		addObjectAndTrack(new PackedObjectInfo(pos, crc32, tempObjectId));
	}

	private void verifySafeObject(final AnyObjectId id, final int type,
			final byte[] data) throws IOException {
		if (objCheck != null) {
			try {
				objCheck.check(type, data);
			} catch (CorruptObjectException e) {
				throw new IOException(MessageFormat.format(JGitText.get().invalidObject
						, Constants.typeString(type) , id.name() , e.getMessage()));
			}
		}

		try {
			final ObjectLoader ldr = readCurs.open(id, type);
			final byte[] existingData = ldr.getCachedBytes(Integer.MAX_VALUE);
			if (!Arrays.equals(data, existingData)) {
				throw new IOException(MessageFormat.format(JGitText.get().collisionOn, id.name()));
			}
		} catch (MissingObjectException notLocal) {
			// This is OK, we don't have a copy of the object locally
			// but the API throws when we try to read it as usually its
			// an error to read something that doesn't exist.
		}
	}

	// Current position of {@link #bOffset} within the entire file.
	private long position() {
		return bBase + bOffset;
	}

	private void position(final long pos) throws IOException {
		packOut.seek(pos);
		bBase = pos;
		bOffset = 0;
		bAvail = 0;
	}

	// Consume exactly one byte from the buffer and return it.
	private int readFrom(final Source src) throws IOException {
		if (bAvail == 0)
			fill(src, 1);
		bAvail--;
		final int b = buf[bOffset++] & 0xff;
		crc.update(b);
		return b;
	}

	// Consume cnt bytes from the buffer.
	private void use(final int cnt) {
		bOffset += cnt;
		bAvail -= cnt;
	}

	// Ensure at least need bytes are available in in {@link #buf}.
	private int fill(final Source src, final int need) throws IOException {
		while (bAvail < need) {
			int next = bOffset + bAvail;
			int free = buf.length - next;
			if (free + bAvail < need) {
				switch(src){
				case INPUT:
					sync();
					break;
				case FILE:
					if (bAvail > 0)
						System.arraycopy(buf, bOffset, buf, 0, bAvail);
					bOffset = 0;
					break;
				}
				next = bAvail;
				free = buf.length - next;
			}
			switch(src){
			case INPUT:
				next = in.read(buf, next, free);
				break;
			case FILE:
				next = packOut.read(buf, next, free);
				break;
			}
			if (next <= 0)
				throw new EOFException(JGitText.get().packfileIsTruncated);
			bAvail += next;
		}
		return bOffset;
	}

	// Store consumed bytes in {@link #buf} up to {@link #bOffset}.
	private void sync() throws IOException {
		packDigest.update(buf, 0, bOffset);
		if (packOut != null)
			packOut.write(buf, 0, bOffset);
		if (bAvail > 0)
			System.arraycopy(buf, bOffset, buf, 0, bAvail);
		bBase += bOffset;
		bOffset = 0;
	}

	private void inflateAndSkip(final Source src, final long inflatedSize)
			throws IOException {
		inflate(src, inflatedSize, skipBuffer, false /* do not keep result */);
	}

	private byte[] inflateAndReturn(final Source src, final long inflatedSize)
			throws IOException {
		final byte[] dst = new byte[(int) inflatedSize];
		inflate(src, inflatedSize, dst, true /* keep result in dst */);
		return dst;
	}

	private void inflate(final Source src, final long inflatedSize,
			final byte[] dst, final boolean keep) throws IOException {
		final Inflater inf = inflater;
		try {
			int off = 0;
			long cnt = 0;
			int p = fill(src, 24);
			inf.setInput(buf, p, bAvail);

			for (;;) {
				int r = inf.inflate(dst, off, dst.length - off);
				if (r == 0) {
					if (inf.finished())
						break;
					if (inf.needsInput()) {
						if (p >= 0) {
							crc.update(buf, p, bAvail);
							use(bAvail);
						}
						p = fill(src, 24);
						inf.setInput(buf, p, bAvail);
					} else {
						throw new CorruptObjectException(MessageFormat.format(
								JGitText.get().packfileCorruptionDetected,
								JGitText.get().unknownZlibError));
					}
				}
				cnt += r;
				if (keep)
					off += r;
			}

			if (cnt != inflatedSize) {
				throw new CorruptObjectException(MessageFormat.format(JGitText
						.get().packfileCorruptionDetected,
						JGitText.get().wrongDecompressedLength));
			}

			int left = bAvail - inf.getRemaining();
			if (left > 0) {
				crc.update(buf, p, left);
				use(left);
			}
		} catch (DataFormatException dfe) {
			throw new CorruptObjectException(MessageFormat.format(JGitText
					.get().packfileCorruptionDetected, dfe.getMessage()));
		} finally {
			inf.reset();
		}
	}

	private static class DeltaChain extends ObjectId {
		UnresolvedDelta head;

		DeltaChain(final AnyObjectId id) {
			super(id);
		}

		UnresolvedDelta remove() {
			final UnresolvedDelta r = head;
			if (r != null)
				head = null;
			return r;
		}

		void add(final UnresolvedDelta d) {
			d.next = head;
			head = d;
		}
	}

	private static class UnresolvedDelta {
		final long position;

		final int crc;

		UnresolvedDelta next;

		UnresolvedDelta(final long headerOffset, final int crc32) {
			position = headerOffset;
			crc = crc32;
		}
	}

	/**
	 * Rename the pack to it's final name and location and open it.
	 * <p>
	 * If the call completes successfully the repository this IndexPack instance
	 * was created with will have the objects in the pack available for reading
	 * and use, without needing to scan for packs.
	 *
	 * @throws IOException
	 *             The pack could not be inserted into the repository's objects
	 *             directory. The pack no longer exists on disk, as it was
	 *             removed prior to throwing the exception to the caller.
	 */
	public void renameAndOpenPack() throws IOException {
		renameAndOpenPack(null);
	}

	/**
	 * Rename the pack to it's final name and location and open it.
	 * <p>
	 * If the call completes successfully the repository this IndexPack instance
	 * was created with will have the objects in the pack available for reading
	 * and use, without needing to scan for packs.
	 *
	 * @param lockMessage
	 *            message to place in the pack-*.keep file. If null, no lock
	 *            will be created, and this method returns null.
	 * @return the pack lock object, if lockMessage is not null.
	 * @throws IOException
	 *             The pack could not be inserted into the repository's objects
	 *             directory. The pack no longer exists on disk, as it was
	 *             removed prior to throwing the exception to the caller.
	 */
	public PackLock renameAndOpenPack(final String lockMessage)
			throws IOException {
		if (!keepEmpty && entryCount == 0) {
			cleanupTemporaryFiles();
			return null;
		}

		final MessageDigest d = Constants.newMessageDigest();
		final byte[] oeBytes = new byte[Constants.OBJECT_ID_LENGTH];
		for (int i = 0; i < entryCount; i++) {
			final PackedObjectInfo oe = entries[i];
			oe.copyRawTo(oeBytes, 0);
			d.update(oeBytes);
		}

		final String name = ObjectId.fromRaw(d.digest()).name();
		final File packDir = new File(repo.getObjectsDirectory(), "pack");
		final File finalPack = new File(packDir, "pack-" + name + ".pack");
		final File finalIdx = new File(packDir, "pack-" + name + ".idx");
		final PackLock keep = new PackLock(finalPack, repo.getFS());

		if (!packDir.exists() && !packDir.mkdir() && !packDir.exists()) {
			// The objects/pack directory isn't present, and we are unable
			// to create it. There is no way to move this pack in.
			//
			cleanupTemporaryFiles();
			throw new IOException(MessageFormat.format(JGitText.get().cannotCreateDirectory, packDir.getAbsolutePath()));
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
					throw new IOException(MessageFormat.format(JGitText.get().cannotLockPackIn, finalPack));
			} catch (IOException e) {
				cleanupTemporaryFiles();
				throw e;
			}
		}

		if (!dstPack.renameTo(finalPack)) {
			cleanupTemporaryFiles();
			keep.unlock();
			throw new IOException(MessageFormat.format(JGitText.get().cannotMovePackTo, finalPack));
		}

		if (!dstIdx.renameTo(finalIdx)) {
			cleanupTemporaryFiles();
			keep.unlock();
			if (!finalPack.delete())
				finalPack.deleteOnExit();
			throw new IOException(MessageFormat.format(JGitText.get().cannotMoveIndexTo, finalIdx));
		}

		try {
			repo.openPack(finalPack, finalIdx);
		} catch (IOException err) {
			keep.unlock();
			finalPack.delete();
			finalIdx.delete();
			throw err;
		}

		return lockMessage != null ? keep : null;
	}

	private void cleanupTemporaryFiles() {
		if (!dstIdx.delete())
			dstIdx.deleteOnExit();
		if (!dstPack.delete())
			dstPack.deleteOnExit();
	}

	private void addObjectAndTrack(PackedObjectInfo oe) {
		entries[entryCount++] = oe;
		if (needNewObjectIds())
			newObjectIds.add(oe);
	}
}

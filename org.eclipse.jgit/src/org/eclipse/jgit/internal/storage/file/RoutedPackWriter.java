/*
 * Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.eclipse.jgit.errors.LockFailedException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.internal.storage.pack.PackIndexWriter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.transport.PackLock;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.sha1.SHA1;

/** Receive-specific output writer; closing aborts, and never publishes. */
final class RoutedPackWriter implements AutoCloseable {
	private final ObjectDirectory main;

	private final PackConfig config;

	private final ProgressMonitor receiving;

	private final ProgressMonitor resolving;

	private final byte[] buffer = new byte[8192];

	private final byte[] header = new byte[32];

	private final CRC32 crc = new CRC32();

	private final List<PackedObjectInfo> objects = new ArrayList<>();

	private final Map<ObjectId, PackedObjectInfo> membership = new HashMap<>();

	private final Map<ObjectId, ObjectId> dependencies = new HashMap<>();

	private final List<File> installed = new ArrayList<>(3);

	private File temporaryPack;

	private File temporaryIndex;

	private File temporarySizeIndex;

	private RandomAccessFile output;

	private Deflater deflater;

	private DeflaterOutputStream compressor;

	private PackLockImpl ownedKeep;

	private Pack pack;

	private boolean failed;

	private boolean finished;

	private boolean published;

	private boolean closed;

	RoutedPackWriter(ObjectDirectory main) {
		this(main, null, null);
	}

	RoutedPackWriter(ObjectDirectory main, ProgressMonitor receiving,
			ProgressMonitor resolving) {
		this.main = main;
		this.config = new PackConfig(main.getConfig());
		this.receiving = receiving;
		this.resolving = resolving;
	}

	void appendWhole(ObjectId id, int type, long fullSize, InputStream data)
			throws IOException {
		checkWritable();
		try {
			if (fullSize < 0 || type < Constants.OBJ_COMMIT
					|| type > Constants.OBJ_TAG) {
				throw new IllegalArgumentException();
			}
			if (alreadyWritten(id, type, fullSize)) {
				return;
			}
			beginPack();
			long offset = output.getFilePointer();
			crc.reset();
			deflater.reset();
			write(header, 0, encodeTypeSize(type, fullSize));
			SHA1 hash = SHA1.newInstance();
			hash.update(Constants.encodedTypeString(type));
			hash.update((byte) ' ');
			hash.update(Constants.encodeASCII(fullSize));
			hash.update((byte) 0);
			long remaining = fullSize;
			while (remaining > 0) {
				checkCancelled();
				int n = data.read(buffer, 0, (int) Math.min(buffer.length, remaining));
				if (n <= 0) {
					throw new EOFException(MessageFormat.format(
							JGitText.get().inputDidntMatchLength,
							Long.valueOf(remaining)));
				}
				hash.update(buffer, 0, n);
				compressor.write(buffer, 0, n);
				remaining -= n;
			}
			if (data.read() != -1) {
				throw new IOException(JGitText.get().wrongDecompressedLength);
			}
			compressor.finish();
			ObjectId actual = hash.toObjectId();
			if (!id.equals(actual)) {
				throw new IOException(MessageFormat.format(
						JGitText.get().receivedObjectIdMismatch, id.name(), actual.name()));
			}
			PackedObjectInfo object = new PackedObjectInfo(id);
			object.setOffset(offset);
			object.setType(type);
			object.setFullSize(fullSize);
			object.setCRC((int) crc.getValue());
			objects.add(object);
			membership.put(object.copy(), object);
		} catch (IOException | RuntimeException | Error failure) {
			failed = true;
			throw failure;
		}
	}

	/**
	 * Copy validated INPUT bytes, independently checking original and output CRCs.
	 */
	void appendCopied(ReceivedPackEntry entry, IncomingPackSpool spool)
			throws IOException {
		checkWritable();
		try {
			if (entry.selected) {
				throw corruptSource();
			}
			if (alreadyWritten(entry.id, entry.type, entry.fullSize)) {
				return;
			}
			byte[] originalHeader = entry.source.originalHeader();
			validateSource(entry, spool, originalHeader.length);
			beginPack();
			long offset = output.getFilePointer();
			crc.reset();
			copySource(entry, spool, originalHeader);
			PackedObjectInfo object = new PackedObjectInfo(entry.id);
			object.setOffset(offset);
			object.setType(entry.type);
			object.setFullSize(entry.fullSize);
			object.setCRC((int) crc.getValue());
			objects.add(object);
			membership.put(object.copy(), object);
			if (!entry.whole) {
				dependencies.put(object.copy(), entry.baseId);
			}
		} catch (IOException | RuntimeException | Error failure) {
			failed = true;
			throw failure;
		}
	}

	private void validateSource(ReceivedPackEntry entry, IncomingPackSpool spool,
			int headerLength) throws IOException {
		ReceivedPackEntry.Source source = entry.source;
		if (source.offset < 12 || headerLength == 0 || headerLength > header.length
				|| source.offset > Long.MAX_VALUE - headerLength
				|| source.payloadOffset != source.offset + headerLength
				|| source.payloadLength <= 0 || source.declaredSize < 0
				|| source.payloadLength > Long.MAX_VALUE - source.payloadOffset
				|| source.payloadOffset + source.payloadLength > spool.length()
				|| entry.type < Constants.OBJ_COMMIT || entry.type > Constants.OBJ_TAG
				|| entry.fullSize < 0
				|| (entry.whole && (source.encodedType != entry.type
						|| source.declaredSize != entry.fullSize))
				|| (!entry.whole && entry.baseId == null)) {
			throw corruptSource();
		}
	}

	private void copySource(ReceivedPackEntry entry, IncomingPackSpool spool,
			byte[] originalHeader) throws IOException {
		ReceivedPackEntry.Source source = entry.source;
		CRC32 originalCrc = new CRC32();
		originalCrc.update(originalHeader);
		try (InputStream input = spool.openRange(source.offset,
				originalHeader.length + source.payloadLength)) {
			byte[] archivedHeader = input.readNBytes(originalHeader.length);
			if (!Arrays.equals(originalHeader, archivedHeader)) {
				throw corruptSource();
			}
			if (entry.whole) {
				write(originalHeader, 0, originalHeader.length);
			} else {
				write(header, 0, encodeTypeSize(Constants.OBJ_REF_DELTA,
						source.declaredSize));
				entry.baseId.copyRawTo(header, 0);
				write(header, 0, Constants.OBJECT_ID_LENGTH);
			}
			for (int n; (n = input.read(buffer)) != -1;) {
				originalCrc.update(buffer, 0, n);
				write(buffer, 0, n);
			}
		}
		if ((int) originalCrc.getValue() != source.crc) {
			throw corruptSource();
		}
	}

	private boolean alreadyWritten(ObjectId id, int type, long fullSize)
			throws IOException {
		PackedObjectInfo previous = membership.get(id);
		if (previous == null) {
			return false;
		}
		if (previous.getType() != type || previous.getFullSize() != fullSize) {
			throw new IOException(MessageFormat.format(
					JGitText.get().receivedObjectMetadataMismatch, id.name()));
		}
		return true;
	}

	private static IOException corruptSource() {
		return new IOException(JGitText.get().packCorruptedWhileWritingToFilesystem);
	}

	/** Validate completed dependencies, allowing forward but not missing/cyclic bases. */
	private void validateDependencies() throws IOException {
		Set<ObjectId> complete = new HashSet<>();
		for (PackedObjectInfo object : objects) {
			checkCancelled();
			Set<ObjectId> path = new HashSet<>();
			ObjectId id = object;
			while (!complete.contains(id)) {
				checkCancelled();
				if (!membership.containsKey(id) || !path.add(id)) {
					throw corruptSource();
				}
				ObjectId base = dependencies.get(id);
				if (base == null) {
					break;
				}
				id = base;
			}
			complete.addAll(path);
		}
	}

	private void beginPack() throws IOException {
		if (output != null) {
			return;
		}
		temporaryPack = File.createTempFile("routing_output_", ".pack", //$NON-NLS-1$ //$NON-NLS-2$
				main.getDirectory());
		output = new RandomAccessFile(temporaryPack, "rw"); //$NON-NLS-1$
		System.arraycopy(Constants.PACK_SIGNATURE, 0, header, 0, 4);
		NB.encodeInt32(header, 4, 2);
		NB.encodeInt32(header, 8, 0);
		output.write(header, 0, 12);
		deflater = new Deflater(config.getCompressionLevel());
		compressor = new DeflaterOutputStream(new OutputStream() {
			@Override
			public void write(int value) throws IOException {
				RoutedPackWriter.this.write(new byte[] { (byte) value }, 0, 1);
			}

			@Override
			public void write(byte[] data, int offset, int count) throws IOException {
				RoutedPackWriter.this.write(data, offset, count);
			}
		}, deflater, buffer.length);
	}

	private int encodeTypeSize(int type, long size) {
		int count = 0;
		int next = (type << 4) | (int) (size & 15);
		size >>>= 4;
		while (size > 0) {
			header[count++] = (byte) (next | 0x80);
			next = (int) (size & 0x7f);
			size >>>= 7;
		}
		header[count++] = (byte) next;
		return count;
	}

	private void write(byte[] data, int offset, int count) throws IOException {
		checkCancelled();
		crc.update(data, offset, count);
		output.write(data, offset, count);
	}

	PackLock publish(String lockMessage) throws IOException {
		checkWritable();
		finished = true;
		if (objects.isEmpty()) {
			return null;
		}
		checkCancelled();
		validateDependencies();
		byte[] packHash = finishPack();
		Collections.sort(objects);
		writeIndexes(packHash);
		PackFile finalPack = new PackFile(main.getPackDirectory(), computeName(),
				PackExt.PACK);
		PackFile finalIndex = finalPack.create(PackExt.INDEX);
		PackFile finalSizeIndex = finalPack.create(PackExt.OBJECT_SIZE_INDEX);
		FileUtils.mkdir(main.getPackDirectory(), true);
		if (exists(finalPack)) {
			if (!finalPack.isFile() || !finalIndex.isFile()) {
				throw new IOException(MessageFormat.format(
						JGitText.get().cannotMovePackTo, finalPack));
			}
			pack = main.openPack(finalPack);
			return null;
		}
		// Orphan final sidecars belong to someone else, not to this operation.
		if (exists(finalIndex) || exists(finalSizeIndex)) {
			throw new IOException(MessageFormat.format(
					JGitText.get().cannotMoveIndexTo,
					exists(finalIndex) ? finalIndex : finalSizeIndex));
		}
		createKeep(finalPack, lockMessage);
		checkCancelled();
		// Files.move without replacement preserves pre-existing destinations.
		// FileUtils.rename's destructive retries and ATOMIC_MOVE's replacement
		// ambiguity are inappropriate for this operation-owned publication.
		install(temporaryPack, finalPack);
		temporaryPack = null;
		install(temporaryIndex, finalIndex);
		temporaryIndex = null;
		if (temporarySizeIndex != null) {
			install(temporarySizeIndex, finalSizeIndex);
			temporarySizeIndex = null;
		}
		boolean interrupted = false;
		try {
			FileSnapshot snapshot = FileSnapshot.save(finalPack);
			if (config.doWaitPreventRacyPack(snapshot.size())) {
				snapshot.waitUntilNotRacy();
			}
		} catch (InterruptedException interruption) {
			interrupted = true;
		}
		try {
			checkCancelled();
			pack = main.openPack(finalPack);
			published = true;
			installed.clear();
		} finally {
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
		PackLock lock = ownedKeep;
		if (lockMessage == null) {
			ownedKeep.unlock();
			lock = null;
		}
		ownedKeep = null;
		return lock;
	}

	private static boolean exists(File path) {
		return Files.exists(path.toPath(), LinkOption.NOFOLLOW_LINKS);
	}

	private void install(File temporary, File destination) throws IOException {
		if (exists(destination)) {
			throw new FileAlreadyExistsException(destination.toString());
		}
		Files.move(temporary.toPath(), destination.toPath());
		installed.add(destination);
	}

	private void createKeep(PackFile finalPack, String message) throws IOException {
		PackFile keepFile = finalPack.create(PackExt.KEEP);
		try (FileChannel keep = FileChannel.open(keepFile.toPath(),
				StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
			ownedKeep = new PackLockImpl(finalPack, main.getFS());
			String text = message != null ? message : "Receiving routed objects"; //$NON-NLS-1$
			if (!text.endsWith("\n")) { //$NON-NLS-1$
				text += '\n';
			}
			ByteBuffer contents = ByteBuffer.wrap(Constants.encode(text));
			while (contents.hasRemaining()) {
				keep.write(contents);
			}
			keep.force(true);
		} catch (FileAlreadyExistsException foreignKeep) {
			throw new LockFailedException(finalPack, MessageFormat.format(
					JGitText.get().cannotLockPackIn, finalPack), foreignKeep);
		}
	}

	private byte[] finishPack() throws IOException {
		output.seek(8);
		output.writeInt(objects.size());
		output.seek(0);
		MessageDigest digest = Constants.newMessageDigest();
		for (int n; (n = output.read(buffer)) != -1;) {
			checkCancelled();
			digest.update(buffer, 0, n);
		}
		byte[] hash = digest.digest();
		output.write(hash);
		output.getChannel().force(true);
		output.close();
		output = null;
		temporaryPack.setReadOnly();
		return hash;
	}

	private ObjectId computeName() {
		MessageDigest digest = Constants.newMessageDigest();
		for (PackedObjectInfo object : objects) {
			object.copyRawTo(buffer, 0);
			digest.update(buffer, 0, Constants.OBJECT_ID_LENGTH);
		}
		return ObjectId.fromRaw(digest.digest());
	}

	private void writeIndexes(byte[] packHash) throws IOException {
		temporaryIndex = File.createTempFile("routing_output_", ".idx", //$NON-NLS-1$ //$NON-NLS-2$
				main.getDirectory());
		int version = main.getConfig().get(CoreConfig.KEY).getPackIndexVersion();
		try (FileOutputStream outputIndex = new FileOutputStream(temporaryIndex)) {
			PackIndexWriter writer = version <= 0
					? BasePackIndexWriter.createOldestPossible(outputIndex, objects)
					: BasePackIndexWriter.createVersion(outputIndex, version);
			writer.write(objects, packHash);
			outputIndex.getChannel().force(true);
		}
		temporaryIndex.setReadOnly();
		checkCancelled();
		if (config.isWriteObjSizeIndex()) {
			temporarySizeIndex = File.createTempFile("routing_output_", //$NON-NLS-1$
					'.' + PackExt.OBJECT_SIZE_INDEX.getExtension(), main.getDirectory());
			try (FileOutputStream outputIndex = new FileOutputStream(temporarySizeIndex)) {
				PackObjectSizeIndexWriter.createWriter(outputIndex,
						config.getMinBytesForObjSizeIndex()).write(objects);
				outputIndex.getChannel().force(true);
			}
			temporarySizeIndex.setReadOnly();
		}
	}

	Pack getPack() {
		return pack;
	}

	long getPackSize() {
		if (pack == null) {
			return 0;
		}
		return pack.getPackFile().length()
				+ new PackFile(pack.getPackFile()).create(PackExt.INDEX).length();
	}

	private void checkWritable() throws IOException {
		if (closed || finished || failed) {
			throw new IOException(JGitText.get().closed);
		}
	}

	private void checkCancelled() throws IOException {
		if ((receiving != null && receiving.isCancelled())
				|| (resolving != null && resolving.isCancelled())) {
			throw new IOException(JGitText.get().downloadCancelled);
		}
	}

	@Override
	public void close() throws IOException {
		if (closed) {
			return;
		}
		closed = true;
		IOException failure = null;
		if (deflater != null) {
			deflater.end();
		}
		if (output != null) {
			try {
				output.close();
			} catch (IOException cleanup) {
				failure = cleanup;
			}
		}
		List<File> cleanupFiles = new ArrayList<>();
		if (!published) {
			cleanupFiles.addAll(installed);
		}
		Collections.reverse(cleanupFiles);
		if (temporarySizeIndex != null) {
			cleanupFiles.add(temporarySizeIndex);
		}
		if (temporaryIndex != null) {
			cleanupFiles.add(temporaryIndex);
		}
		if (temporaryPack != null) {
			cleanupFiles.add(temporaryPack);
		}
		for (File path : cleanupFiles) {
			try {
				FileUtils.delete(path, FileUtils.RETRY | FileUtils.SKIP_MISSING);
			} catch (IOException cleanup) {
				if (failure == null) {
					failure = cleanup;
				} else {
					failure.addSuppressed(cleanup);
				}
			}
		}
		if (ownedKeep != null) {
			try {
				ownedKeep.unlock();
			} catch (IOException cleanup) {
				if (failure == null) {
					failure = cleanup;
				} else {
					failure.addSuppressed(cleanup);
				}
			}
		}
		if (failure != null) {
			throw failure;
		}
	}
}

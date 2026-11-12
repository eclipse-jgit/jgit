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

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.transport.PackLock;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.eclipse.jgit.transport.ReceivedObjectFilter;

/**
 * Exclusive receive routing with an unindexed spool and self-contained output.
 */
class RoutingPackParser extends PackParser implements AutoCloseable {
	private final ObjectDirectory main;

	private final ObjectDirectory alternate;

	private final ReceivedObjectFilter filter;

	private final Map<ObjectId, ReceivedPackEntry> received = new LinkedHashMap<>();

	private final Map<Long, ReceivedPackEntry.Source> sources = new HashMap<>();

	private final CRC32 crc = new CRC32();

	private long inputOffset;

	private int inputType;

	private long inputSize;

	private byte[] inputHeader;

	private long payloadLength;

	private IncomingPackSpool spool;

	private RoutedPackWriter writer;

	private ObjectDirectoryInserter loose;

	private ProgressMonitor receiving;

	private ProgressMonitor resolving;

	private long databasePosition;

	private long packSize;

	private boolean started;

	private boolean closed;

	RoutingPackParser(ObjectDirectory main, ObjectDirectory alternate,
			ReceivedObjectFilter filter, InputStream input) {
		super(main, input);
		this.main = main;
		this.alternate = alternate;
		this.filter = filter;
	}

	@Override
	public PackLock parse(ProgressMonitor receiving, ProgressMonitor resolving)
			throws IOException {
		if (started || closed) {
			throw new IllegalStateException(JGitText.get().closed);
		}
		started = true;
		this.receiving = receiving;
		this.resolving = resolving;
		PackLock lock = null;
		Throwable parseFailure = null;
		try (IncomingPackSpool archive = new IncomingPackSpool(main);
				RoutedPackWriter output = new RoutedPackWriter(main, receiving,
						resolving);
				ObjectDirectoryInserter destination = alternate.newInserter()) {
			spool = archive;
			writer = output;
			loose = destination;
			super.parse(receiving, resolving);
			for (ReceivedPackEntry entry : received.values()) {
				if (entry.whole && entry.selected) {
					checkCancelled();
					replayWholeObject(entry.replaySource(), (type, size, data) ->
							insertLoose(entry, data));
				}
			}
			for (ReceivedPackEntry entry : received.values()) {
				if (!entry.selected && !entry.outputEmitted) {
					checkCancelled();
					writer.appendCopied(entry, spool);
					entry.outputEmitted = true;
				}
				if (entry.selected == entry.outputEmitted) {
					throw new IOException(JGitText.get().packCorruptedWhileWritingToFilesystem);
				}
			}
			checkCancelled();
			loose.flush();
			// All archive consumers succeeded. A cleanup failure must also prevent
			// main publication, not strand a returned keep before ReceivePack sees it.
			archive.close();
			checkCancelled();
			lock = writer.publish(getLockMessage());
			packSize = writer.getPackSize();
			return lock;
		} catch (IOException | RuntimeException | Error failure) {
			parseFailure = failure;
			if (lock != null) {
				try {
					lock.unlock();
				} catch (IOException cleanup) {
					failure.addSuppressed(cleanup);
				}
			}
			throw failure;
		} finally {
			closed = true;
			spool = null;
			writer = null;
			loose = null;
			releaseParserResources(parseFailure);
		}
	}

	@Override
	public long getPackSize() {
		return packSize;
	}

	@Override
	protected void onEndWholeObject(PackedObjectInfo object) throws IOException {
		ReceivedPackEntry.Source source = finishSource();
		object.setCRC(source.crc);
		record(object);
	}

	@Override
	protected void onInflatedObjectData(PackedObjectInfo object, int type,
			byte[] data) throws IOException {
		ReceivedPackEntry entry = record(object);
		if (entry == null) {
			// Whole non-blobs already completed; duplicate representations never
			// replace the first entry's metadata or emit another physical output.
			return;
		}
		if (entry.selected) {
			insertLoose(entry, new ByteArrayInputStream(data));
		} else if (!receivedBaseIsKnownRetained(entry)) {
			// Membership may still be unknown when resolving via an external
			// representation. Expand now, without rejecting input or caching data.
			writer.appendWhole(entry.id, entry.type, entry.fullSize,
					new ByteArrayInputStream(data));
			entry.outputEmitted = true;
		}
	}

	private ReceivedPackEntry record(PackedObjectInfo object)
			throws IOException {
		ObjectId id = object.copy();
		ReceivedPackEntry previous = received.get(id);
		if (previous != null) {
			if (previous.type != object.getType()
					|| previous.fullSize != object.getFullSize()) {
				throw new IOException(MessageFormat.format(
						JGitText.get().receivedObjectMetadataMismatch, id.name()));
			}
			return null;
		}
		ReceivedPackEntry.Source source = sources
				.get(Long.valueOf(object.getOffset()));
		ObjectId baseId = object instanceof IncomingObjectInfo
				? ((IncomingObjectInfo) object).baseId : null;
		ReceivedPackEntry entry = new ReceivedPackEntry(object, source, baseId,
				filter.select(id, object.getType(), object.getFullSize()));
		received.put(id, entry);
		return entry;
	}

	private boolean receivedBaseIsKnownRetained(ReceivedPackEntry entry) {
		ReceivedPackEntry base = received.get(entry.baseId);
		// A newly recorded identity delta cannot establish its own base membership.
		return base != null && base != entry && !base.selected;
	}

	@Override
	protected PackedObjectInfo newInfo(AnyObjectId id, UnresolvedDelta delta,
			ObjectId deltaBase) {
		IncomingObjectInfo info = new IncomingObjectInfo(id, deltaBase);
		if (delta != null) {
			info.setCRC(delta.getCRC());
		}
		return info;
	}

	private static final class IncomingObjectInfo extends PackedObjectInfo {
		final ObjectId baseId;

		IncomingObjectInfo(AnyObjectId id, AnyObjectId baseId) {
			super(id);
			this.baseId = baseId != null ? baseId.copy() : null;
		}
	}

	private ObjectId insertLoose(ReceivedPackEntry entry, InputStream data)
			throws IOException {
		checkCancelled();
		ObjectId inserted = loose.insert(entry.type, entry.fullSize,
				cancellable(data), true);
		if (!entry.id.equals(inserted)) {
			throw new IOException(MessageFormat.format(
					JGitText.get().receivedObjectIdMismatch, entry.id.name(),
					ObjectId.toString(inserted)));
		}
		return inserted;
	}

	private InputStream cancellable(InputStream data) {
		return new FilterInputStream(data) {
			@Override
			public int read() throws IOException {
				checkCancelled();
				return super.read();
			}

			@Override
			public int read(byte[] buffer, int offset, int count) throws IOException {
				checkCancelled();
				return super.read(buffer, offset, count);
			}
		};
	}

	private void checkCancelled() throws IOException {
		if ((receiving != null && receiving.isCancelled())
				|| (resolving != null && resolving.isCancelled())) {
			throw new IOException(JGitText.get().downloadCancelled);
		}
	}

	@Override
	protected void onPackHeader(long count) {
		// Input count and signature are validated by PackParser.
	}

	@Override
	protected void onPackFooter(byte[] hash) {
		// Only input bytes before the checksum are archived. No index is created.
	}

	@Override
	protected void onBeginWholeObject(long offset, int type, long size) {
		beginSource(offset, type, size);
	}

	@Override
	protected void onBeginOfsDelta(long offset, long baseOffset, long size) {
		beginSource(offset, Constants.OBJ_OFS_DELTA, size);
	}

	@Override
	protected void onBeginRefDelta(long offset, AnyObjectId base, long size) {
		beginSource(offset, Constants.OBJ_REF_DELTA, size);
	}

	@Override
	protected UnresolvedDelta onEndDelta() {
		UnresolvedDelta delta = new UnresolvedDelta();
		delta.setCRC(finishSource().crc);
		return delta;
	}

	private void beginSource(long offset, int type, long size) {
		crc.reset();
		inputOffset = offset;
		inputType = type;
		inputSize = size;
		inputHeader = null;
		payloadLength = 0;
	}

	private ReceivedPackEntry.Source finishSource() {
		ReceivedPackEntry.Source source = new ReceivedPackEntry.Source(inputOffset,
				inputType, inputSize, inputHeader, inputOffset + inputHeader.length,
				payloadLength, (int) crc.getValue());
		sources.put(Long.valueOf(inputOffset), source);
		return source;
	}

	@Override
	protected void onStoreStream(byte[] data, int offset, int count)
			throws IOException {
		spool.append(data, offset, count);
	}

	@Override
	protected void onObjectHeader(Source source, byte[] data, int offset, int count) {
		crc.update(data, offset, count);
		if (source == Source.INPUT) {
			inputHeader = Arrays.copyOfRange(data, offset, offset + count);
		}
	}

	@Override
	protected void onObjectData(Source source, byte[] data, int offset, int count) {
		crc.update(data, offset, count);
		if (source == Source.INPUT) {
			payloadLength += count;
		}
	}

	@Override
	protected ObjectTypeAndSize seekDatabase(PackedObjectInfo object,
			ObjectTypeAndSize info) throws IOException {
		databasePosition = object.getOffset();
		crc.reset();
		return readObjectHeader(info);
	}

	@Override
	protected ObjectTypeAndSize seekDatabase(UnresolvedDelta delta,
			ObjectTypeAndSize info) throws IOException {
		databasePosition = delta.getOffset();
		crc.reset();
		return readObjectHeader(info);
	}

	@Override
	protected int readDatabase(byte[] data, int offset, int count) throws IOException {
		int read = spool.read(databasePosition, data, offset, count);
		if (read > 0) {
			databasePosition += read;
		}
		return read;
	}

	@Override
	protected boolean checkCRC(int expected) {
		return expected == (int) crc.getValue();
	}

	@Override
	protected boolean onAppendBase(int type, byte[] data, PackedObjectInfo object) {
		// External bases resolve input deltas, but are not received/output entries.
		return false;
	}

	@Override
	protected void onEndThinPack() {
		// The input is never installed; boundary expansion avoids thin-pack repair.
	}

	private void releaseParserResources(Throwable failure) {
		try {
			releaseResources();
		} catch (RuntimeException | Error cleanup) {
			if (failure == null) {
				throw cleanup;
			}
			failure.addSuppressed(cleanup);
		}
	}

	@Override
	public void close() throws IOException {
		if (closed) {
			return;
		}
		closed = true;
		Throwable closeFailure = null;
		try (IncomingPackSpool archive = spool;
				RoutedPackWriter output = writer;
				ObjectDirectoryInserter destination = loose) {
			// Only abort operation-owned resources; never publish or close databases.
		} catch (IOException | RuntimeException | Error failure) {
			closeFailure = failure;
			throw failure;
		} finally {
			spool = null;
			writer = null;
			loose = null;
			releaseParserResources(closeFailure);
		}
	}
}

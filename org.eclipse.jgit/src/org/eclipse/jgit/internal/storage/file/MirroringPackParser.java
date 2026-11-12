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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.transport.PackLock;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.eclipse.jgit.transport.ReceivedObjectFilter;

/** Normal pack import followed by forced loose copies of selected entries. */
class MirroringPackParser extends ObjectDirectoryPackParser {
	private record ReceivedObject(ObjectId id, int type, long size,
			boolean selected) {
	}

	private final ObjectDirectory main;

	private final ObjectDirectory alternate;

	private final ReceivedObjectFilter filter;

	private final Map<ObjectId, ReceivedObject> received = new LinkedHashMap<>();

	MirroringPackParser(ObjectDirectory main, ObjectDirectory alternate,
			ReceivedObjectFilter filter, InputStream in) {
		super(main, in);
		this.main = main;
		this.alternate = alternate;
		this.filter = filter;
	}

	@Override
	protected void onEndWholeObject(PackedObjectInfo info) throws IOException {
		super.onEndWholeObject(info);
		record(info);
	}

	@Override
	protected void onInflatedObjectData(PackedObjectInfo info, int type,
			byte[] data) throws IOException {
		super.onInflatedObjectData(info, type, data);
		record(info);
	}

	private void record(PackedObjectInfo info) throws IOException {
		ObjectId id = info.copy();
		ReceivedObject previous = received.get(id);
		if (previous != null) {
			if (previous.type() != info.getType()
					|| previous.size() != info.getFullSize()) {
				throw new IOException(MessageFormat.format(
						JGitText.get().receivedObjectMetadataMismatch, id.name()));
			}
		} else {
			received.put(id, new ReceivedObject(id, info.getType(),
					info.getFullSize(), filter.select(id, info.getType(),
							info.getFullSize())));
		}
	}

	@Override
	public PackLock parse(ProgressMonitor receiving, ProgressMonitor resolving)
			throws IOException {
		PackLock lock = super.parse(receiving, resolving);
		try {
			copySelected(receiving, resolving);
			return lock;
		} catch (IOException | RuntimeException | Error failure) {
			if (lock != null) {
				try {
					lock.unlock();
				} catch (IOException cleanupFailure) {
					failure.addSuppressed(cleanupFailure);
				}
			}
			throw failure;
		}
	}

	private void copySelected(ProgressMonitor receiving,
			ProgressMonitor resolving) throws IOException {
		try (ObjectReader reader = main.newReader();
				ObjectDirectoryInserter inserter = alternate.newInserter()) {
			for (ReceivedObject object : received.values()) {
				if (!object.selected()) {
					continue;
				}
				checkCancelled(receiving, resolving);
				ObjectLoader loader = reader.open(object.id(), object.type());
				try (InputStream stream = new FilterInputStream(loader.openStream()) {
					@Override
					public int read() throws IOException {
						checkCancelled(receiving, resolving);
						return super.read();
					}

					@Override
					public int read(byte[] b, int off, int len) throws IOException {
						checkCancelled(receiving, resolving);
						return super.read(b, off, len);
					}
				}) {
					ObjectId copied = inserter.insert(object.type(), object.size(),
							stream, true);
					if (!object.id().equals(copied)) {
						throw new IOException(MessageFormat.format(
								JGitText.get().receivedObjectIdMismatch,
								object.id().name(), copied.name()));
					}
				}
			}
			checkCancelled(receiving, resolving);
			inserter.flush();
		}
	}

	private static void checkCancelled(ProgressMonitor receiving,
			ProgressMonitor resolving) throws IOException {
		if ((receiving != null && receiving.isCancelled())
				|| (resolving != null && resolving.isCancelled())) {
			throw new IOException(JGitText.get().downloadCancelled);
		}
	}
}

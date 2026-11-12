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

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.transport.ReceivedObjectFilter;

/** Receive-only inserter; the caller owns both object databases. */
final class MirroringObjectInserter extends ObjectInserter {
	private final ObjectDirectory main;

	private final ObjectDirectory alternate;

	private final ReceivedObjectFilter filter;

	MirroringObjectInserter(ObjectDirectory main, ObjectDirectory alternate,
			ReceivedObjectFilter filter) {
		this.main = main;
		this.alternate = alternate;
		this.filter = filter;
	}

	@Override
	public ObjectId insert(int type, long length, InputStream in) {
		throw new UnsupportedOperationException();
	}

	@Override
	public PackParser newPackParser(InputStream in) throws IOException {
		return new MirroringPackParser(main, alternate, filter, in);
	}

	@Override
	public ObjectReader newReader() {
		ObjectReader reader = main.newReader();
		return new ObjectReader.Filter() {
			@Override
			protected ObjectReader delegate() {
				return reader;
			}

			@Override
			public ObjectInserter getCreatedFromInserter() {
				return MirroringObjectInserter.this;
			}
		};
	}

	@Override
	public void flush() {
		// Parsing imports the main pack and flushes the loose copies.
	}

	@Override
	public void close() {
		// All operation-owned handles are scoped to parse().
	}
}

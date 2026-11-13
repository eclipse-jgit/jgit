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
import java.io.UncheckedIOException;
import java.util.Objects;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.transport.ReceivedObjectFilter;

/** Receive-only inserter; the caller owns both databases and returned pack locks. */
final class RoutingObjectInserter extends ObjectInserter {
	private final ObjectDirectory main;

	private final ObjectDatabase alternate;

	private final ReceivedObjectFilter filter;

	private RoutingPackParser parser;

	private boolean closed;

	RoutingObjectInserter(ObjectDirectory main, ObjectDatabase alternate,
			ReceivedObjectFilter filter) {
		this.main = main;
		this.alternate = alternate;
		this.filter = filter;
	}

	@Override
	public ObjectId insert(int type, long size, InputStream data) {
		throw new UnsupportedOperationException();
	}

	@Override
	public PackParser newPackParser(InputStream input) {
		checkOpen();
		Objects.requireNonNull(input, "input"); //$NON-NLS-1$
		if (parser != null) {
			throw new IllegalStateException();
		}
		parser = new RoutingPackParser(main, alternate, filter, input);
		return parser;
	}

	@Override
	public ObjectReader newReader() {
		checkOpen();
		ObjectReader reader = main.newReader();
		return new ObjectReader.Filter() {
			@Override
			protected ObjectReader delegate() {
				return reader;
			}

			@Override
			public ObjectInserter getCreatedFromInserter() {
				return RoutingObjectInserter.this;
			}
		};
	}

	@Override
	public void flush() {
		// Parsing completes alternate flush and main publication. Repeated
		// flushes never publish a second pack or flush caller-owned databases.
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		if (parser != null) {
			try {
				parser.close();
			} catch (IOException failure) {
				throw new UncheckedIOException(failure);
			}
		}
	}

	private void checkOpen() {
		if (closed) {
			throw new IllegalStateException(JGitText.get().closed);
		}
	}
}

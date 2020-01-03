/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.IOException;

import org.eclipse.jgit.internal.storage.pack.StoredObjectRepresentation;
import org.eclipse.jgit.lib.ObjectId;

class LocalObjectRepresentation extends StoredObjectRepresentation {
	static LocalObjectRepresentation newWhole(PackFile f, long p, long length) {
		LocalObjectRepresentation r = new LocalObjectRepresentation() {
			@Override
			public int getFormat() {
				return PACK_WHOLE;
			}
		};
		r.pack = f;
		r.offset = p;
		r.length = length;
		return r;
	}

	static LocalObjectRepresentation newDelta(PackFile f, long p, long n,
			ObjectId base) {
		LocalObjectRepresentation r = new Delta();
		r.pack = f;
		r.offset = p;
		r.length = n;
		r.baseId = base;
		return r;
	}

	static LocalObjectRepresentation newDelta(PackFile f, long p, long n,
			long base) {
		LocalObjectRepresentation r = new Delta();
		r.pack = f;
		r.offset = p;
		r.length = n;
		r.baseOffset = base;
		return r;
	}

	PackFile pack;

	long offset;

	long length;

	private long baseOffset;

	private ObjectId baseId;

	/** {@inheritDoc} */
	@Override
	public int getWeight() {
		return (int) Math.min(length, Integer.MAX_VALUE);
	}

	/** {@inheritDoc} */
	@Override
	public ObjectId getDeltaBase() {
		if (baseId == null && getFormat() == PACK_DELTA) {
			try {
				baseId = pack.findObjectForOffset(baseOffset);
			} catch (IOException error) {
				return null;
			}
		}
		return baseId;
	}

	private static final class Delta extends LocalObjectRepresentation {
		@Override
		public int getFormat() {
			return PACK_DELTA;
		}
	}
}

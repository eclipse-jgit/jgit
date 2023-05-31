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
	static LocalObjectRepresentation newWhole(Pack pack, long offset, long length) {
		LocalObjectRepresentation r = new LocalObjectRepresentation() {
			@Override
			public int getFormat() {
				return PACK_WHOLE;
			}
		};
		r.pack = pack;
		r.offset = offset;
		r.length = length;
		return r;
	}

	static LocalObjectRepresentation newDelta(Pack pack, long offset, long length,
			ObjectId base) {
		LocalObjectRepresentation r = new Delta();
		r.pack = pack;
		r.offset = offset;
		r.length = length;
		r.baseId = base;
		return r;
	}

	static LocalObjectRepresentation newDelta(Pack pack, long offset, long length,
			long base) {
		LocalObjectRepresentation r = new Delta();
		r.pack = pack;
		r.offset = offset;
		r.length = length;
		r.baseOffset = base;
		return r;
	}

	Pack pack;

	long offset;

	long length;

	private long baseOffset;

	private ObjectId baseId;

	@Override
	public int getWeight() {
		return (int) Math.min(length, Integer.MAX_VALUE);
	}

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

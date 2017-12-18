/*
 * Copyright (C) 2010, Google Inc.
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

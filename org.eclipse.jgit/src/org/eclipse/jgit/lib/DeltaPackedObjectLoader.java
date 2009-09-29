/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.lib;

import java.io.IOException;
import java.util.zip.DataFormatException;

import org.eclipse.jgit.errors.CorruptObjectException;

/** Reader for a deltified object stored in a pack file. */
abstract class DeltaPackedObjectLoader extends PackedObjectLoader {
	private static final int OBJ_COMMIT = Constants.OBJ_COMMIT;

	private final int deltaSize;

	DeltaPackedObjectLoader(final PackFile pr, final long dataOffset,
			final long objectOffset, final int deltaSz) {
		super(pr, dataOffset, objectOffset);
		objectType = -1;
		deltaSize = deltaSz;
	}

	@Override
	public void materialize(final WindowCursor curs) throws IOException {
		if (cachedBytes != null) {
			return;
		}

		if (objectType != OBJ_COMMIT) {
			final UnpackedObjectCache.Entry cache = pack.readCache(dataOffset);
			if (cache != null) {
				curs.release();
				objectType = cache.type;
				objectSize = cache.data.length;
				cachedBytes = cache.data;
				return;
			}
		}

		try {
			final PackedObjectLoader baseLoader = getBaseLoader(curs);
			baseLoader.materialize(curs);
			cachedBytes = BinaryDelta.apply(baseLoader.getCachedBytes(),
					pack.decompress(dataOffset, deltaSize, curs));
			curs.release();
			objectType = baseLoader.getType();
			objectSize = cachedBytes.length;
			if (objectType != OBJ_COMMIT)
				pack.saveCache(dataOffset, cachedBytes, objectType);
		} catch (DataFormatException dfe) {
			final CorruptObjectException coe;
			coe = new CorruptObjectException("Object at " + dataOffset + " in "
					+ pack.getPackFile() + " has bad zlib stream");
			coe.initCause(dfe);
			throw coe;
		}
	}

	@Override
	public long getRawSize() {
		return deltaSize;
	}

	/**
	 * @param curs
	 *            temporary thread storage during data access.
	 * @return the object loader for the base object
	 * @throws IOException
	 */
	protected abstract PackedObjectLoader getBaseLoader(WindowCursor curs)
			throws IOException;
}

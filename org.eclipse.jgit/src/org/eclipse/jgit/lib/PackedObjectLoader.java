/*
 * Copyright (C) 2009, Google Inc.
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

/**
 * Base class for a set of object loader classes for packed objects.
 */
abstract class PackedObjectLoader extends ObjectLoader {
	protected final PackFile pack;

	/** Position of the first byte of the object's header. */
	protected final long objectOffset;

	/** Bytes used to express the object header, including delta reference. */
	protected final int headerSize;

	protected int objectType;

	protected int objectSize;

	protected byte[] cachedBytes;

	PackedObjectLoader(final PackFile pr, final long objectOffset,
			final int headerSize) {
		pack = pr;
		this.objectOffset = objectOffset;
		this.headerSize = headerSize;
	}

	/**
	 * Force this object to be loaded into memory and pinned in this loader.
	 * <p>
	 * Once materialized, subsequent get operations for the following methods
	 * will always succeed without raising an exception, as all information is
	 * pinned in memory by this loader instance.
	 * <ul>
	 * <li>{@link #getType()}</li>
	 * <li>{@link #getSize()}</li>
	 * <li>{@link #getBytes()}, {@link #getCachedBytes}</li>
	 * <li>{@link #getRawSize()}</li>
	 * <li>{@link #getRawType()}</li>
	 * </ul>
	 *
	 * @param curs
	 *            temporary thread storage during data access.
	 * @throws IOException
	 *             the object cannot be read.
	 */
	public abstract void materialize(WindowCursor curs) throws IOException;

	public final int getType() {
		return objectType;
	}

	public final long getSize() {
		return objectSize;
	}

	@Override
	public final byte[] getCachedBytes() {
		return cachedBytes;
	}

	/**
	 * @return offset of object header within pack file
	 */
	public final long getObjectOffset() {
		return objectOffset;
	}

	/**
	 * @return true if this loader is capable of fast raw-data copying basing on
	 *         compressed data checksum; false if raw-data copying needs
	 *         uncompressing and compressing data
	 * @throws IOException
	 *             the index file format cannot be determined.
	 */
	public boolean supportsFastCopyRawData() throws IOException {
		return pack.supportsFastCopyRawData();
	}

	/**
	 * @return id of delta base object for this object representation. null if
	 *         object is not stored as delta.
	 * @throws IOException
	 *             when delta base cannot read.
	 */
	public abstract ObjectId getDeltaBase() throws IOException;
}

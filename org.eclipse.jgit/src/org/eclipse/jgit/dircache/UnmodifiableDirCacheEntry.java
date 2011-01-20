/*
 * Copyright (C) 2011, Jens Baumgart <jens.baumgart@sap.com>
 * Copyright (C) 2011, Christian Halstrick <christian.halstrick@sap.com>
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
package org.eclipse.jgit.dircache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.util.MutableInteger;

/**
 * This class implements an immutable and thus thread safe DirCacheEntry for
 * reading purposes.
 */
public class UnmodifiableDirCacheEntry extends DirCacheEntry {

	/**
	 * @param sharedInfo
	 * @param infoAt
	 * @param in
	 * @param md
	 * @throws IOException
	 */
	public UnmodifiableDirCacheEntry(final byte[] sharedInfo,
			final MutableInteger infoAt, final InputStream in,
			final MessageDigest md) throws IOException {
		super(sharedInfo, infoAt, in, md);
	}

	@Override
	public void copyMetaData(DirCacheEntry src) {
		throw new UnsupportedOperationException();
	}

	@Override
	void write(OutputStream os) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setAssumeValid(boolean assume) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setUpdateNeeded(boolean updateNeeded) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setFileMode(FileMode mode) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setLastModified(long when) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setLength(int sz) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setLength(long sz) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setObjectId(AnyObjectId id) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setObjectIdFromRaw(byte[] bs, int p) {
		throw new UnsupportedOperationException();
	}

}

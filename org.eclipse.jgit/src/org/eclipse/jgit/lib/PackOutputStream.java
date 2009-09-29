/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
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
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.zip.CRC32;

/** Custom output stream to support {@link PackWriter}. */
final class PackOutputStream extends OutputStream {
	private final OutputStream out;

	private final CRC32 crc = new CRC32();

	private final MessageDigest md = Constants.newMessageDigest();

	private long count;

	PackOutputStream(final OutputStream out) {
		this.out = out;
	}

	@Override
	public void write(final int b) throws IOException {
		out.write(b);
		crc.update(b);
		md.update((byte) b);
		count++;
	}

	@Override
	public void write(final byte[] b, final int off, final int len)
			throws IOException {
		out.write(b, off, len);
		crc.update(b, off, len);
		md.update(b, off, len);
		count += len;
	}

	@Override
	public void flush() throws IOException {
		out.flush();
	}

	/** @return total number of bytes written since stream start. */
	long length() {
		return count;
	}

	/** @return obtain the current CRC32 register. */
	int getCRC32() {
		return (int) crc.getValue();
	}

	/** Reinitialize the CRC32 register for a new region. */
	void resetCRC32() {
		crc.reset();
	}

	/** @return obtain the current SHA-1 digest. */
	byte[] getDigest() {
		return md.digest();
	}
}

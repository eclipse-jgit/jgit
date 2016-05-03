/*
 * Copyright (C) 2016, Christian Halstrick <christian.halstrick@sap.com>
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
package org.eclipse.jgit.lfs;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;

import org.eclipse.jgit.lfs.lib.LongObjectId;

/**
 *
 */
public class LfsPointer {
	/**
	 *
	 */
	public static final String VERSION = "https://git-lfs.github.com/spec/v1"; //$NON-NLS-1$

	/**
	 *
	 */
	public static final String OIDTYPE = "sha256"; //$NON-NLS-1$

	private LongObjectId oid;

	private long size;

	/**
	 * @param oid
	 * @param size
	 */
	public LfsPointer(LongObjectId oid, long size) {
		this.oid = oid;
		this.size = size;
	}

	/**
	 * @return @TODO
	 */
	public LongObjectId getOid() {
		return oid;
	}

	/**
	 * @return @TODO
	 */
	public long getSize() {
		return size;
	}

	/**
	 * @param out
	 */
	public void encode(OutputStream out) {
		try (PrintStream ps = new PrintStream(out)) {
			ps.print("version "); //$NON-NLS-1$
			ps.println(VERSION);
			ps.print("oid "); //$NON-NLS-1$
			ps.println(LongObjectId.toString(oid));
			ps.print("size "); //$NON-NLS-1$
			ps.println(size);
		}
	}

	/**
	 * @param in
	 * @param buffer
	 * @return @todo
	 * @throws IOException
	 */
	public static LfsParserResult parseLfsPointer(InputStream in, byte[] buffer)
			throws IOException {
		boolean v = false;
		LongObjectId id = null;
		long si = -1;
		LfsParserResult ret = new LfsParserResult();
		if ((ret.bufferSize = in.read(ret.buffer, 0,
				ret.buffer.length)) == -1) {
			return null;
		}
		try (BufferedReader br = new BufferedReader(new InputStreamReader(
				new ByteArrayInputStream(ret.buffer, 0, ret.bufferSize)))) {
			for (String s = br.readLine(); s != null; s = br.readLine()) {
				if (s.startsWith("#") || s.length() == 0) //$NON-NLS-1$
					continue;
				if (s.startsWith("version") && s.length() > 8 //$NON-NLS-1$
						&& s.substring(8).trim().equals(VERSION)) {
					v = true;
				} else if (s.startsWith("oid") && s.length() > 4) { //$NON-NLS-1$
					id = LongObjectId.fromString(s.substring(4).trim());
				} else if (s.startsWith("size") && s.length() > 5) { //$NON-NLS-1$
					si = Long.parseLong(s.substring(5).trim());
				} else {
					return null;
				}
			}
			if (v && id != null && si > -1) {
				ret.pointer = new LfsPointer(id, si);
			}
		}
		return ret;
	}
}

class LfsParserResult {
	public byte[] buffer = new byte[1024];

	public int bufferSize = -1;

	public LfsPointer pointer = null;

}

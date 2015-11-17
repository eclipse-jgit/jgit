/*
 * Copyright (C) 2010, 2013 Marc Strapetz <marc.strapetz@syntevo.com>
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

package org.eclipse.jgit.util.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * An input stream which canonicalizes EOLs bytes on the fly to '\n'.
 *
 * Optionally, a binary check on the first 8000 bytes is performed and in case
 * of binary files, canonicalization is turned off (for the complete file).
 *
 * @deprecated use {@link AutoLFInputStream} instead
 */
@Deprecated
public class EolCanonicalizingInputStream extends AutoLFInputStream {

	/**
	 * Creates a new InputStream, wrapping the specified stream
	 *
	 * @param in
	 *            raw input stream
	 * @param detectBinary
	 *            whether binaries should be detected
	 */
	public EolCanonicalizingInputStream(InputStream in, boolean detectBinary) {
		super(in, detectBinary);
	}

	/**
	 * Creates a new InputStream, wrapping the specified stream
	 *
	 * @param in
	 *            raw input stream
	 * @param detectBinary
	 *            whether binaries should be detected
	 * @param abortIfBinary
	 *            throw an IOException if the file is binary
	 */
	public EolCanonicalizingInputStream(InputStream in, boolean detectBinary,
			boolean abortIfBinary) {
		super(in, detectBinary, abortIfBinary);
	}

	/**
	 * A special exception thrown when {@link AutoLFInputStream} is told to
	 * throw an exception when attempting to read a binary file. The exception
	 * may be thrown at any stage during reading.
	 *
	 * @since 3.3
	 */
	public static class IsBinaryException extends IOException {
		private static final long serialVersionUID = 1L;

		IsBinaryException() {
			super();
		}
	}

}

/*
 * Copyright (C) 2010, Matthias Sohn <matthias.sohn@sap.com>
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
package org.eclipse.jgit.util.fs;

import java.io.File;

/**
 * Abstraction of file system access functions
 */
public abstract class FSAccess {

	private static boolean isNativeImplementation;

	/**
	 * The only instance
	 */
	public final static FSAccess INSTANCE;

	static {
		FSAccess fsa = null;
		try {
			System.loadLibrary("jgitnative");
			fsa = new FSAccessNative();
			isNativeImplementation = true;
			System.out.println("successfully loaded native implementation");
		} catch (IllegalArgumentException e) {
			isNativeImplementation = false;
		} catch (SecurityException e) {
			isNativeImplementation = false;
		}
		// fall back to (slower) Java implementation if native implementation is
		// not available
		if (fsa == null) {
			fsa = new FSAccessJava();
			System.out
					.println("failed loading native implementation, loading Java implementation");
		}
		INSTANCE = fsa;
	}

	/**
	 * @return <code>true</code> if native implementation could be loaded
	 */
	public static final boolean isNativeImplementation() {
		return isNativeImplementation;
	}

	/**
	 * The lstat() method obtains lstat information about the named file from
	 * the underlying file system
	 *
	 * @param file
	 *            the file lstat information shall be retrieved for
	 * @return lstat information about the named file
	 * @throws NoSuchFileException
	 *             lstat returned error ENOENT
	 * @throws NotDirectoryException
	 *             lstat returned ENOTDIR
	 */
	public abstract LStat lstat(File file) throws NoSuchFileException,
			NotDirectoryException;
}

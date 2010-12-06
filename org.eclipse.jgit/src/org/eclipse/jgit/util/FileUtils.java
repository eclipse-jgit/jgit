/*
 * Copyright (C) 2010, Google Inc.
 * Copyright (C) 2010, Matthias Sohn <matthias.sohn@sap.com>
 * Copyright (C) 2010, Jens Baumgart <jens.baumgart@sap.com>
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

package org.eclipse.jgit.util;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.JGitText;

/**
 * File Utilities
 */
public class FileUtils {

	/**
	 * Option to delete given {@code File}
	 */
	public static final int NONE = 0;

	/**
	 * Option to recursively delete given {@code File}
	 */
	public static final int RECURSIVE = 1;

	/**
	 * Option to retry deletion if not successful
	 */
	public static final int RETRY = 2;

	/**
	 * Delete file or empty folder
	 *
	 * @param f
	 *            {@code File} to be deleted
	 * @throws IOException
	 *             if deletion of {@code f} fails. This may occur if {@code f}
	 *             didn't exist when the method was called. This can therefore
	 *             cause IOExceptions during race conditions when multiple
	 *             concurrent threads all try to delete the same file.
	 */
	public static void delete(final File f) throws IOException {
		delete(f, NONE);
	}

	/**
	 * Delete file or folder
	 *
	 * @param f
	 *            {@code File} to be deleted
	 * @param options
	 *            deletion options, {@code RECURSIVE} for recursive deletion of
	 *            a subtree, {@code RETRY} to retry when deletion failed.
	 *            Retrying may help if the underlying file system doesn't allow
	 *            deletion of files being read by another thread.
	 * @throws IOException
	 *             if deletion of {@code f} fails. This may occur if {@code f}
	 *             didn't exist when the method was called. This can therefore
	 *             cause IOExceptions during race conditions when multiple
	 *             concurrent threads all try to delete the same file.
	 */
	public static void delete(final File f, int options) throws IOException {
		if ((options & RECURSIVE) != 0 && f.isDirectory()) {
			final File[] items = f.listFiles();
			if (items != null) {
				for (File c : items)
					delete(c, options);
			}
		}
		if (!f.delete()) {
			if ((options & RETRY) != 0 && f.exists()) {
				for (int i = 1; i < 10; i++) {
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						// ignore
					}
					if (f.delete())
						return;
				}
			}
			throw new IOException(MessageFormat.format(
					JGitText.get().deleteFileFailed, f.getAbsolutePath()));
		}
	}

}

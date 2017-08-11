/*
 * Copyright (C) 2017, Harald Weiner
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
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.internal.JGitText;

/***
 * Handler for dealing with <code>$GITDIR/.shallow</code>.<br/>
 * <br/>
 * A direct instantiation of this class will only serve as a stub. If a concrete
 * implementation is needed, you might be looking for
 * {@code org.eclipse.jgit.storage.file.FileBasedShallow}.
 *
 * @since 5.0
 */
public class RepositoryShallow {

	/***
	 * Shallow line format: <code>shallow &lt;commitId&gt;\n</code>
	 */
	public static final String PREFIX_SHALLOW = "shallow "; //$NON-NLS-1$

	/***
	 * Unshallow line format: <code>unshallow &lt;commit-SHA1-hash&gt;\n</code>
	 */
	public static final String PREFIX_UNSHALLOW = "unshallow "; //$NON-NLS-1$

	/***
	 * Length of a shallow or unshallow line
	 */
	protected static final int HASH_LENGTH = 40;

	/***
	 * Lock shallow file.<br/>
	 * <br/>
	 * Locking prevents concurrent changes by another thread. Do not forget to
	 * call unlock() to release the lock.
	 *
	 * @throws IOException
	 */
	public void lock() throws IOException {
		// do nothing!
	}

	/***
	 * Reads content of <code>$GITDIR/.shallow</code> into memory.
	 *
	 * @return A list of commit SHA1 hashes where each commit id references a
	 *         commit with truncated history.
	 * @throws IOException
	 */
	public List<ObjectId> read() throws IOException {
		return Collections.emptyList();
	}

	/***
	 * Parse String as shallow/unshallow line and add/remove commit-id if it
	 * is.<br/>
	 * <br/>
	 * Is able to support reading shallow/unshallow lines that are sent as
	 * server response to a client.
	 *
	 * @param line
	 *            either <code>shallow &lt;commitId&gt;\n</code> or
	 *            <code>unshallow &lt;commitId&gt;\n</code>
	 * @return True if parsing has been successful. Returns false if line is
	 *         empty..
	 * @throws IOException
	 * @see <a href=
	 *      "https://github.com/git/git/blob/master/Documentation/technical/pack-protocol.txt#L261"
	 *      >https://github.com/git/git/blob/master/Documentation/technical/pack-protocol.txt#L261</a>
	 */
	public boolean parseShallowUnshallowLine(final String line)
			throws IOException {
		final int length = line.length();
		if (length == 0) {
			return false;
		}
		if (line.startsWith(PREFIX_SHALLOW)) {
			convertStringToObjectId(line, PREFIX_SHALLOW.length(),
					PREFIX_SHALLOW.length() + HASH_LENGTH);
		} else if (line.startsWith(PREFIX_UNSHALLOW)) {
			convertStringToObjectId(line, PREFIX_UNSHALLOW.length(),
					PREFIX_UNSHALLOW.length() + HASH_LENGTH);
		} else {
			throw new RepositoryShallowException(
					JGitText.get().expectedShallowUnshallowGot, line);
		}
		return true;
	}

	/***
	 * Convert SHA1 string commit-id to <code>ObjectId</code>.
	 *
	 * @param string
	 * @param beginIndex
	 * @param endIndex
	 * @return result
	 * @throws IOException
	 */
	protected ObjectId convertStringToObjectId(final String string,
			int beginIndex, int endIndex) throws IOException {
		final String objectIdAsString = string.substring(beginIndex, endIndex);
		try {
			final ObjectId result = ObjectId.fromString(objectIdAsString);
			return result;
		} catch (IllegalArgumentException ex) {
			throw new IOException(
					MessageFormat.format(JGitText.get().badShallowLine,
							objectIdAsString));
		}
	}

	/***
	 * Unlock shallow file.<br/>
	 * <br/>
	 * Can be used to write in-memory content to <code>$GITDIR/.shallow</code>.
	 * If that fails, the lock will be released anyway.
	 *
	 * @param writeChanges
	 *            If set to true this will override file content of
	 *            $GITDIR/shallow with current in-memory copy.
	 *
	 * @throws IOException
	 */
	public void unlock(final boolean writeChanges) throws IOException {
		// do nothing!
	}

	/***
	 * Exception class for dealing with <code>$GITDIR/.shallow</code>
	 */
	protected class RepositoryShallowException extends IOException {

		/**
		 * serialVersionUID generated by Eclipse
		 */
		private static final long serialVersionUID = -6812015161109424512L;

		/***
		 * Constructor for exception
		 *
		 * @param message
		 */
		public RepositoryShallowException(final String message) {
			super(message);
		}

		/***
		 * Constructor for exception
		 *
		 * @param title
		 * @param argument
		 */
		public RepositoryShallowException(final String title,
				final String argument) {
			super(MessageFormat.format(title, argument));
		}

	}

}

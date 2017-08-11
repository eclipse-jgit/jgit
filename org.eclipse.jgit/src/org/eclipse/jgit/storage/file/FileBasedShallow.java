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

package org.eclipse.jgit.storage.file;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RepositoryShallow;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.IO;

/***
 * Implements handling of file <code>$GITDIR/shallow</code>.<br/>
 * <br/>
 * A GIT repository is considered to be shallow when the commit history has been
 * truncated at some point. Each shallow GIT repository contains a shallow-file.
 * This file contains commit-ids (SHA1 hashes) line-wise. Each commit which is
 * referenced from inside this file is shallow. That means that this commit has
 * no parent, because its parent has not been transmitted on purpose.
 *
 * @since 5.0
 */
public class FileBasedShallow extends RepositoryShallow {

	private final File shallowFile;
	private final LockFile shallowLockFile;

	private final List<ObjectId> commitIds;

	/***
	 * Constructor.<br/>
	 * <br/>
	 * No reading or locking is done here.
	 *
	 * @param parentDirectory
	 *            $GITDIR meta-data directory
	 */
	public FileBasedShallow(final File parentDirectory) {
		this.commitIds = new ArrayList<>();
		if (parentDirectory == null) {
			// should never get here
			throw new RuntimeException(
					JGitText.get().repositoryLocalMetadataDirectoryInvalid);
		}
		shallowFile = new File(parentDirectory, Constants.SHALLOW);
		shallowLockFile = new LockFile(shallowFile);
	}

	@Override
	public void lock() throws IOException {
		if (!shallowLockFile.lock()) {
			final String path = shallowFile.getAbsolutePath();
			throw new RepositoryShallowException(JGitText.get().cannotLock,
					path);
		}
	}

	@Override
	public List<ObjectId> read() throws IOException {
		commitIds.clear();
		if (!shallowFile.exists()) {
			// file does not exist which means no shallow commits and,
			// therefore, nothing to do here
			return Collections.emptyList();
		}
		final FileReader in = new FileReader(shallowFile);
		try {
			for (String line;;) {
				// last char is for new-line
				line = IO.readLine(in, HASH_LENGTH + 1);
				if (line.length() == 0) {
					break;
				}
				final ObjectId id = convertStringToObjectId(line, 0,
						HASH_LENGTH);
				commitIds.add(id);
			}
		} finally {
			in.close();
		}
		return Collections.unmodifiableList(commitIds);
	}

	@Override
	public boolean parseShallowUnshallowLine(final String line)
			throws IOException {
		final int length = line.length();
		if (length == 0) {
			return false;
		}
		if (line.startsWith(PREFIX_SHALLOW)) {
			final ObjectId objId = convertStringToObjectId(line,
					PREFIX_SHALLOW.length(), PREFIX_SHALLOW.length() + 40);
			commitIds.add(objId);
		} else if (line.startsWith(PREFIX_UNSHALLOW)) {
			final ObjectId objId = convertStringToObjectId(line,
					PREFIX_UNSHALLOW.length(), PREFIX_UNSHALLOW.length() + 40);
			commitIds.remove(objId);
		} else {
			throw new RepositoryShallowException(
					JGitText.get().expectedShallowUnshallowGot, line);
		}
		return true;
	}

	@Override
	public void unlock(final boolean writeChanges) throws IOException {
		if (writeChanges) {
			try {
				writeChanges();
			} catch (IOException ex) {
				ex.printStackTrace();
			} finally {
				shallowLockFile.unlock();
			}
		} else {
			shallowLockFile.unlock();
		}
	}

	private void writeChanges() throws IOException {
		if (commitIds.isEmpty()) {
			if (shallowFile.exists()) {
				// no shallow commits, so whole repository
				// is not shallow anymore
				FileUtils.delete(shallowFile);
				shallowLockFile.unlock();
			}
			return;
		}
		final StringBuffer buffer = new StringBuffer();
		Collections.sort(commitIds);
		for (ObjectId id : commitIds) {
			// shallowLockFile.write(id); is not able to write more than a
			// single id!!!
			buffer.append(id.name());
			buffer.append('\n');
		}
		final String contentAsString = buffer.toString();
		final byte[] content = contentAsString.getBytes();
		shallowLockFile.write(content);
		shallowLockFile.commit();
	}

}

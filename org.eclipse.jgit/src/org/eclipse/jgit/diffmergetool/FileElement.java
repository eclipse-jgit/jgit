/*
 * Copyright (C) 2018-2019, Andre Bossert <andre.bossert@siemens.com>
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

package org.eclipse.jgit.diffmergetool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectStream;

/**
 * The element used as left or right file for compare.
 *
 */
public class FileElement {

	private final String path;

	private final String id;

	private ObjectStream stream;

	private File tempFile;

	/**
	 * @param path
	 *            the file path
	 * @param id
	 *            the file id
	 */
	public FileElement(final String path, final String id) {
		this(path, id, null);
	}

	/**
	 * @param path
	 *            the file path
	 * @param id
	 *            the file id
	 * @param stream
	 *            the object stream to load instead of file
	 */
	public FileElement(final String path, final String id,
			ObjectStream stream) {
		this.path = path;
		this.id = id;
		this.stream = stream;
	}

	/**
	 * @return the file path
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @return the file id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @return the object stream
	 */
	public ObjectStream getStream() {
		return stream;
	}

	/**
	 * @param stream
	 *            the object stream
	 */
	public void setStream(ObjectStream stream) {
		this.stream = stream;
	}

	/**
	 * @param workingDir the working directory used if file cannot be found (e.g. /dev/null)
	 * @return the object stream
	 * @throws IOException
	 */
	public File getFile(File workingDir) throws IOException {
		File file = new File(path);
		String name = file.getName();
		if (path.equals(DiffEntry.DEV_NULL)) { 
			file = new File(workingDir, "nul"); //$NON-NLS-1$
		}
		else if ((stream != null)) {
			tempFile = File.createTempFile(".__", "__" + name); //$NON-NLS-1$ //$NON-NLS-2$
			try (OutputStream outStream = new FileOutputStream(tempFile)) {
				int read = 0;
				byte[] bytes = new byte[8 * 1024];
				while ((read = stream.read(bytes)) != -1) {
					outStream.write(bytes, 0, read);
				}
			}
			return tempFile;
		}
		return file;
	}

	/**
	 * Deletes temporary file if necessary
	 */
	public void cleanTemporaries() {
		if (tempFile != null && tempFile.exists())
		tempFile.delete();
	}

}

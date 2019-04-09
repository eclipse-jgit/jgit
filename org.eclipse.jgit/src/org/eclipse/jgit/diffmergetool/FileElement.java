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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import org.eclipse.jgit.diff.DiffEntry;

/**
 * The element used as left or right file for compare.
 *
 * @since 5.4
 *
 */
public class FileElement {

	/**
	 * The file element type.
	 *
	 */
	public enum Type {
		/**
		 * The local file element (ours).
		 */
		LOCAL,
		/**
		 * The remote file element (theirs).
		 */
		REMOTE,
		/**
		 * The merged file element (path in worktree).
		 */
		MERGED,
		/**
		 * The base file element (of ours and theirs).
		 */
		BASE,
		/**
		 * The backup file element (copy of merged / conflicted).
		 */
		BACKUP();
	}

	private final String path;

	private final Type type;

	private final File workDir;

	private InputStream stream;

	private File tempFile;

	/**
	 * @param path
	 *            the file path
	 * @param type
	 *            the element type
	 */
	public FileElement(final String path, final Type type) {
		this(path, type, null);
	}

	/**
	 * @param path
	 *            the file path
	 * @param type
	 *            the element type
	 * @param workDir
	 *            the working directory of the path (can be null, then current
	 *            working dir is used)
	 */
	public FileElement(final String path, final Type type, final File workDir) {
		this(path, type, workDir, null, null);
	}

	/**
	 * @param path
	 *            the file path
	 * @param type
	 *            the element type
	 * @param workDir
	 *            the working directory of the path (can be null, then current
	 *            working dir is used)
	 * @param tempFile
	 *            the temporary file to be used (can be null and will be created
	 *            then)
	 * @param stream
	 *            the object stream to load and write on demand, @see getFile(),
	 *            to tempFile once (can be null)
	 */
	public FileElement(final String path, final Type type, final File workDir,
			final File tempFile, InputStream stream) {
		this.path = path;
		this.type = type;
		this.workDir = workDir;
		this.tempFile = tempFile;
		this.stream = stream;
	}

	/**
	 * @return the file path
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @return the element type
	 */
	public Type getType() {
		return type;
	}

	/**
	 * Returns
	 * <ul>
	 * <li>a temporary file if already created and stream is not valid</li>
	 * <li>OR a real file from work tree: if no temp file was created (@see
	 * createTempFile()) and if no stream was set</li>
	 * <li>OR an empty temporary file if path is "/dev/null"</li>
	 * <li>OR a temporary file with stream content if stream is valid (not
	 * null); stream is closed and invalidated (set to null) after write to temp
	 * file, so stream is used only once during first call!</li>
	 * </ul>
	 *
	 * @return the object stream
	 * @throws IOException
	 */
	public File getFile() throws IOException {
		// if we have already temp file and no stream
		// then just return this temp file (it was filled from outside)
		if ((tempFile != null) && (stream == null)) {
			return tempFile;
		}
		File file = new File(workDir, path);
		// if we have a stream or file is missing (path is "/dev/null")
		// then optionally create temporary file and fill it with stream content
		if ((stream != null) || isNullPath()) {
			if (tempFile == null) {
				tempFile = getTempFile(file, type.name(), null);
			}
			if (stream != null) {
				copyFromStream(tempFile, stream);
			}
			// invalidate the stream, because it is used once
			stream = null;
			return tempFile;
		}
		return file;
	}

	/**
	 * @return true if path is "/dev/null"
	 */
	public boolean isNullPath() {
		return path.equals(DiffEntry.DEV_NULL);
	}

	/**
	 * @param workingDir
	 *            the working directory for the file
	 * @return temporary file in working directory or in the system temporary
	 *         directory
	 * @throws IOException
	 */
	public File createTempFile(final File workingDir) throws IOException {
		if (tempFile == null) {
			tempFile = getTempFile(new File(path), type.name(), workingDir);
		}
		return tempFile;
	}

	/**
	 * Deletes and invalidates temporary file if necessary.
	 */
	public void cleanTemporaries() {
		if (tempFile != null && tempFile.exists()) {
			tempFile.delete();
		}
		tempFile = null;
	}

	/**
	 * @param input
	 *            the input string
	 * @return the replaced input string
	 * @throws IOException
	 */
	public String replaceVariable(String input) throws IOException {
		return input.replace("$" + type.name(), getFile().getPath()); //$NON-NLS-1$
	}

	/**
	 * @param env
	 *            the environment where this element should be added
	 * @throws IOException
	 */
	public void addToEnv(Map<String, String> env) throws IOException {
		env.put(type.name(), getFile().getPath());
	}

	private static File getTempFile(final File file, final String midName,
			final File workingDir) throws IOException {
		String[] fileNameAndExtension = splitBaseFileNameAndExtension(file);
		// TODO: avoid long random file name (number generated by
		// createTempFile)
		return File.createTempFile(
				fileNameAndExtension[0] + "_" + midName + "_", //$NON-NLS-1$ //$NON-NLS-2$
				fileNameAndExtension[1], workingDir);
	}

	private static void copyFromStream(final File file,
			final InputStream stream)
			throws IOException, FileNotFoundException {
		try (OutputStream outStream = new FileOutputStream(file)) {
			int read = 0;
			byte[] bytes = new byte[8 * 1024];
			while ((read = stream.read(bytes)) != -1) {
				outStream.write(bytes, 0, read);
			}
		} finally {
			// stream can only be consumed once --> close it and invalidate
			stream.close();
		}
	}

	private static String[] splitBaseFileNameAndExtension(File file) {
		String[] result = new String[2];
		result[0] = file.getName();
		result[1] = ""; //$NON-NLS-1$
		int idx = result[0].lastIndexOf("."); //$NON-NLS-1$
		// if "." was found (>-1) and last-index is not first char (>0), then
		// split (same behavior like cgit)
		if (idx > 0) {
			result[1] = result[0].substring(idx, result[0].length());
			result[0] = result[0].substring(0, idx);
		}
		return result;
	}

}

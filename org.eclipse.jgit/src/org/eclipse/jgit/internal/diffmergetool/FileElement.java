/*
 * Copyright (C) 2018-2021, Andre Bossert <andre.bossert@siemens.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.diffmergetool;

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

	private InputStream stream;

	private File tempFile;

	/**
	 * Creates file element for path.
	 *
	 * @param path
	 *            the file path
	 * @param type
	 *            the element type
	 */
	public FileElement(String path, Type type) {
		this(path, type, null, null);
	}

	/**
	 * Creates file element for path.
	 *
	 * @param path
	 *            the file path
	 * @param type
	 *            the element type
	 * @param tempFile
	 *            the temporary file to be used (can be null and will be created
	 *            then)
	 * @param stream
	 *            the object stream to load instead of file
	 */
	public FileElement(String path, Type type, File tempFile,
			InputStream stream) {
		this.path = path;
		this.type = type;
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
	 * Return a temporary file within passed directory and fills it with stream
	 * if valid.
	 *
	 * @param directory
	 *            the directory where the temporary file is created
	 * @param midName
	 *            name added in the middle of generated temporary file name
	 * @return the object stream
	 * @throws IOException
	 */
	public File getFile(File directory, String midName) throws IOException {
		if ((tempFile != null) && (stream == null)) {
			return tempFile;
		}
		tempFile = getTempFile(path, directory, midName);
		return copyFromStream(tempFile, stream);
	}

	/**
	 * Return a real file from work tree or a temporary file with content if
	 * stream is valid or if path is "/dev/null"
	 *
	 * @return the object stream
	 * @throws IOException
	 */
	public File getFile() throws IOException {
		if ((tempFile != null) && (stream == null)) {
			return tempFile;
		}
		File file = new File(path);
		// if we have a stream or file is missing ("/dev/null") then create
		// temporary file
		if ((stream != null) || isNullPath()) {
			tempFile = getTempFile(file);
			return copyFromStream(tempFile, stream);
		}
		return file;
	}

	/**
	 * Check if path id "/dev/null"
	 *
	 * @return true if path is "/dev/null"
	 */
	public boolean isNullPath() {
		return path.equals(DiffEntry.DEV_NULL);
	}

	/**
	 * Create temporary file in given or system temporary directory
	 *
	 * @param directory
	 *            the directory for the file (can be null); if null system
	 *            temporary directory is used
	 * @return temporary file in directory or in the system temporary directory
	 * @throws IOException
	 */
	public File createTempFile(File directory) throws IOException {
		if (tempFile == null) {
			File file = new File(path);
			if (directory != null) {
				tempFile = getTempFile(file, directory, type.name());
			} else {
				tempFile = getTempFile(file);
			}
		}
		return tempFile;
	}

	private static File getTempFile(File file) throws IOException {
		return File.createTempFile(".__", "__" + file.getName()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static File getTempFile(File file, File directory, String midName)
			throws IOException {
		String[] fileNameAndExtension = splitBaseFileNameAndExtension(file);
		return File.createTempFile(
				fileNameAndExtension[0] + "_" + midName + "_", //$NON-NLS-1$ //$NON-NLS-2$
				fileNameAndExtension[1], directory);
	}

	private static File getTempFile(String path, File directory, String midName)
			throws IOException {
		return getTempFile(new File(path), directory, midName);
	}

	/**
	 * Delete and invalidate temporary file if necessary.
	 */
	public void cleanTemporaries() {
		if (tempFile != null && tempFile.exists())
		tempFile.delete();
		tempFile = null;
	}

	private static File copyFromStream(File file, final InputStream stream)
			throws IOException, FileNotFoundException {
		if (stream != null) {
			try (OutputStream outStream = new FileOutputStream(file)) {
				int read = 0;
				byte[] bytes = new byte[8 * 1024];
				while ((read = stream.read(bytes)) != -1) {
					outStream.write(bytes, 0, read);
				}
			} finally {
				// stream can only be consumed once --> close it
				stream.close();
			}
		}
		return file;
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

	/**
	 * Replace variable in input
	 *
	 * @param input
	 *            the input string
	 * @return the replaced input string
	 * @throws IOException
	 */
	public String replaceVariable(String input) throws IOException {
		return input.replace("$" + type.name(), getFile().getPath()); //$NON-NLS-1$
	}

	/**
	 * Add variable to environment map.
	 *
	 * @param env
	 *            the environment where this element should be added
	 * @throws IOException
	 */
	public void addToEnv(Map<String, String> env) throws IOException {
		env.put(type.name(), getFile().getPath());
	}

}

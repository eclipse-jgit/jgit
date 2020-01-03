/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * An interface providing FTP operations over a {@link RemoteSession}. All
 * operations are supposed to throw {@link FtpException} for remote file system
 * errors and other IOExceptions on connection errors.
 *
 * @since 5.2
 */
public interface FtpChannel {

	/**
	 * An {@link Exception} for reporting SFTP errors.
	 */
	static class FtpException extends IOException {

		private static final long serialVersionUID = 7176525179280330876L;

		public static final int OK = 0;

		public static final int EOF = 1;

		public static final int NO_SUCH_FILE = 2;

		public static final int NO_PERMISSION = 3;

		public static final int UNSPECIFIED_FAILURE = 4;

		public static final int PROTOCOL_ERROR = 5;

		public static final int UNSUPPORTED = 8;

		private final int status;

		public FtpException(String message, int status) {
			super(message);
			this.status = status;
		}

		public FtpException(String message, int status, Throwable cause) {
			super(message, cause);
			this.status = status;
		}

		public int getStatus() {
			return status;
		}
	}

	/**
	 * Connects the {@link FtpChannel} to the remote end.
	 *
	 * @param timeout
	 *            for establishing the FTP connection
	 * @param unit
	 *            of the {@code timeout}
	 * @throws IOException
	 */
	void connect(int timeout, TimeUnit unit) throws IOException;

	/**
	 * Disconnects and {@link FtpChannel}.
	 */
	void disconnect();

	/**
	 * @return whether the {@link FtpChannel} is connected
	 */
	boolean isConnected();

	/**
	 * Changes the current remote directory.
	 *
	 * @param path
	 *            target directory
	 * @throws IOException
	 *             if the operation could not be performed remotely
	 */
	void cd(String path) throws IOException;

	/**
	 * @return the current remote directory path
	 * @throws IOException
	 */
	String pwd() throws IOException;

	/**
	 * Simplified remote directory entry.
	 */
	interface DirEntry {
		String getFilename();

		long getModifiedTime();

		boolean isDirectory();
	}

	/**
	 * Lists contents of a remote directory
	 *
	 * @param path
	 *            of the directory to list
	 * @return the directory entries
	 * @throws IOException
	 */
	Collection<DirEntry> ls(String path) throws IOException;

	/**
	 * Deletes a directory on the remote file system. The directory must be
	 * empty.
	 *
	 * @param path
	 *            to delete
	 * @throws IOException
	 */
	void rmdir(String path) throws IOException;

	/**
	 * Creates a directory on the remote file system.
	 *
	 * @param path
	 *            to create
	 * @throws IOException
	 */
	void mkdir(String path) throws IOException;

	/**
	 * Obtain an {@link InputStream} to read the contents of a remote file.
	 *
	 * @param path
	 *            of the file to read
	 *
	 * @return the stream to read from
	 * @throws IOException
	 */
	InputStream get(String path) throws IOException;

	/**
	 * Obtain an {@link OutputStream} to write to a remote file. If the file
	 * exists already, it will be overwritten.
	 *
	 * @param path
	 *            of the file to read
	 *
	 * @return the stream to read from
	 * @throws IOException
	 */
	OutputStream put(String path) throws IOException;

	/**
	 * Deletes a file on the remote file system.
	 *
	 * @param path
	 *            to delete
	 * @throws IOException
	 *             if the file does not exist or could otherwise not be deleted
	 */
	void rm(String path) throws IOException;

	/**
	 * Deletes a file on the remote file system. If the file does not exist, no
	 * exception is thrown.
	 *
	 * @param path
	 *            to delete
	 * @throws IOException
	 *             if the file exist but could not be deleted
	 */
	default void delete(String path) throws IOException {
		try {
			rm(path);
		} catch (FileNotFoundException e) {
			// Ignore; it's OK if the file doesn't exist
		} catch (FtpException f) {
			if (f.getStatus() == FtpException.NO_SUCH_FILE) {
				return;
			}
			throw f;
		}
	}

	/**
	 * Renames a file on the remote file system. If {@code to} exists, it is
	 * replaced by {@code from}. (POSIX rename() semantics)
	 *
	 * @param from
	 *            original name of the file
	 * @param to
	 *            new name of the file
	 * @throws IOException
	 * @see <a href=
	 *      "http://pubs.opengroup.org/onlinepubs/9699919799/functions/rename.html">stdio.h:
	 *      rename()</a>
	 */
	void rename(String from, String to) throws IOException;

}

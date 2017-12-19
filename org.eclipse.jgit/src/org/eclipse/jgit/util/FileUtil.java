/*
 * Copyright (C) 2012, Robin Rosenberg <robin.rosenberg@dewire.com>
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

import org.eclipse.jgit.util.FS.Attributes;

/**
 * File utilities using Java 7 NIO2
 */
@Deprecated
public class FileUtil {

	/**
	 * Read target path of a symlink.
	 *
	 * @param path
	 *            a {@link java.io.File}.
	 * @return target path of the symlink.
	 * @throws java.io.IOException
	 * @deprecated use {@link org.eclipse.jgit.util.FileUtils#readSymLink(File)}
	 *             instead
	 */
	@Deprecated
	public static String readSymlink(File path) throws IOException {
		return FileUtils.readSymLink(path);
	}

	/**
	 * Create a symlink
	 *
	 * @param path
	 *            path of the symlink to be created
	 * @param target
	 *            target of the symlink to be created
	 * @throws java.io.IOException
	 * @deprecated use
	 *             {@link org.eclipse.jgit.util.FileUtils#createSymLink(File, String)}
	 *             instead
	 */
	@Deprecated
	public static void createSymLink(File path, String target)
			throws IOException {
		FileUtils.createSymLink(path, target);
	}

	/**
	 * Whether the passed file is a symlink
	 *
	 * @param path
	 *            a {@link java.io.File} object.
	 * @return {@code true} if the passed path is a symlink
	 * @deprecated Use
	 *             {@link java.nio.file.Files#isSymbolicLink(java.nio.file.Path)}
	 *             instead
	 */
	@Deprecated
	public static boolean isSymlink(File path) {
		return FileUtils.isSymlink(path);
	}

	/**
	 * Get lastModified attribute for given path
	 *
	 * @param path
	 *            a {@link java.io.File}.
	 * @return lastModified attribute for given path
	 * @throws java.io.IOException
	 * @deprecated Use
	 *             {@link java.nio.file.Files#getLastModifiedTime(java.nio.file.Path, java.nio.file.LinkOption...)}
	 *             instead
	 */
	@Deprecated
	public static long lastModified(File path) throws IOException {
		return FileUtils.lastModified(path);
	}

	/**
	 * Set lastModified attribute for given path
	 *
	 * @param path
	 *            a {@link java.io.File}.
	 * @param time
	 *            a long.
	 * @throws java.io.IOException
	 * @deprecated Use
	 *             {@link java.nio.file.Files#setLastModifiedTime(java.nio.file.Path, java.nio.file.attribute.FileTime)}
	 *             instead
	 */
	@Deprecated
	public static void setLastModified(File path, long time) throws IOException {
		FileUtils.setLastModified(path, time);
	}

	/**
	 * Whether this file exists
	 *
	 * @param path
	 *            a {@link java.io.File}.
	 * @return {@code true} if the given path exists
	 * @deprecated Use
	 *             {@link java.nio.file.Files#exists(java.nio.file.Path, java.nio.file.LinkOption...)}
	 *             instead
	 */
	@Deprecated
	public static boolean exists(File path) {
		return FileUtils.exists(path);
	}

	/**
	 * Whether this file is hidden
	 *
	 * @param path
	 *            a {@link java.io.File}.
	 * @return {@code true} if the given path is hidden
	 * @throws java.io.IOException
	 * @deprecated Use {@link java.nio.file.Files#isHidden(java.nio.file.Path)}
	 *             instead
	 */
	@Deprecated
	public static boolean isHidden(File path) throws IOException {
		return FileUtils.isHidden(path);
	}

	/**
	 * Set this file hidden
	 *
	 * @param path
	 *            a {@link java.io.File}.
	 * @param hidden
	 *            a boolean.
	 * @throws java.io.IOException
	 * @deprecated Use
	 *             {@link org.eclipse.jgit.util.FileUtils#setHidden(File,boolean)}
	 *             instead
	 */
	@Deprecated
	public static void setHidden(File path, boolean hidden) throws IOException {
		FileUtils.setHidden(path, hidden);
	}

	/**
	 * Get file length
	 *
	 * @param path
	 *            a {@link java.io.File}.
	 * @return length of the given file
	 * @throws java.io.IOException
	 * @deprecated Use {@link org.eclipse.jgit.util.FileUtils#getLength(File)}
	 *             instead
	 */
	@Deprecated
	public static long getLength(File path) throws IOException {
		return FileUtils.getLength(path);
	}

	/**
	 * Whether the given File is a directory
	 *
	 * @param path
	 *            a {@link java.io.File} object.
	 * @return {@code true} if the given file is a directory
	 * @deprecated Use
	 *             {@link java.nio.file.Files#isDirectory(java.nio.file.Path, java.nio.file.LinkOption...)}
	 *             instead
	 */
	@Deprecated
	public static boolean isDirectory(File path) {
		return FileUtils.isDirectory(path);
	}

	/**
	 * Whether the given File is a file
	 *
	 * @param path
	 *            a {@link java.io.File} object.
	 * @return {@code true} if the given file is a file
	 * @deprecated Use
	 *             {@link java.nio.file.Files#isRegularFile(java.nio.file.Path, java.nio.file.LinkOption...)}
	 *             instead
	 */
	@Deprecated
	public static boolean isFile(File path) {
		return FileUtils.isFile(path);
	}

	/**
	 * Whether the given file can be executed
	 *
	 * @param path
	 *            a {@link java.io.File} object.
	 * @return {@code true} if the given file can be executed
	 * @deprecated Use {@link org.eclipse.jgit.util.FileUtils#canExecute(File)}
	 *             instead
	 */
	@Deprecated
	public static boolean canExecute(File path) {
		return FileUtils.canExecute(path);
	}

	/**
	 * Delete the given file
	 *
	 * @param path
	 *            a {@link java.io.File} object.
	 * @throws java.io.IOException
	 * @deprecated use {@link org.eclipse.jgit.util.FileUtils#delete(File)}
	 */
	@Deprecated
	public static void delete(File path) throws IOException {
		FileUtils.delete(path);
	}

	/**
	 * Get file system attributes for the given file
	 *
	 * @param fs
	 *            a {@link org.eclipse.jgit.util.FS} object.
	 * @param path
	 *            a {@link java.io.File} object.
	 * @return file system attributes for the given file
	 * @deprecated Use
	 *             {@link org.eclipse.jgit.util.FileUtils#getFileAttributesPosix(FS,File)}
	 *             instead
	 */
	@Deprecated
	public static Attributes getFileAttributesPosix(FS fs, File path) {
		return FileUtils.getFileAttributesPosix(fs, path);
	}

	/**
	 * NFC normalize File (on Mac), otherwise do nothing
	 *
	 * @param file
	 *            a {@link java.io.File}.
	 * @return on Mac: NFC normalized {@link java.io.File}, otherwise the passed
	 *         file
	 * @deprecated Use {@link org.eclipse.jgit.util.FileUtils#normalize(File)}
	 *             instead
	 */
	@Deprecated
	public static File normalize(File file) {
		return FileUtils.normalize(file);
	}

	/**
	 * NFC normalize file name (on Mac), otherwise do nothing
	 *
	 * @param name
	 *            a {@link java.lang.String} object.
	 * @return on Mac: NFC normalized form of given name
	 * @deprecated Use {@link org.eclipse.jgit.util.FileUtils#normalize(String)}
	 *             instead
	 */
	@Deprecated
	public static String normalize(String name) {
		return FileUtils.normalize(name);
	}

}

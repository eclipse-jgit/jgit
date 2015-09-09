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
import java.nio.file.Files;

import org.eclipse.jgit.util.FS.Attributes;

/**
 * File utilities using Java 7 NIO2
 */
@Deprecated
public class FileUtil {

	/**
	 * @param path
	 * @return target path of the symlink
	 * @throws IOException
	 * @deprecated use {@link FileUtils#readSymLink(File)} instead
	 */
	@Deprecated
	public static String readSymlink(File path) throws IOException {
		return FileUtils.readSymLink(path);
	}

	/**
	 * @param path
	 *            path of the symlink to be created
	 * @param target
	 *            target of the symlink to be created
	 * @throws IOException
	 * @deprecated use {@link FileUtils#createSymLink(File, String)} instead
	 */
	@Deprecated
	public static void createSymLink(File path, String target)
			throws IOException {
		FileUtils.createSymLink(path, target);
	}

	/**
	 * @param path
	 * @return {@code true} if the passed path is a symlink
	 * @deprecated Use {@link Files#isSymbolicLink(java.nio.file.Path)} instead
	 */
	@Deprecated
	public static boolean isSymlink(File path) {
		return FileUtils.isSymlink(path);
	}

	/**
	 * @param path
	 * @return lastModified attribute for given path
	 * @throws IOException
	 * @deprecated Use
	 *             {@link Files#getLastModifiedTime(java.nio.file.Path, java.nio.file.LinkOption...)}
	 *             instead
	 */
	@Deprecated
	public static long lastModified(File path) throws IOException {
		return FileUtils.lastModified(path);
	}

	/**
	 * @param path
	 * @param time
	 * @throws IOException
	 * @deprecated Use
	 *             {@link Files#setLastModifiedTime(java.nio.file.Path, java.nio.file.attribute.FileTime)}
	 *             instead
	 */
	@Deprecated
	public static void setLastModified(File path, long time) throws IOException {
		FileUtils.setLastModified(path, time);
	}

	/**
	 * @param path
	 * @return {@code true} if the given path exists
	 * @deprecated Use
	 *             {@link Files#exists(java.nio.file.Path, java.nio.file.LinkOption...)}
	 *             instead
	 */
	@Deprecated
	public static boolean exists(File path) {
		return FileUtils.exists(path);
	}

	/**
	 * @param path
	 * @return {@code true} if the given path is hidden
	 * @throws IOException
	 * @deprecated Use {@link Files#isHidden(java.nio.file.Path)} instead
	 */
	@Deprecated
	public static boolean isHidden(File path) throws IOException {
		return FileUtils.isHidden(path);
	}

	/**
	 * @param path
	 * @param hidden
	 * @throws IOException
	 * @deprecated Use {@link FileUtils#setHidden(File,boolean)} instead
	 */
	@Deprecated
	public static void setHidden(File path, boolean hidden) throws IOException {
		FileUtils.setHidden(path, hidden);
	}

	/**
	 * @param path
	 * @return length of the given file
	 * @throws IOException
	 * @deprecated Use {@link FileUtils#getLength(File)} instead
	 */
	@Deprecated
	public static long getLength(File path) throws IOException {
		return FileUtils.getLength(path);
	}

	/**
	 * @param path
	 * @return {@code true} if the given file a directory
	 * @deprecated Use
	 *             {@link Files#isDirectory(java.nio.file.Path, java.nio.file.LinkOption...)}
	 *             instead
	 */
	@Deprecated
	public static boolean isDirectory(File path) {
		return FileUtils.isDirectory(path);
	}

	/**
	 * @param path
	 * @return {@code true} if the given file is a file
	 * @deprecated Use
	 *             {@link Files#isRegularFile(java.nio.file.Path, java.nio.file.LinkOption...)}
	 *             instead
	 */
	@Deprecated
	public static boolean isFile(File path) {
		return FileUtils.isFile(path);
	}

	/**
	 * @param path
	 * @return {@code true} if the given file can be executed
	 * @deprecated Use {@link FileUtils#canExecute(File)} instead
	 */
	@Deprecated
	public static boolean canExecute(File path) {
		return FileUtils.canExecute(path);
	}

	/**
	 * @param path
	 * @throws IOException
	 * @deprecated use {@link FileUtils#delete(File)}
	 */
	@Deprecated
	public static void delete(File path) throws IOException {
		FileUtils.delete(path);
	}

	/**
	 * @param fs
	 * @param path
	 * @return file system attributes for the given file
	 * @deprecated Use {@link FileUtils#getFileAttributesPosix(FS,File)} instead
	 */
	@Deprecated
	public static Attributes getFileAttributesPosix(FS fs, File path) {
		return FileUtils.getFileAttributesPosix(fs, path);
	}

	/**
	 * @param file
	 * @return on Mac: NFC normalized {@link File}, otherwise the passed file
	 * @deprecated Use {@link FileUtils#normalize(File)} instead
	 */
	@Deprecated
	public static File normalize(File file) {
		return FileUtils.normalize(file);
	}

	/**
	 * @param name
	 * @return on Mac: NFC normalized form of given name
	 * @deprecated Use {@link FileUtils#normalize(String)} instead
	 */
	@Deprecated
	public static String normalize(String name) {
		return FileUtils.normalize(name);
	}

}

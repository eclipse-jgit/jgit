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
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.text.Normalizer;
import java.text.Normalizer.Form;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.FS.Attributes;

class FileUtil {

	static class Java7PosixAttributes extends Attributes {

		Java7PosixAttributes(FS fs, File fPath, Path pPath,
				boolean exists,
				boolean isDirectory,
				boolean isExecutable,
				boolean isSymbolicLink, boolean isRegularFile,
				long creationTime, long lastModifiedTime, long length) {
			super(fs, fPath, exists, isDirectory, isExecutable, isSymbolicLink,
					isRegularFile, creationTime, lastModifiedTime, length);
		}
	}

	static String readSymlink(File path) throws IOException {
		Path nioPath = path.toPath();
		Path target = Files.readSymbolicLink(nioPath);
		String targetString = target.toString();
		if (SystemReader.getInstance().isWindows())
			targetString = targetString.replace('\\', '/');
		else if (SystemReader.getInstance().isMacOS())
			targetString = Normalizer.normalize(targetString, Form.NFC);
		return targetString;
	}

	public static void createSymLink(File path, String target)
			throws IOException {
		Path nioPath = path.toPath();
		if (Files.exists(nioPath, LinkOption.NOFOLLOW_LINKS))
			Files.delete(nioPath);
		if (SystemReader.getInstance().isWindows())
			target = target.replace('/', '\\');
		Path nioTarget = new File(target).toPath();
		Files.createSymbolicLink(nioPath, nioTarget);
	}

	public static boolean isSymlink(File path) {
		Path nioPath = path.toPath();
		return Files.isSymbolicLink(nioPath);
	}

	public static long lastModified(File path) throws IOException {
		Path nioPath = path.toPath();
		return Files.getLastModifiedTime(nioPath, LinkOption.NOFOLLOW_LINKS)
				.toMillis();
	}

	public static void setLastModified(File path, long time) throws IOException {
		Path nioPath = path.toPath();
		Files.setLastModifiedTime(nioPath, FileTime.fromMillis(time));
	}

	public static boolean exists(File path) {
		Path nioPath = path.toPath();
		return Files.exists(nioPath, LinkOption.NOFOLLOW_LINKS);
	}

	public static boolean isHidden(File path) throws IOException {
		Path nioPath = path.toPath();
		return Files.isHidden(nioPath);
	}

	public static void setHidden(File path, boolean hidden) throws IOException {
		Path nioPath = path.toPath();
		Files.setAttribute(nioPath, "dos:hidden", Boolean.valueOf(hidden), //$NON-NLS-1$
				LinkOption.NOFOLLOW_LINKS);
	}

	public static long getLength(File path) throws IOException {
		Path nioPath = path.toPath();
		if (Files.isSymbolicLink(nioPath))
			return Files.readSymbolicLink(nioPath).toString()
					.getBytes(Constants.CHARSET).length;
		return Files.size(nioPath);
	}

	public static boolean isDirectory(File path) {
		Path nioPath = path.toPath();
		return Files.isDirectory(nioPath, LinkOption.NOFOLLOW_LINKS);
	}

	public static boolean isFile(File path) {
		Path nioPath = path.toPath();
		return Files.isRegularFile(nioPath, LinkOption.NOFOLLOW_LINKS);
	}

	public static boolean canExecute(File path) {
		if (!isFile(path))
			return false;
		return path.canExecute();
	}

	public static boolean setExecute(File path, boolean executable) {
		if (!isFile(path))
			return false;
		return path.setExecutable(executable);
	}

	public static void delete(File path) throws IOException {
		Path nioPath = path.toPath();
		Files.delete(nioPath);
	}

	static Attributes getFileAttributes(FS fs, File path) {
		try {
			Path nioPath = path.toPath();
			PosixFileAttributes readAttributes = nioPath
					.getFileSystem()
					.provider()
					.getFileAttributeView(nioPath,
							PosixFileAttributeView.class,
							LinkOption.NOFOLLOW_LINKS).readAttributes();
			Attributes attributes = new FileUtil.Java7PosixAttributes(fs, path,
					nioPath, true, readAttributes.isDirectory(), readAttributes
							.permissions().contains(
									PosixFilePermission.OWNER_EXECUTE),
					readAttributes.isSymbolicLink(),
					readAttributes.isRegularFile(), readAttributes
							.creationTime().toMillis(), readAttributes
							.lastModifiedTime().toMillis(),
					readAttributes.size());
			return attributes;
		} catch (NoSuchFileException e) {
			return new FileUtil.Java7PosixAttributes(fs, path, null, false,
					false, false, false, false, 0L, 0L, 0L);
		} catch (IOException e) {
			return new Attributes(path, fs);
		}
	}

	public static File normalize(File file) {
		if (SystemReader.getInstance().isMacOS()) {
			// TODO: Would it be faster to check with isNormalized first
			// assuming normalized paths are much more common
			String normalized = Normalizer.normalize(file.getPath(),
					Normalizer.Form.NFC);
			return new File(normalized);
		}
		return file;
	}

	public static String normalize(String name) {
		if (SystemReader.getInstance().isMacOS()) {
			if (name == null)
				return null;
			return Normalizer.normalize(name, Normalizer.Form.NFC);
		}
		return name;
	}

}

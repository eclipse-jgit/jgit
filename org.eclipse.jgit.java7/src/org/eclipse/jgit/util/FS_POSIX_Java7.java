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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.attribute.PosixFilePermission;

/**
 * FS implementation for Java7 on unix like systems
 */
public class FS_POSIX_Java7 extends FS_POSIX {

	/*
	 * True if the current user "umask" allows to set execute bit for "others".
	 * Can be null if "umask" is not supported (or returns unexpected values) by
	 * current user shell. See
	 * https://bugs.eclipse.org/bugs/show_bug.cgi?id=424395
	 */
	private static final Boolean EXECUTE_FOR_OTHERS;

	static {
		String umask = readUmask();

		// umask return value consists or 3 or 4 digits, like "002" or "0002"
		if (umask != null && umask.length() > 0 && umask.matches("\\d{3,4}")) //$NON-NLS-1$
			// If "ownerOnly" is set to "true", File.setExecutable(executable,
			// ownerOnly) seems to derive right *group* rights automagically
			// from the umask, the extra work is needed for "others" bit only.
			EXECUTE_FOR_OTHERS = isSet(PosixFilePermission.OTHERS_EXECUTE,
					umask);
		else
			EXECUTE_FOR_OTHERS = null;

	}

	FS_POSIX_Java7(FS_POSIX_Java7 src) {
		super(src);
	}

	FS_POSIX_Java7() {
		// empty
	}

	@Override
	public FS newInstance() {
		return new FS_POSIX_Java7(this);
	}

	@Override
	public boolean supportsExecute() {
		return true;
	}

	@Override
	public boolean canExecute(File f) {
		return FileUtil.canExecute(f);
	}

	@Override
	public boolean setExecute(File f, boolean canExecute) {
		// only if the execute has to be set, and we know the umask
		if (canExecute && EXECUTE_FOR_OTHERS != null) {
			if (!isFile(f))
				return false;
			boolean ownerOnly = !EXECUTE_FOR_OTHERS.booleanValue();
			return f.setExecutable(canExecute, ownerOnly);
		}
		// default implementation sets the execute bit for user and group only
		return FileUtil.setExecute(f, canExecute);
	}

	/**
	 * Derives requested permission from given octal umask value as defined e.g.
	 * in http://linux.die.net/man/2/umask.
	 *
	 * @param p
	 *            not null
	 * @param umask
	 *            not null, not empty
	 * @return true if the requested permission is set according to given umask
	 */
	private static Boolean isSet(PosixFilePermission p, String umask) {
		char val;
		switch (p) {
		case OTHERS_EXECUTE:
			// Read last digit, because umask is ordered as: User/Group/Others.
			val = umask.charAt(umask.length() - 1);
			return fromExecuteBit(val);
		default:
			throw new UnsupportedOperationException(
					"isSet() for " + p + " is not implemented!"); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static String readUmask() {
		Process p;
		try {
			p = Runtime.getRuntime().exec(
					new String[] { "sh", "-c", "umask" }, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			final BufferedReader lineRead = new BufferedReader(
					new InputStreamReader(p.getInputStream(), Charset
							.defaultCharset().name()));
			p.waitFor();
			return lineRead.readLine();
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * <pre>
	 * Octal value : Permission
	 *          0 : read, write and execute
	 *          1 : read and write
	 *          2 : read and execute
	 *          3 : read only
	 *          4 : write and execute
	 *          5 : write only
	 *          6 : execute only
	 *          7 : no permissions
	 * </pre>
	 * 
	 * @param val
	 *            digit
	 * @return true if the execute bit is set
	 */
	private static Boolean fromExecuteBit(char val) {
		if (val == '0' || val == '2' || val == '4' || val == '6')
			return Boolean.TRUE;
		return Boolean.FALSE;
	}

	@Override
	public boolean retryFailedLockFileCommit() {
		return false;
	}

	@Override
	public boolean supportsSymlinks() {
		return true;
	}

	@Override
	public boolean isSymLink(File path) throws IOException {
		return FileUtil.isSymlink(path);
	}

	@Override
	public long lastModified(File path) throws IOException {
		return FileUtil.lastModified(path);
	}

	@Override
	public void setLastModified(File path, long time) throws IOException {
		FileUtil.setLastModified(path, time);
	}

	@Override
	public void delete(File path) throws IOException {
		FileUtil.delete(path);
	}

	@Override
	public long length(File f) throws IOException {
		return FileUtil.getLength(f);
	}

	@Override
	public boolean exists(File path) {
		return FileUtil.exists(path);
	}

	@Override
	public boolean isDirectory(File path) {
		return FileUtil.isDirectory(path);
	}

	@Override
	public boolean isFile(File path) {
		return FileUtil.isFile(path);
	}

	@Override
	public boolean isHidden(File path) throws IOException {
		return FileUtil.isHidden(path);
	}

	@Override
	public void setHidden(File path, boolean hidden) throws IOException {
		// no action on POSIX
	}

	@Override
	public String readSymLink(File path) throws IOException {
		return FileUtil.readSymlink(path);
	}

	@Override
	public void createSymLink(File path, String target) throws IOException {
		FileUtil.createSymLink(path, target);
	}

	/**
	 * @since 3.3
	 */
	@Override
	public Attributes getAttributes(File path) {
		return FileUtil.getFileAttributesPosix(this, path);
	}

	/**
	 * @since 3.3
	 */
	@Override
	public File normalize(File file) {
		return FileUtil.normalize(file);
	}

	/**
	 * @since 3.3
	 */
	@Override
	public String normalize(String name) {
		return FileUtil.normalize(name);
	}
}

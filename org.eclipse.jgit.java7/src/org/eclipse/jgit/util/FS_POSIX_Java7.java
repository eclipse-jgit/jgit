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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/**
 * FS implementation for Java7 on unix like systems
 */
public class FS_POSIX_Java7 extends FS_POSIX {

	/*
	 * True if the current user "umask" allows to set execute bit for "others".
	 * Can be null if "umask" is not supported (or returns unexpected values) by
	 * current user shell.
	 *
	 * Bug 424395: with the umask of 0002 (user: rwx group: rwx others: rx) egit
	 * checked out files as rwx,rwx,r (execution not allowed for "others"). To
	 * fix this and properly set "executable" permission bit for "others", we
	 * must consider the user umask on checkout
	 */
	private static final Boolean EXECUTE_FOR_OTHERS;

	/*
	 * True if the current user "umask" allows to set execute bit for "group".
	 * Can be null if "umask" is not supported (or returns unexpected values) by
	 * current user shell.
	 */
	private static final Boolean EXECUTE_FOR_GROUP;

	static {
		String umask = readUmask();

		// umask return value consists of 3 or 4 digits, like "002" or "0002"
		if (umask != null && umask.length() > 0 && umask.matches("\\d{3,4}")) { //$NON-NLS-1$
			EXECUTE_FOR_OTHERS = isGranted(PosixFilePermission.OTHERS_EXECUTE,
					umask);
			EXECUTE_FOR_GROUP = isGranted(PosixFilePermission.GROUP_EXECUTE,
					umask);
		} else {
			EXECUTE_FOR_OTHERS = null;
			EXECUTE_FOR_GROUP = null;
		}
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
		if (!isFile(f))
			return false;
		// only if the execute has to be set, and we know the umask
		if (canExecute && EXECUTE_FOR_OTHERS != null) {
			try {
				Path path = f.toPath();
				Set<PosixFilePermission> pset = Files
						.getPosixFilePermissions(path);
				// user is always allowed to set execute
				pset.add(PosixFilePermission.OWNER_EXECUTE);

				if (EXECUTE_FOR_GROUP.booleanValue())
					pset.add(PosixFilePermission.GROUP_EXECUTE);

				if (EXECUTE_FOR_OTHERS.booleanValue())
					pset.add(PosixFilePermission.OTHERS_EXECUTE);

				Files.setPosixFilePermissions(path, pset);
				return true;
			} catch (IOException e) {
				// The interface doesn't allow to throw IOException
				final boolean debug = Boolean.parseBoolean(SystemReader
						.getInstance().getProperty("jgit.fs.debug")); //$NON-NLS-1$
				if (debug)
					System.err.println(e);
				return false;
			}
		}
		// if umask is not working for some reason: fall back to default (buggy)
		// implementation which does not consider umask: see bug 424395
		return f.setExecutable(canExecute);
	}

	/**
	 * Derives requested permission from given octal umask value as defined e.g.
	 * in <a href="http://linux.die.net/man/2/umask">http://linux.die.net/man/2/
	 * umask</a>.
	 * <p>
	 * The umask expected here must consist of 3 or 4 digits. Last three digits
	 * are significant here because they represent file permissions granted to
	 * the "owner", "group" and "others" (in this order).
	 * <p>
	 * Each single digit from the umask represents 3 bits of the mask standing
	 * for "<b>r</b>ead, <b>w</b>rite, e<b>x</b>ecute" permissions (in this
	 * order).
	 * <p>
	 * The possible umask values table:
	 *
	 * <pre>
	 * Value : Bits:Abbr.: Permission
	 *     0 : 000 :rwx  : read, write and execute
	 *     1 : 001 :rw   : read and write
	 *     2 : 010 :rx   : read and execute
	 *     3 : 011 :r    : read only
	 *     4 : 100 :wx   : write and execute
	 *     5 : 101 :w    : write only
	 *     6 : 110 :x    : execute only
	 *     7 : 111 :     : no permissions
	 * </pre>
	 * <p>
	 * Note, that umask value is used to "mask" the requested permissions on
	 * file creation by combining the requested permission bit with the
	 * <b>negated</b> value of the umask bit.
	 * <p>
	 * Simply speaking, if a bit is <b>not</b> set in the umask, then the
	 * appropriate right <b>will</b> be granted <b>if</b> requested. If a bit is
	 * set in the umask value, then the appropriate permission will be not
	 * granted.
	 * <p>
	 * Example:
	 * <li>umask 023 ("000 010 011" or rwx rx r) combined with the request to
	 * create an executable file with full set of permissions for everyone (777)
	 * results in the file with permissions 754 (rwx rx r).
	 * <li>umask 002 ("000 000 010" or rwx rwx rx) combined with the request to
	 * create an executable file with full set of permissions for everyone (777)
	 * results in the file with permissions 775 (rwx rwx rx).
	 * <li>umask 002 ("000 000 010" or rwx rwx rx) combined with the request to
	 * create a file without executable rights for everyone (666) results in the
	 * file with permissions 664 (rw rw r).
	 *
	 * @param p
	 *            non null permission
	 * @param umask
	 *            octal umask value represented by at least three digits. The
	 *            digits (read from the end to beginning of the umask) represent
	 *            permissions for "others", "group" and "owner".
	 *
	 * @return true if the requested permission is set according to given umask
	 */
	private static Boolean isGranted(PosixFilePermission p, String umask) {
		char val;
		switch (p) {
		case OTHERS_EXECUTE:
			// Read last digit, because umask is ordered as: User/Group/Others.
			val = umask.charAt(umask.length() - 1);
			return isExecuteGranted(val);
		case GROUP_EXECUTE:
			val = umask.charAt(umask.length() - 2);
			return isExecuteGranted(val);
		default:
			throw new UnsupportedOperationException(
					"isGranted() for " + p + " is not implemented!"); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	/**
	 * @param c
	 *            character representing octal permission value from the table
	 *            in {@link #isGranted(PosixFilePermission, String)}
	 * @return true if the "execute" permission is granted according to given
	 *         character
	 */
	private static Boolean isExecuteGranted(char c) {
		if (c == '0' || c == '2' || c == '4' || c == '6')
			return Boolean.TRUE;
		return Boolean.FALSE;
	}

	private static String readUmask() {
		Process p;
		try {
			p = Runtime.getRuntime().exec(
					new String[] { "sh", "-c", "umask" }, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			try (BufferedReader lineRead = new BufferedReader(
					new InputStreamReader(p.getInputStream(), Charset
							.defaultCharset().name()))) {
				p.waitFor();
				return lineRead.readLine();
			}
		} catch (Exception e) {
			return null;
		}
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

/*
 * Copyright (C) 2012, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2014, Obeo
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

/**
 * FS for Java7 on Windows
 */
public class FS_Win32_Java7 extends FS_Win32 {

	private volatile Boolean supportSymlinks;

	FS_Win32_Java7(FS src) {
		super(src);
	}

	FS_Win32_Java7() {
	}

	@Override
	public FS newInstance() {
		return new FS_Win32_Java7(this);
	}

	@Override
	public boolean supportsSymlinks() {
		if (supportSymlinks == null)
			detectSymlinkSupport();
		return Boolean.TRUE.equals(supportSymlinks);
	}

	private void detectSymlinkSupport() {
		File tempFile = null;
		try {
			tempFile = File.createTempFile("tempsymlinktarget", ""); //$NON-NLS-1$ //$NON-NLS-2$
			File linkName = new File(tempFile.getParentFile(), "tempsymlink"); //$NON-NLS-1$
			FileUtil.createSymLink(linkName, tempFile.getPath());
			supportSymlinks = Boolean.TRUE;
			linkName.delete();
		} catch (IOException e) {
			supportSymlinks = Boolean.FALSE;
		} finally {
			if (tempFile != null)
				try {
					FileUtils.delete(tempFile);
				} catch (IOException e) {
					throw new RuntimeException(e); // panic
				}
		}
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
		FileUtil.setHidden(path, hidden);
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
		return FileUtil.getFileAttributesBasic(this, path);
	}

	/**
	 * @since 3.4
	 */
	@Override
	public PathMatcher getPathMatcher(String globPattern) {
		return new PathMatcher_Java7(globPattern);
	}
}

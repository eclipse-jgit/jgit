/*
 * Copyright (C) 2010, Matthias Sohn <matthias.sohn@sap.com>
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
package org.eclipse.jgit.util.fs;

import org.eclipse.jgit.lib.FileMode;

/**
 * Value class encapsulating lstat()'s return value
 */
public final class LStat {
	private int ctimeSec;

	private int ctimeNSec;

	private int mtimeSec;

	private int mtimeNSec;

	private int device;

	private int inode;

	private FileMode mode;

	private int userId;

	private int groupId;

	private long size;

	/**
	 * construct from data available via JDK
	 *
	 * @param lastModified
	 *            the last time a file's data changed in msec since the epoch
	 * @param mode
	 *            the file's mode
	 * @param size
	 *            the file's size in bytes
	 */
	LStat(long lastModified, FileMode mode, long size) {
		mtimeSec = (int) (lastModified / 1000);
		mtimeNSec = (int) (lastModified % 1000) * 1000000;
		this.mode = mode;
		this.size = size;
	}

	/**
	 * @param rawlstat
	 *            raw lstat data as int[]
	 */
	LStat(int[] rawlstat) {
		ctimeSec = rawlstat[0];
		ctimeNSec = rawlstat[1];
		mtimeSec = rawlstat[2];
		mtimeNSec = rawlstat[3];
		device = rawlstat[4];
		inode = rawlstat[5];
		mode = FileMode.fromBits(rawlstat[6]);
		userId = rawlstat[7];
		groupId = rawlstat[8];
		size = rawlstat[9] + rawlstat[10] << 32;
	}

	/**
	 * @return ctime seconds, the last time a file's metadata changed
	 */
	public int getCTimeSec() {
		return ctimeSec;
	}

	/**
	 * @return ctime nanoseconds, the last time a file's metadata changed
	 */
	public int getCTimeNSec() {
		return ctimeNSec;
	}

	/**
	 * @return mtime seconds, the last time a file's data changed
	 */
	public int getMTimeSec() {
		return mtimeSec;
	}

	/**
	 * @return mtime nanoseconds, the last time a file's data changed
	 */
	public int getMTimeNSec() {
		return mtimeNSec;
	}

	/**
	 * @return device inode resides on
	 */
	public int getDevice() {
		return device;
	}

	/**
	 * @return inode's number
	 */
	public int getInode() {
		return inode;
	}

	/**
	 * @return inode protection mode
	 */
	public FileMode getMode() {
		return mode;
	}

	/**
	 * @return user-id of owner
	 */
	public int getUserId() {
		return userId;
	}

	/**
	 * @return group-id of owner
	 */
	public int getGroupId() {
		return groupId;
	}

	/**
	 * @return file size, in bytes
	 */
	public long getSize() {
		return size;
	}

	/**
	 * @return string representation
	 */
	public String toString() {
		StringBuilder s = new StringBuilder("ctime: ");
		s.append(getCTimeSec());
		s.append("\nctimensec: ").append(getCTimeNSec());
		s.append("\nmtime: ").append(getMTimeSec());
		s.append("\nmtimensec: ").append(getMTimeNSec());
		s.append("\ndevice: ").append(getDevice());
		s.append("\ninode: ").append(getInode());
		s.append("\nmode: ").append(getMode());
		s.append("\nuid: ").append(getUserId());
		s.append("\ngid: ").append(getGroupId());
		s.append("\nsize: ").append(getSize());
		return s.toString();
	}
}

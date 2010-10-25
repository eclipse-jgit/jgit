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

/**
 * Value class encapsulating lstat()'s return value
 */
public final class LStat {
	private int[] rawlstat;

	/**
	 * @param rawlstat
	 *            raw lstat data as int[]
	 */
	public LStat(int[] rawlstat) {
		this.rawlstat = rawlstat;
	}

	/**
	 * @return ctime seconds, the last time a file's metadata changed
	 */
	public int getCTimeSec() {
		return rawlstat[0];
	}

	/**
	 * @return ctime nanoseconds, the last time a file's metadata changed
	 */
	public int getCTimeNSec() {
		return rawlstat[1];
	}

	/**
	 * @return mtime seconds, the last time a file's data changed
	 */
	public int getMTimeSec() {
		return rawlstat[2];
	}

	/**
	 * @return mtime nanoseconds, the last time a file's data changed
	 */
	public int getMTimeNSec() {
		return rawlstat[3];
	}

	/**
	 * @return device inode resides on
	 */
	public int getDevice() {
		return rawlstat[4];
	}

	/**
	 * @return inode's number
	 */
	public int getInode() {
		return rawlstat[5];
	}

	/**
	 * @return inode protection mode
	 */
	public int getMode() {
		return rawlstat[6];
	}

	/**
	 * @return user-id of owner
	 */
	public int getUserId() {
		return rawlstat[7];
	}

	/**
	 * @return group-id of owner
	 */
	public int getGroupId() {
		return rawlstat[8];
	}

	/**
	 * @return file size, in bytes
	 */
	public int getSize() {
		return rawlstat[9];
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


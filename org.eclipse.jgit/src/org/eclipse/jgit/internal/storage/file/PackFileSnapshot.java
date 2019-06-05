/*
 * Copyright (C) 2019, Matthias Sohn <matthias.sohn@sap.com>
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
package org.eclipse.jgit.internal.storage.file;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

class PackFileSnapshot extends FileSnapshot {

	private static final ObjectId MISSING_CHECKSUM = ObjectId.zeroId();

	/**
	 * Record a snapshot for a specific packfile path.
	 * <p>
	 * This method should be invoked before the packfile is accessed.
	 *
	 * @param path
	 *            the path to later remember. The path's current status
	 *            information is saved.
	 * @return the snapshot.
	 */
	public static PackFileSnapshot save(File path) {
		return new PackFileSnapshot(path);
	}

	private AnyObjectId checksum = MISSING_CHECKSUM;

	private boolean wasChecksumChanged;


	PackFileSnapshot(File packFile) {
		super(packFile);
	}

	void setChecksum(AnyObjectId checksum) {
		this.checksum = checksum;
	}

	/** {@inheritDoc} */
	@Override
	public boolean isModified(File packFile) {
		if (!super.isModified(packFile)) {
			return false;
		}
		if (wasSizeChanged() || wasFileKeyChanged()
				|| !wasLastModifiedRacilyClean()) {
			return true;
		}
		return isChecksumChanged(packFile);
	}

	boolean isChecksumChanged(File packFile) {
		return wasChecksumChanged = checksum != MISSING_CHECKSUM
				&& !checksum.equals(readChecksum(packFile));
	}

	private AnyObjectId readChecksum(File packFile) {
		try (RandomAccessFile fd = new RandomAccessFile(packFile, "r")) { //$NON-NLS-1$
			fd.seek(fd.length() - 20);
			final byte[] buf = new byte[20];
			fd.readFully(buf, 0, 20);
			return ObjectId.fromRaw(buf);
		} catch (IOException e) {
			return MISSING_CHECKSUM;
		}
	}

	boolean wasChecksumChanged() {
		return wasChecksumChanged;
	}

	@SuppressWarnings("nls")
	@Override
	public String toString() {
		return "PackFileSnapshot [checksum=" + checksum + ", "
				+ super.toString() + "]";
	}

}

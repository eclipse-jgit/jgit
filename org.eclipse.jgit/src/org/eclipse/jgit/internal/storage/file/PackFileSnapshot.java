/*
 * Copyright (C) 2019, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.Equality;

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
		return wasChecksumChanged = !Equality.isSameInstance(checksum,
				MISSING_CHECKSUM)
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

/*
 * Copyright (C) 2011, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.internal.storage.pack.CachedPack;
import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.internal.storage.pack.PackOutputStream;
import org.eclipse.jgit.internal.storage.pack.StoredObjectRepresentation;

class LocalCachedPack extends CachedPack {
	private final ObjectDirectory odb;

	private final String[] packNames;

	private PackFile[] packs;

	LocalCachedPack(ObjectDirectory odb, List<String> packNames) {
		this.odb = odb;
		this.packNames = packNames.toArray(new String[0]);
	}

	LocalCachedPack(List<PackFile> packs) {
		odb = null;
		packNames = null;
		this.packs = packs.toArray(new PackFile[0]);
	}

	/** {@inheritDoc} */
	@Override
	public long getObjectCount() throws IOException {
		long cnt = 0;
		for (PackFile pack : getPacks())
			cnt += pack.getObjectCount();
		return cnt;
	}

	void copyAsIs(PackOutputStream out, WindowCursor wc)
			throws IOException {
		for (PackFile pack : getPacks())
			pack.copyPackAsIs(out, wc);
	}

	/** {@inheritDoc} */
	@Override
	public boolean hasObject(ObjectToPack obj, StoredObjectRepresentation rep) {
		try {
			LocalObjectRepresentation local = (LocalObjectRepresentation) rep;
			for (PackFile pack : getPacks()) {
				if (local.pack == pack)
					return true;
			}
			return false;
		} catch (FileNotFoundException packGone) {
			return false;
		}
	}

	private PackFile[] getPacks() throws FileNotFoundException {
		if (packs == null) {
			PackFile[] p = new PackFile[packNames.length];
			for (int i = 0; i < packNames.length; i++)
				p[i] = getPackFile(packNames[i]);
			packs = p;
		}
		return packs;
	}

	private PackFile getPackFile(String packName) throws FileNotFoundException {
		for (PackFile pack : odb.getPacks()) {
			if (packName.equals(pack.getPackName()))
				return pack;
		}
		throw new FileNotFoundException(getPackFilePath(packName));
	}

	private String getPackFilePath(String packName) {
		final File packDir = odb.getPackDirectory();
		return new File(packDir, "pack-" + packName + ".pack").getPath(); //$NON-NLS-1$ //$NON-NLS-2$
	}
}

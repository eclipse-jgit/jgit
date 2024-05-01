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
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.internal.storage.pack.PackOutputStream;
import org.eclipse.jgit.internal.storage.pack.StoredObjectRepresentation;

class LocalCachedPack extends CachedPack {
	private final ObjectDirectory odb;

	private final String[] packNames;

	private Pack[] packs;

	LocalCachedPack(ObjectDirectory odb, List<String> packNames) {
		this.odb = odb;
		this.packNames = packNames.toArray(new String[0]);
	}

	LocalCachedPack(List<Pack> packs) {
		odb = null;
		packNames = null;
		this.packs = packs.toArray(new Pack[0]);
	}

	@Override
	public long getObjectCount() throws IOException {
		long cnt = 0;
		for (Pack pack : getPacks())
			cnt += pack.getObjectCount();
		return cnt;
	}

	void copyAsIs(PackOutputStream out, WindowCursor wc)
			throws IOException {
		for (Pack pack : getPacks())
			pack.copyPackAsIs(out, wc);
	}

	@Override
	public boolean hasObject(ObjectToPack obj, StoredObjectRepresentation rep) {
		try {
			LocalObjectRepresentation local = (LocalObjectRepresentation) rep;
			for (Pack pack : getPacks()) {
				if (local.pack == pack)
					return true;
			}
			return false;
		} catch (FileNotFoundException packGone) {
			return false;
		}
	}

	private Pack[] getPacks() throws FileNotFoundException {
		if (packs == null) {
			Pack[] p = new Pack[packNames.length];
			for (int i = 0; i < packNames.length; i++)
				p[i] = getPackFile(packNames[i]);
			packs = p;
		}
		return packs;
	}

	private Pack getPackFile(String packName) throws FileNotFoundException {
		for (Pack pack : odb.getPacks()) {
			if (packName.equals(pack.getPackName()))
				return pack;
		}
		throw new FileNotFoundException(getPackFilePath(packName));
	}

	private String getPackFilePath(String packName) {
		final File packDir = odb.getPackDirectory();
		return new PackFile(packDir, packName, PackExt.PACK).getPath();
	}
}

/*
 * Copyright (C) 2011, Google Inc.
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
		this.packNames = packNames.toArray(new String[packNames.size()]);
	}

	LocalCachedPack(List<PackFile> packs) {
		odb = null;
		packNames = null;
		this.packs = packs.toArray(new PackFile[packs.size()]);
	}

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

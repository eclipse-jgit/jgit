/*
 * Copyright (C) 2019, Google LLC.
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

package org.eclipse.jgit.internal.storage.dfs;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;

import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.internal.storage.pack.PackOutputStream;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.TestRng;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.junit.Before;
import org.junit.Test;

public class DfsPackFileTest {
	InMemoryRepository db;
	boolean bypassCache;
	boolean clearCache;

	@Before
	public void setUp() {
		db = new InMemoryRepository(new DfsRepositoryDescription("test"));
	}

	@Test
	public void testCopyPackBypassCachesSmallCached() throws IOException {
		bypassCache = true;
		clearCache = false;
		setupPack(512, 256);
		assertPackSize();
	}

	@Test
	public void testCopyPackBypassCacheSmallNoCache() throws IOException {
		bypassCache = true;
		clearCache = true;
		setupPack(512, 256);
		assertPackSize();
	}

	@Test
	public void testCopyPackBypassCacheLargeCached() throws IOException {
		bypassCache = true;
		clearCache = false;
		setupPack(512, 8000);
		assertPackSize();
	}

	@Test
	public void testCopyPackBypassCacheLargeNoCache() throws IOException {
		bypassCache = true;
		clearCache = true;
		setupPack(512, 8000);
		assertPackSize();
	}

	@Test
	public void testCopyPackThroughCacheSmallCached() throws IOException {
		bypassCache = false;
		clearCache = false;
		setupPack(512, 256);
		assertPackSize();
	}

	@Test
	public void testCopyPackThroughCacheSmallNoCache() throws IOException {
		bypassCache = false;
		clearCache = true;
		setupPack(512, 256);
		assertPackSize();
	}

	@Test
	public void testCopyPackThroughCacheLargeCached() throws IOException {
		bypassCache = false;
		clearCache = false;
		setupPack(512, 8000);
		assertPackSize();
	}

	@Test
	public void testCopyPackThroughCacheLargeNoCache() throws IOException {
		bypassCache = false;
		clearCache = true;
		setupPack(512, 8000);
		assertPackSize();
	}

	private void setupPack(int bs, int ps) throws IOException {
		DfsBlockCacheConfig cfg = new DfsBlockCacheConfig().setBlockSize(bs)
				.setBlockLimit(bs * 100).setStreamRatio(bypassCache ? 0F : 1F);
		DfsBlockCache.reconfigure(cfg);

		byte[] data = new TestRng(JGitTestUtil.getName()).nextBytes(ps);
		DfsInserter ins = (DfsInserter) db.newObjectInserter();
		ins.setCompressionLevel(Deflater.NO_COMPRESSION);
		ins.insert(Constants.OBJ_BLOB, data);
		ins.flush();

		if (clearCache) {
			DfsBlockCache.reconfigure(cfg);
			db.getObjectDatabase().clearCache();
		}
	}

	private void assertPackSize() throws IOException {
		try (DfsReader ctx = db.getObjectDatabase().newReader();
				PackWriter pw = new PackWriter(ctx);
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				PackOutputStream out = new PackOutputStream(
						NullProgressMonitor.INSTANCE, os, pw)) {
			DfsPackFile pack = db.getObjectDatabase().getPacks()[0];
			long packSize = pack.getPackDescription().getFileSize(PackExt.PACK);
			pack.copyPackAsIs(out, ctx);
			assertEquals(packSize - (12 + 20), os.size());
		}
	}
}

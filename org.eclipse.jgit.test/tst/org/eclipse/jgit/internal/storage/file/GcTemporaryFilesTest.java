/*
 * Copyright (C) 2017 Ericsson
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Paths;
import java.time.Instant;

import org.junit.Before;
import org.junit.Test;

public class GcTemporaryFilesTest extends GcTestCase {
	private static final String TEMP_IDX = "gc_1234567890.idx_tmp";

	private static final String TEMP_PACK = "gc_1234567890.pack_tmp";

	private File packDir;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		packDir = Paths.get(repo.getObjectsDirectory().getAbsolutePath(),
				"pack").toFile(); //$NON-NLS-1$
	}

	@Test
	public void oldTempPacksAndIdxAreDeleted() throws Exception {
		File tempIndex = new File(packDir, TEMP_IDX);
		File tempPack = new File(packDir, TEMP_PACK);
		if (!packDir.exists() || !packDir.isDirectory()) {
			assertTrue(packDir.mkdirs());
		}
		assertTrue(tempPack.createNewFile());
		assertTrue(tempIndex.createNewFile());
		assertTrue(tempIndex.exists());
		assertTrue(tempPack.exists());
		long _24HoursBefore = Instant.now().toEpochMilli()
				- 24 * 60 * 62 * 1000;
		tempIndex.setLastModified(_24HoursBefore);
		tempPack.setLastModified(_24HoursBefore);
		gc.gc();
		assertFalse(tempIndex.exists());
		assertFalse(tempPack.exists());
	}

	@Test
	public void recentTempPacksAndIdxAreNotDeleted() throws Exception {
		File tempIndex = new File(packDir, TEMP_IDX);
		File tempPack = new File(packDir, TEMP_PACK);
		if (!packDir.exists() || !packDir.isDirectory()) {
			assertTrue(packDir.mkdirs());
		}
		assertTrue(tempPack.createNewFile());
		assertTrue(tempIndex.createNewFile());
		assertTrue(tempIndex.exists());
		assertTrue(tempPack.exists());
		gc.gc();
		assertTrue(tempIndex.exists());
		assertTrue(tempPack.exists());
	}
}

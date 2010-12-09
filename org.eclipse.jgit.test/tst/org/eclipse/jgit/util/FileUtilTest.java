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

package org.eclipse.jgit.util;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

public class FileUtilTest extends TestCase {
	public void testDeleteFile() throws IOException {
		File f = new File("test");
		assertTrue(f.createNewFile());
		FileUtils.delete(f);
		assertFalse(f.exists());

		try {
			FileUtils.delete(f);
			fail("deletion of non-existing file must fail");
		} catch (IOException e) {
			// expected
		}

		try {
			FileUtils.delete(f, FileUtils.SKIP_MISSING);
		} catch (IOException e) {
			fail("deletion of non-existing file must not fail with option SKIP_MISSING");
		}
	}

	public void testDeleteRecursive() throws IOException {
		File f1 = new File("test/test/a");
		f1.mkdirs();
		f1.createNewFile();
		File f2 = new File("test/test/b");
		f2.createNewFile();
		File d = new File("test");
		FileUtils.delete(d, FileUtils.RECURSIVE);
		assertFalse(d.exists());

		try {
			FileUtils.delete(d, FileUtils.RECURSIVE);
			fail("recursive deletion of non-existing directory must fail");
		} catch (IOException e) {
			// expected
		}

		try {
			FileUtils.delete(d, FileUtils.RECURSIVE | FileUtils.SKIP_MISSING);
		} catch (IOException e) {
			fail("recursive deletion of non-existing directory must not fail with option SKIP_MISSING");
		}
	}
}

/*
 * Copyright (C) 2008, Imran M Yousuf <imyousuf@smartitengineering.com>
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2009, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.io.File;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class PackIndexV1Test extends PackIndexTestCase {
	@Override
	public File getFileForPack34be9032() {
		return JGitTestUtil.getTestResourceFile(
                    "pack-34be9032ac282b11fa9babdc2b2a93ca996c9c2f.idx");
	}

	@Override
	public File getFileForPackdf2982f28() {
		return JGitTestUtil.getTestResourceFile(
                    "pack-df2982f284bbabb6bdb59ee3fcc6eb0983e20371.idx");
	}

	/**
	 * Verify CRC32 - V1 should not index anything.
	 *
	 * @throws MissingObjectException
	 */
	@Override
	@Test
	public void testCRC32() throws MissingObjectException {
		assertFalse(smallIdx.hasCRC32Support());
		try {
			smallIdx.findCRC32(ObjectId
					.fromString("4b825dc642cb6eb9a060e54bf8d69288fbee4904"));
			fail("index V1 shouldn't support CRC");
		} catch (UnsupportedOperationException x) {
			// expected
		}
	}
}

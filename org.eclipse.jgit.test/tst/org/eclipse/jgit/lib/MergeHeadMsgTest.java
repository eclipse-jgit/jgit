/*
 * Copyright (C) 2010-2012 Christian Halstrick <christian.halstrick@sap.com>
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
package org.eclipse.jgit.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.Test;

public class MergeHeadMsgTest extends RepositoryTestCase {
	private static final String mergeMsg = "merge a and b";

	private static final String sampleId = "1c6db447abdbb291b25f07be38ea0b1bf94947c5";

	@Test
	public void testReadWriteMergeHeads() throws IOException {
		assertEquals(db.readMergeHeads(), null);
		db.writeMergeHeads(Arrays.asList(ObjectId.zeroId(),
				ObjectId.fromString(sampleId)));
		assertEquals(read(new File(db.getDirectory(), "MERGE_HEAD")), "0000000000000000000000000000000000000000\n1c6db447abdbb291b25f07be38ea0b1bf94947c5\n");
		assertEquals(db.readMergeHeads().size(), 2);
		assertEquals(db.readMergeHeads().get(0), ObjectId.zeroId());
		assertEquals(db.readMergeHeads().get(1), ObjectId.fromString(sampleId));
		// same test again, this time with lower-level io
		FileOutputStream fos = new FileOutputStream(new File(db.getDirectory(),
		"MERGE_HEAD"));
		try {
			fos.write("0000000000000000000000000000000000000000\n1c6db447abdbb291b25f07be38ea0b1bf94947c5\n".getBytes(Constants.CHARACTER_ENCODING));
		} finally {
			fos.close();
		}
		assertEquals(db.readMergeHeads().size(), 2);
		assertEquals(db.readMergeHeads().get(0), ObjectId.zeroId());
		assertEquals(db.readMergeHeads().get(1), ObjectId.fromString(sampleId));
		db.writeMergeHeads(Collections.<ObjectId> emptyList());
		assertEquals(read(new File(db.getDirectory(), "MERGE_HEAD")), "");
		assertEquals(db.readMergeHeads(), null);
		fos = new FileOutputStream(new File(db.getDirectory(),
				"MERGE_HEAD"));
		try {
			fos.write(sampleId.getBytes(Constants.CHARACTER_ENCODING));
		} finally {
			fos.close();
		}
		assertEquals(db.readMergeHeads().size(), 1);
		assertEquals(db.readMergeHeads().get(0), ObjectId.fromString(sampleId));
	}

	@Test
	public void testReadWriteMergeMsg() throws IOException {
		assertEquals(db.readMergeCommitMsg(), null);
		assertFalse(new File(db.getDirectory(), "MERGE_MSG").exists());
		db.writeMergeCommitMsg(mergeMsg);
		assertEquals(db.readMergeCommitMsg(), mergeMsg);
		assertEquals(read(new File(db.getDirectory(), "MERGE_MSG")), mergeMsg);
		db.writeMergeCommitMsg(null);
		assertEquals(db.readMergeCommitMsg(), null);
		assertFalse(new File(db.getDirectory(), "MERGE_MSG").exists());
		FileOutputStream fos = new FileOutputStream(new File(db.getDirectory(),
				Constants.MERGE_MSG));
		try {
			fos.write(mergeMsg.getBytes(Constants.CHARACTER_ENCODING));
		} finally {
			fos.close();
		}
		assertEquals(db.readMergeCommitMsg(), mergeMsg);
	}

}

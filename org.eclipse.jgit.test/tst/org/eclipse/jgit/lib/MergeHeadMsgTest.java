/*
 * Copyright (C) 2010-2012 Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.jupiter.api.Test;

public class MergeHeadMsgTest extends RepositoryTestCase {
	private static final String mergeMsg = "merge a and b";

	private static final String sampleId = "1c6db447abdbb291b25f07be38ea0b1bf94947c5";

	@Test
	void testReadWriteMergeHeads() throws IOException {
		assertNull(db.readMergeHeads());
		db.writeMergeHeads(Arrays.asList(ObjectId.zeroId(),
				ObjectId.fromString(sampleId)));
		assertEquals(read(new File(db.getDirectory(), "MERGE_HEAD")), "0000000000000000000000000000000000000000\n1c6db447abdbb291b25f07be38ea0b1bf94947c5\n");
		assertEquals(db.readMergeHeads().size(), 2);
		assertEquals(db.readMergeHeads().get(0), ObjectId.zeroId());
		assertEquals(db.readMergeHeads().get(1), ObjectId.fromString(sampleId));

		// same test again, this time with lower-level io
		try (FileOutputStream fos = new FileOutputStream(
				new File(db.getDirectory(), "MERGE_HEAD"));) {
			fos.write(
					"0000000000000000000000000000000000000000\n1c6db447abdbb291b25f07be38ea0b1bf94947c5\n"
							.getBytes(UTF_8));
		}
		assertEquals(db.readMergeHeads().size(), 2);
		assertEquals(db.readMergeHeads().get(0), ObjectId.zeroId());
		assertEquals(db.readMergeHeads().get(1), ObjectId.fromString(sampleId));
		db.writeMergeHeads(Collections.<ObjectId>emptyList());
		assertEquals(read(new File(db.getDirectory(), "MERGE_HEAD")), "");
		assertNull(db.readMergeHeads());
		try (FileOutputStream fos = new FileOutputStream(
				new File(db.getDirectory(), "MERGE_HEAD"))) {
			fos.write(sampleId.getBytes(UTF_8));
		}
		assertEquals(db.readMergeHeads().size(), 1);
		assertEquals(db.readMergeHeads().get(0), ObjectId.fromString(sampleId));
	}

	@Test
	void testReadWriteMergeMsg() throws IOException {
		assertNull(db.readMergeCommitMsg());
		assertFalse(new File(db.getDirectory(), "MERGE_MSG").exists());
		db.writeMergeCommitMsg(mergeMsg);
		assertEquals(db.readMergeCommitMsg(), mergeMsg);
		assertEquals(read(new File(db.getDirectory(), "MERGE_MSG")), mergeMsg);
		db.writeMergeCommitMsg(null);
		assertNull(db.readMergeCommitMsg());
		assertFalse(new File(db.getDirectory(), "MERGE_MSG").exists());
		try (FileOutputStream fos = new FileOutputStream(
				new File(db.getDirectory(), Constants.MERGE_MSG))) {
			fos.write(mergeMsg.getBytes(UTF_8));
		}
		assertEquals(db.readMergeCommitMsg(), mergeMsg);
	}

}

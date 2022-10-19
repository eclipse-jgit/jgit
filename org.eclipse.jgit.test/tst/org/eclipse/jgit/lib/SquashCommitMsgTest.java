/*
 * Copyright (C) 2012, IBM Corporation and others. and others
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

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.jupiter.api.Test;

public class SquashCommitMsgTest extends RepositoryTestCase {
	private static final String squashMsg = "squashed commit";

	@Test
	void testReadWriteMergeMsg() throws IOException {
		assertNull(db.readSquashCommitMsg());
		assertFalse(new File(db.getDirectory(), Constants.SQUASH_MSG).exists());
		db.writeSquashCommitMsg(squashMsg);
		assertEquals(squashMsg, db.readSquashCommitMsg());
		assertEquals(read(new File(db.getDirectory(), Constants.SQUASH_MSG)),
				squashMsg);
		db.writeSquashCommitMsg(null);
		assertNull(db.readSquashCommitMsg());
		assertFalse(new File(db.getDirectory(), Constants.SQUASH_MSG).exists());
		try (FileOutputStream fos = new FileOutputStream(
				new File(db.getDirectory(), Constants.SQUASH_MSG))) {
			fos.write(squashMsg.getBytes(UTF_8));
		}
		assertEquals(db.readSquashCommitMsg(), squashMsg);
	}
}

/*
 * Copyright (C) 2012, IBM Corporation and others.
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.Test;

public class SquashCommitMsgTest extends RepositoryTestCase {
	private static final String squashMsg = "squashed commit";

	@Test
	public void testReadWriteMergeMsg() throws IOException {
		assertEquals(db.readSquashCommitMsg(), null);
		assertFalse(new File(db.getDirectory(), Constants.SQUASH_MSG).exists());
		db.writeSquashCommitMsg(squashMsg);
		assertEquals(squashMsg, db.readSquashCommitMsg());
		assertEquals(read(new File(db.getDirectory(), Constants.SQUASH_MSG)),
				squashMsg);
		db.writeSquashCommitMsg(null);
		assertEquals(db.readSquashCommitMsg(), null);
		assertFalse(new File(db.getDirectory(), Constants.SQUASH_MSG).exists());
		try (FileOutputStream fos = new FileOutputStream(
				new File(db.getDirectory(), Constants.SQUASH_MSG))) {
			fos.write(squashMsg.getBytes(UTF_8));
		}
		assertEquals(db.readSquashCommitMsg(), squashMsg);
	}
}

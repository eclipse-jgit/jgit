/*******************************************************************************
 * Copyright (c) 2014 Andreas Hermann
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
 *******************************************************************************/
package org.eclipse.jgit.internal.storage.file;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.test.resources.SampleDataRepositoryTestCase;
import org.junit.Test;

public class ReflogWriterTest extends SampleDataRepositoryTestCase {

	private static String oneLine = "da85355dfc525c9f6f3927b876f379f46ccf826e 3e7549db262d1e836d9bf0af7e22355468f1717c"
			+ " John Doe <john@doe.com> 1243028200 +0200\tstash: Add message with line feeds\n";

	@Test
	public void shouldFilterLineFeedFromMessage() throws Exception {
		ReflogWriter writer =
				new ReflogWriter((RefDirectory) db.getRefDatabase());
		PersonIdent ident = new PersonIdent("John Doe", "john@doe.com",
				1243028200000L, 120);
		ObjectId oldId = ObjectId
				.fromString("da85355dfc525c9f6f3927b876f379f46ccf826e");
		ObjectId newId = ObjectId
				.fromString("3e7549db262d1e836d9bf0af7e22355468f1717c");

		writer.log("refs/heads/master", oldId, newId, ident,
				"stash: Add\nmessage\r\nwith line feeds");

		byte[] buffer = new byte[oneLine.getBytes(UTF_8).length];
		readReflog(buffer);
		assertEquals(oneLine, new String(buffer, UTF_8));
	}

	private void readReflog(byte[] buffer)
			throws FileNotFoundException, IOException {
		File logfile = new File(db.getDirectory(), "logs/refs/heads/master");
		if (!logfile.getParentFile().mkdirs()
				&& !logfile.getParentFile().isDirectory()) {
			throw new IOException(
					"oops, cannot create the directory for the test reflog file"
							+ logfile);
		}
		try (FileInputStream fileInputStream = new FileInputStream(logfile)) {
			fileInputStream.read(buffer);
		}
	}
}

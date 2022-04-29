/*******************************************************************************
 * Copyright (c) 2014 Andreas Hermann and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/
package org.eclipse.jgit.internal.storage.file;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.test.resources.SampleDataRepositoryTestCase;
import org.junit.Test;

public class ReflogWriterTest extends SampleDataRepositoryTestCase {

	private static String oneLinePrefix = "da85355dfc525c9f6f3927b876f379f46ccf826e 3e7549db262d1e836d9bf0af7e22355468f1717c"
			+ " John Doe <john@doe.com> 1243028200 +0200\t";

	private static String oneLine = oneLinePrefix
			+ "stash: Add message with line feeds\n";

	private static final ObjectId oldId = ObjectId
			.fromString("da85355dfc525c9f6f3927b876f379f46ccf826e");

	private static final ObjectId newId = ObjectId
			.fromString("3e7549db262d1e836d9bf0af7e22355468f1717c");

	private static final PersonIdent ident = new PersonIdent("John Doe",
			"john@doe.com", 1243028200000L, 120);

	@Test
	public void shouldFilterLineFeedFromMessage() throws Exception {
		ReflogWriter writer = new ReflogWriter(
				(RefDirectory) db.getRefDatabase());

		writer.log("refs/heads/master", oldId, newId, ident,
				"stash: Add\nmessage\r\nwith line feeds");

		byte[] buffer = new byte[oneLine.getBytes(UTF_8).length];
		readReflog(buffer, "refs/heads/master");
		assertEquals(oneLine, new String(buffer, UTF_8));
	}

	@Test
	public void shouldLogRefsHeads() throws Exception {
		String headRef = Constants.R_HEADS + "foo";
		String logMessage = "foo message";
		ReflogWriter writer = new ReflogWriter(
				(RefDirectory) db.getRefDatabase());

		writer.log(headRef, oldId, newId, ident, logMessage);
		assertTrue(new File(db.getDirectory(), "logs/" + headRef).exists());

		String expectedLog = oneLinePrefix + logMessage;
		byte[] buffer = new byte[expectedLog.getBytes(UTF_8).length];
		readReflog(buffer, headRef);
		assertEquals(expectedLog, new String(buffer, UTF_8));
	}

	@Test
	public void shouldNotLogRemoteRefs() throws Exception {
		String remoteRef = Constants.R_REMOTES + "foo";
		ReflogWriter writer = new ReflogWriter(
				(RefDirectory) db.getRefDatabase());

		writer.log(remoteRef, oldId, newId, ident, "foo");
		assertFalse(new File(db.getDirectory(), "logs/" + remoteRef).exists());
	}

	private void readReflog(byte[] buffer, String refName)
			throws FileNotFoundException, IOException {
		File logfile = new File(db.getDirectory(), "logs/" + refName);
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

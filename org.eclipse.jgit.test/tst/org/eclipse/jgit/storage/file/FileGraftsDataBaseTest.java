/*
 * Copyright (C) 2010, Robin Rosenberg
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
package org.eclipse.jgit.storage.file;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class FileGraftsDataBaseTest extends LocalDiskRepositoryTestCase {

	ObjectId id1 = ObjectId
			.fromString("3423254356356456456425645245614256410001");

	ObjectId id2 = ObjectId
			.fromString("342a254356356456456425645245614256410002");

	ObjectId id3 = ObjectId
			.fromString("3423c54356356456456425645245614256410003");

	ObjectId id4 = ObjectId
			.fromString("34232f4356356456456425645245614256410004");

	@Test
	public void testEmptyGraftsDb() throws IOException {
		FileRepository fill = fill("");
		assertEquals(0, fill.getGrafts().size());
	}

	@Test
	public void testOneLineNoParentGraftsDb() throws IOException {
		FileRepository fill = fill(id1.name() + "\n");
		assertEquals(1, fill.getGrafts().size());
		assertEquals(0, fill.getGrafts().get(id1).size());
		assertNull(fill.getGrafts().get(id4));
	}

	@Test
	public void testOneLineOneParentGraftsDb() throws IOException {
		FileRepository fill = fill(id1.name() + " " + id2.name() + "\n");
		assertEquals(1, fill.getGrafts().size());
		assertEquals(1, fill.getGrafts().get(id1).size());
		assertEquals(id2, fill.getGrafts().get(id1).get(0));
		assertNull(fill.getGrafts().get(id4));
	}

	@Test
	public void testOneLineTwoParentGraftsDb() throws IOException {
		FileRepository fill = fill(id1.name() + " " + id2.name() + " "
				+ id3.name() + "\n");
		assertEquals(1, fill.getGrafts().size());
		assertEquals(2, fill.getGrafts().get(id1).size());
		assertEquals(id2, fill.getGrafts().get(id1).get(0));
		assertEquals(id3, fill.getGrafts().get(id1).get(1));
		assertNull(fill.getGrafts().get(id4));
	}

	@Test
	public void testMultiLineGraftsDb() throws IOException {
		FileRepository fill = fill(id1.name() + " " + id2.name() + "\n"
				+ id3.name() + " " + id2.name() + " " + id4.name() + "\n");
		assertEquals(2, fill.getGrafts().size());
		assertEquals(1, fill.getGrafts().get(id1).size());
		assertEquals(id2, fill.getGrafts().get(id1).get(0));
		assertEquals(2, fill.getGrafts().get(id3).size());
		assertEquals(id2, fill.getGrafts().get(id3).get(0));
		assertEquals(id4, fill.getGrafts().get(id3).get(1));
	}

	private FileRepository fill(String string) throws IOException {
		FileRepository repo = super.createWorkRepository();
		File file = repo.getGraftsFile();
		file.getParentFile().mkdir();
		FileWriter fileWriter = new FileWriter(file);
		fileWriter.write(string);
		fileWriter.close();
		return repo;
	}

}
/*
 * Copyright (C) 2010, Ketan Padegaonkar <KetanPadegaonkar@gmail.com>
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
package org.eclipse.jgit.pgm;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.pgm.CatFile.CatType;
import org.eclipse.jgit.storage.file.FileRepository;

public class CatFileTest extends LocalDiskRepositoryTestCase {

	public void testShouldReadContentsOfCommitsByObjectId() throws Exception {
		FileRepository repo = repo();

		CatFile cat = new CatFile();
		cat.init(repo, repo.getDirectory());
		cat.objectName = "279dae73ebf5f028bcac84afb3b40794f4ae60d4";
		cat.type = CatType.COMMIT;
		StringWriter out = new StringWriter();
		cat.out = new PrintWriter(out);
		cat.run();

		assertEquals(
				"tree 8233e088814f6e198d6179c981177b65ab617b94\n"//
						+ "author GIT_COMMITTER_NAME <GIT_COMMITTER_EMAIL> 1250379778 -0330\n"//
						+ "committer GIT_COMMITTER_NAME <GIT_COMMITTER_EMAIL> 1250379778 -0330\n"//
						+ "\n" + //
						"Initial commit", //
				out.getBuffer().toString());
	}

	public void testShouldReadContentsOfBlobsByObjectId() throws Exception {
		FileRepository repo = repo();

		CatFile cat = new CatFile();
		cat.init(repo, repo.getDirectory());
		cat.objectName = "e916b49e039faf8ee2f3e61c4678d88fa4c09e40";
		cat.type = CatType.BLOB;
		StringWriter out = new StringWriter();
		cat.out = new PrintWriter(out);
		cat.run();

		assertEquals("This project does foo!", out.getBuffer().toString());
	}

	public void testShouldReadContentsOfTreesByObjectId() throws Exception {
		FileRepository repo = repo();

		CatFile cat = new CatFile();
		cat.init(repo, repo.getDirectory());
		cat.objectName = "c7cb03cd4c57db844ca695a23123ce4990a648d1";
		cat.type = CatType.TREE;
		StringWriter out = new StringWriter();
		cat.out = new PrintWriter(out);
		cat.run();

		assertEquals("100644 README", out.getBuffer().toString());
	}

	public void testShouldReadContentsOfTagsByObjectId() throws Exception {
		FileRepository repo = repo();

		CatFile cat = new CatFile();
		cat.init(repo, repo.getDirectory());
		cat.objectName = "v0.1b";
		cat.type = CatType.TAG;
		StringWriter out = new StringWriter();
		cat.out = new PrintWriter(out);
		cat.run();

		assertEquals("object 279dae73ebf5f028bcac84afb3b40794f4ae60d4\n" + //
				"type commit\n" + //
				"tag v0.1b\n" + //
				"\n" + //
				"Tagging for release 0.1b",//
				out.getBuffer().toString());
	}

	public void testShouldThrowErrorOnInvalidObjectId() throws Exception {
		FileRepository repo = repo();

		CatFile cat = new CatFile();
		cat.init(repo, repo.getDirectory());
		cat.objectName = "does-not-exist";
		cat.type = CatType.BLOB;
		StringWriter out = new StringWriter();
		cat.out = new PrintWriter(out);
		try {
			cat.run();
		} catch (Die e) {
			assertEquals("does-not-exist is not a valid object name", e.getMessage());
		}
	}

	private FileRepository repo() throws Exception {
		FileRepository repo = createWorkRepository();
		write(new File(repo.getWorkTree(), "foo/README"),
				"This project does foo!");
		Git git = new Git(repo);
		git.add().addFilepattern(".").call();
		git.commit().setMessage("Initial commit").call();

		ObjectId head = repo.resolve("head");
		final ObjectLoader ldr = repo.open(head);

		org.eclipse.jgit.lib.Tag tag = new org.eclipse.jgit.lib.Tag(repo);
		tag.setObjId(head);
		tag.setType(Constants.typeString(ldr.getType()));
		tag.setMessage("Tagging for release 0.1b");
		tag.setTag("v0.1b");
		tag.tag();

		return repo;
	}

}

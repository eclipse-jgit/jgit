/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Jonas Fonseca <fonseca@diku.dk>
 * Copyright (C) 2007-2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006, Shawn O. Pearce <spearce@spearce.org>
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

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;

public class RepositoryResolveTest extends SampleDataRepositoryTestCase {

	public void testObjectId_existing() throws IOException {
		assertEquals("49322bb17d3acc9146f98c97d078513228bbf3c0",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0").name());
	}

	public void testObjectId_nonexisting() throws IOException {
		assertEquals("49322bb17d3acc9146f98c97d078513228bbf3c1",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c1").name());
	}

	public void testObjectId_objectid_implicit_firstparent() throws IOException {
		assertEquals("6e1475206e57110fcef4b92320436c1e9872a322",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^").name());
		assertEquals("1203b03dc816ccbb67773f28b3c19318654b0bc8",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^^").name());
		assertEquals("bab66b48f836ed950c99134ef666436fb07a09a0",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^^^").name());
	}

	public void testObjectId_objectid_self() throws IOException {
		assertEquals("49322bb17d3acc9146f98c97d078513228bbf3c0",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^0").name());
		assertEquals("49322bb17d3acc9146f98c97d078513228bbf3c0",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^0^0").name());
		assertEquals("49322bb17d3acc9146f98c97d078513228bbf3c0",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^0^0^0").name());
	}

	public void testObjectId_objectid_explicit_firstparent() throws IOException {
		assertEquals("6e1475206e57110fcef4b92320436c1e9872a322",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^1").name());
		assertEquals("1203b03dc816ccbb67773f28b3c19318654b0bc8",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^1^1").name());
		assertEquals("bab66b48f836ed950c99134ef666436fb07a09a0",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^1^1^1").name());
	}

	public void testObjectId_objectid_explicit_otherparents() throws IOException {
		assertEquals("6e1475206e57110fcef4b92320436c1e9872a322",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^1").name());
		assertEquals("f73b95671f326616d66b2afb3bdfcdbbce110b44",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^2").name());
		assertEquals("d0114ab8ac326bab30e3a657a0397578c5a1af88",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^3").name());
		assertEquals("d0114ab8ac326bab30e3a657a0397578c5a1af88",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^03").name());
	}

	public void testRef_refname() throws IOException {
		assertEquals("49322bb17d3acc9146f98c97d078513228bbf3c0",db.resolve("master^0").name());
		assertEquals("6e1475206e57110fcef4b92320436c1e9872a322",db.resolve("master^").name());
		assertEquals("6e1475206e57110fcef4b92320436c1e9872a322",db.resolve("refs/heads/master^1").name());
	}

	public void testDistance() throws IOException {
		assertEquals("49322bb17d3acc9146f98c97d078513228bbf3c0",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0~0").name());
		assertEquals("6e1475206e57110fcef4b92320436c1e9872a322",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0~1").name());
		assertEquals("1203b03dc816ccbb67773f28b3c19318654b0bc8",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0~2").name());
		assertEquals("bab66b48f836ed950c99134ef666436fb07a09a0",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0~3").name());
		assertEquals("bab66b48f836ed950c99134ef666436fb07a09a0",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0~03").name());
	}

	public void testTree() throws IOException {
		assertEquals("6020a3b8d5d636e549ccbd0c53e2764684bb3125",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^{tree}").name());
		assertEquals("02ba32d3649e510002c21651936b7077aa75ffa9",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^^{tree}").name());
	}

	public void testHEAD() throws IOException {
		assertEquals("6020a3b8d5d636e549ccbd0c53e2764684bb3125",db.resolve("HEAD^{tree}").name());
	}

	public void testDerefCommit() throws IOException {
		assertEquals("49322bb17d3acc9146f98c97d078513228bbf3c0",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^{}").name());
		assertEquals("49322bb17d3acc9146f98c97d078513228bbf3c0",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^{commit}").name());
		// double deref
		assertEquals("6020a3b8d5d636e549ccbd0c53e2764684bb3125",db.resolve("49322bb17d3acc9146f98c97d078513228bbf3c0^{commit}^{tree}").name());
	}

	public void testDerefTag() throws IOException {
		assertEquals("17768080a2318cd89bba4c8b87834401e2095703",db.resolve("refs/tags/B").name());
		assertEquals("d86a2aada2f5e7ccf6f11880bfb9ab404e8a8864",db.resolve("refs/tags/B^{commit}").name());
		assertEquals("032c063ce34486359e3ee3d4f9e5c225b9e1a4c2",db.resolve("refs/tags/B10th").name());
		assertEquals("d86a2aada2f5e7ccf6f11880bfb9ab404e8a8864",db.resolve("refs/tags/B10th^{commit}").name());
		assertEquals("d86a2aada2f5e7ccf6f11880bfb9ab404e8a8864",db.resolve("refs/tags/B10th^{}").name());
		assertEquals("d86a2aada2f5e7ccf6f11880bfb9ab404e8a8864",db.resolve("refs/tags/B10th^0").name());
		assertEquals("d86a2aada2f5e7ccf6f11880bfb9ab404e8a8864",db.resolve("refs/tags/B10th~0").name());
		assertEquals("0966a434eb1a025db6b71485ab63a3bfbea520b6",db.resolve("refs/tags/B10th^").name());
		assertEquals("0966a434eb1a025db6b71485ab63a3bfbea520b6",db.resolve("refs/tags/B10th^1").name());
		assertEquals("0966a434eb1a025db6b71485ab63a3bfbea520b6",db.resolve("refs/tags/B10th~1").name());
		assertEquals("2c349335b7f797072cf729c4f3bb0914ecb6dec9",db.resolve("refs/tags/B10th~2").name());
	}

	public void testDerefBlob() throws IOException {
		assertEquals("fd608fbe625a2b456d9f15c2b1dc41f252057dd7",db.resolve("spearce-gpg-pub^{}").name());
		assertEquals("fd608fbe625a2b456d9f15c2b1dc41f252057dd7",db.resolve("spearce-gpg-pub^{blob}").name());
		assertEquals("fd608fbe625a2b456d9f15c2b1dc41f252057dd7",db.resolve("fd608fbe625a2b456d9f15c2b1dc41f252057dd7^{}").name());
		assertEquals("fd608fbe625a2b456d9f15c2b1dc41f252057dd7",db.resolve("fd608fbe625a2b456d9f15c2b1dc41f252057dd7^{blob}").name());
	}

	public void testDerefTree() throws IOException {
		assertEquals("032c063ce34486359e3ee3d4f9e5c225b9e1a4c2",db.resolve("refs/tags/B10th").name());
		assertEquals("856ec208ae6cadac25a6d74f19b12bb27a24fe24",db.resolve("032c063ce34486359e3ee3d4f9e5c225b9e1a4c2^{tree}").name());
		assertEquals("856ec208ae6cadac25a6d74f19b12bb27a24fe24",db.resolve("refs/tags/B10th^{tree}").name());
	}

	public void testParseGitDescribeOutput() throws IOException {
		ObjectId exp = db.resolve("b");
		assertEquals(exp, db.resolve("B-g7f82283")); // old style
		assertEquals(exp, db.resolve("B-6-g7f82283")); // new style

		assertEquals(exp, db.resolve("B-6-g7f82283^0"));
		assertEquals(exp, db.resolve("B-6-g7f82283^{commit}"));

		try {
			db.resolve("B-6-g7f82283^{blob}");
			fail("expected IncorrectObjectTypeException");
		} catch (IncorrectObjectTypeException badType) {
			// Expected
		}

		assertEquals(db.resolve("b^1"), db.resolve("B-6-g7f82283^1"));
		assertEquals(db.resolve("b~2"), db.resolve("B-6-g7f82283~2"));
	}

	public void testParseLookupPath() throws IOException {
		ObjectId b2_txt = id("10da5895682013006950e7da534b705252b03be6");
		ObjectId b3_b2_txt = id("e6bfff5c1d0f0ecd501552b43a1e13d8008abc31");
		ObjectId b_root = id("acd0220f06f7e4db50ea5ba242f0dfed297b27af");
		ObjectId master_txt = id("82b1d08466e9505f8666b778744f9a3471a70c81");

		assertEquals(b2_txt, db.resolve("b:b/b2.txt"));
		assertEquals(b_root, db.resolve("b:"));
		assertEquals(master_txt, db.resolve(":master.txt"));
		assertEquals(b3_b2_txt, db.resolve("b~3:b/b2.txt"));

		assertNull("no FOO", db.resolve("b:FOO"));
		assertNull("no b/FOO", db.resolve("b:b/FOO"));
		assertNull("no b/FOO", db.resolve(":b/FOO"));
		assertNull("no not-a-branch:", db.resolve("not-a-branch:"));
	}

	private static ObjectId id(String name) {
		return ObjectId.fromString(name);
	}
}

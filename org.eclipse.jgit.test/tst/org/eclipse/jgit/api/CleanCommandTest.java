/*
 * Copyright (C) 2011, Abhishek Bhatnagar <abhatnag@redhat.com>
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
package org.eclipse.jgit.api;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Set;

import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.junit.Test;

/**
 * @author abhishek
 *
 */
public class CleanCommandTest extends RepositoryTestCase {
	private Git git;

	private Set<String> files;

	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);

		// create test files
		writeTrashFile("File1.txt", "Hello world");
		writeTrashFile("File2.txt", "Delete Me");

		// add and commit first file
		git.add().addFilepattern("File1.txt").call();
		git.commit().setMessage("Initial commit").call();
	}

	/**
	 * Test method for {@link org.eclipse.jgit.api.CleanCommand#call()}.
	 */
	@SuppressWarnings("null")
	@Test
	public void testCall() {
		// create status
		StatusCommand command = git.status();
		Status status = null;

		// get untracked files from git
		try {
			status = command.call();
		} catch (NoWorkTreeException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		files = status.getUntracked();

		// run clean
		System.out.println("Running clean...");
		git.clean().call();

		// run test
		assertEquals(0, files.size());
	}

	/**
	 * @param files the file to set
	 */
	public void setFile(Set<String> files) {
		this.files = files;
	}

	/**
	 * @return the file
	 */
	public Set<String> getFile() {
		return files;
	}

}

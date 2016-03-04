/*
 * Copyright (C) 2015, Ivan Motsch <ivan.motsch@bsiag.com>
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
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.experimental.theories.Theories;
import org.junit.runner.RunWith;

/**
 * Unit tests for end-of-line conversion and settings using core.autocrlf, *
 * core.eol and the .gitattributes eol, text, binary (macro for -diff -merge
 * -text)
 */
@RunWith(Theories.class)
public class NativeEolRepositoryTest extends EolRepositoryTest {
	public NativeEolRepositoryTest(String[] testContent) {
		super(testContent);
	}

	@Override
	protected void gitCommit(Git git, String msg) {
		assertEquals(0, exec("git commit -m " + msg,
				git.getRepository().getWorkTree()));
	}

	@Override
	protected void gitAdd(Git git, String path) {
		assertEquals(0, exec("git add .", git.getRepository().getWorkTree()));
	}

	@Override
	protected void gitResetHard(Git git) {
		assertEquals(0,
				exec("git reset --hard", git.getRepository().getWorkTree()));
	}

	@Override
	protected void gitCheckout(Git git, String revstr)
			throws GitAPIException, RevisionSyntaxException, IOException {
		assertEquals(0, exec("git checkout " + revstr,
				git.getRepository().getWorkTree()));
	}

	private static int exec(String command, File dir) {
		byte buffer[]=new byte[8196];
		int ret = -1;
		int length = 1;
		try {
			Process proc = Runtime.getRuntime().exec(command, null, dir);
			InputStream in=proc.getInputStream();
			while (IO.readFully(in, buffer, 0) == buffer.length)
				;
			in = proc.getErrorStream();
			while ((length = IO.readFully(in, buffer, 0)) == buffer.length)
				;
			ret = proc.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (ret != 0)
			System.err.println(RawParseUtils.decode(buffer, 0, length));
		return ret;
	}
}

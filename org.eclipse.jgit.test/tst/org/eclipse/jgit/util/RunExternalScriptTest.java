/*
 * Copyright (C) 2015, Christian Halstrick <christian.halstrick@sap.com>
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

package org.eclipse.jgit.util;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.junit.Before;
import org.junit.Test;

public class RunExternalScriptTest {
	private static final String LF = "\n";

	private ByteArrayOutputStream out;

	private ByteArrayOutputStream err;

	@Before
	public void setUp() throws Exception {
		out = new ByteArrayOutputStream();
		err = new ByteArrayOutputStream();
	}

	@Test
	public void testCopyStdIn() throws IOException, InterruptedException {
		String inputStr = "a\nb\rc\r\nd";
		File script = writeTempFile("cat -");
		int rc = FS.DETECTED.runProcess(
				new ProcessBuilder("sh", script.getPath()), out, err,
				new ByteArrayInputStream(inputStr.getBytes()));
		assertEquals(0, rc);
		assertEquals(inputStr, new String(out.toByteArray()));
		assertEquals("", new String(err.toByteArray()));
	}

	@Test
	public void testCopyNullStdIn() throws IOException, InterruptedException {
		File script = writeTempFile("cat -");
		int rc = FS.DETECTED.runProcess(
				new ProcessBuilder("sh", script.getPath()), out, err,
				(InputStream) null);
		assertEquals(0, rc);
		assertEquals("", new String(out.toByteArray()));
		assertEquals("", new String(err.toByteArray()));
	}

	@Test
	public void testArguments() throws IOException, InterruptedException {
		File script = writeTempFile("echo $#,$1,$2,$3,$4,$5,$6");
		int rc = FS.DETECTED.runProcess(
				new ProcessBuilder("sh",
				script.getPath(), "a", "b", "c"), out, err, (InputStream) null);
		assertEquals(0, rc);
		assertEquals("3,a,b,c,,,\n", new String(out.toByteArray()));
		assertEquals("", new String(err.toByteArray()));
	}

	@Test
	public void testRc() throws IOException, InterruptedException {
		File script = writeTempFile("exit 3");
		int rc = FS.DETECTED.runProcess(
				new ProcessBuilder("sh", script.getPath(), "a", "b", "c"),
				out, err, (InputStream) null);
		assertEquals(3, rc);
		assertEquals("", new String(out.toByteArray()));
		assertEquals("", new String(err.toByteArray()));
	}

	@Test
	public void testNullStdout() throws IOException, InterruptedException {
		File script = writeTempFile("echo hi");
		int rc = FS.DETECTED.runProcess(
				new ProcessBuilder("sh", script.getPath()), null, err,
				(InputStream) null);
		assertEquals(0, rc);
		assertEquals("", new String(out.toByteArray()));
		assertEquals("", new String(err.toByteArray()));
	}

	@Test
	public void testStdErr() throws IOException, InterruptedException {
		File script = writeTempFile("echo hi >&2");
		int rc = FS.DETECTED.runProcess(
				new ProcessBuilder("sh", script.getPath()), null, err,
				(InputStream) null);
		assertEquals(0, rc);
		assertEquals("", new String(out.toByteArray()));
		assertEquals("hi" + LF, new String(err.toByteArray()));
	}

	@Test
	public void testAllTogetherBin() throws IOException, InterruptedException {
		String inputStr = "a\nb\rc\r\nd";
		File script = writeTempFile("echo $#,$1,$2,$3,$4,$5,$6 >&2 ; cat -; exit 5");
		int rc = FS.DETECTED.runProcess(
				new ProcessBuilder("sh", script.getPath(), "a", "b", "c"),
				out, err, new ByteArrayInputStream(inputStr.getBytes()));
		assertEquals(5, rc);
		assertEquals(inputStr, new String(out.toByteArray()));
		assertEquals("3,a,b,c,,," + LF, new String(err.toByteArray()));
	}

	@Test(expected = IOException.class)
	public void testWrongSh() throws IOException, InterruptedException {
		File script = writeTempFile("cat -");
		FS.DETECTED.runProcess(
				new ProcessBuilder("/bin/sh-foo", script.getPath(), "a", "b",
						"c"), out, err, (InputStream) null);
	}

	@Test
	public void testWrongScript() throws IOException, InterruptedException {
		File script = writeTempFile("cat-foo -");
		int rc = FS.DETECTED.runProcess(
				new ProcessBuilder("sh", script.getPath(), "a", "b", "c"),
				out, err, (InputStream) null);
		assertEquals(127, rc);
	}

	@Test
	public void testCopyStdInExecute()
			throws IOException, InterruptedException {
		String inputStr = "a\nb\rc\r\nd";
		File script = writeTempFile("cat -");
		ProcessBuilder pb = new ProcessBuilder("sh", script.getPath());
		ExecutionResult res = FS.DETECTED.execute(pb,
				new ByteArrayInputStream(inputStr.getBytes()));
		assertEquals(0, res.getRc());
		assertEquals(inputStr, new String(res.getStdout().toByteArray()));
		assertEquals("", new String(res.getStderr().toByteArray()));
	}

	@Test
	public void testStdErrExecute() throws IOException, InterruptedException {
		File script = writeTempFile("echo hi >&2");
		ProcessBuilder pb = new ProcessBuilder("sh", script.getPath());
		ExecutionResult res = FS.DETECTED.execute(pb, null);
		assertEquals(0, res.getRc());
		assertEquals("", new String(res.getStdout().toByteArray()));
		assertEquals("hi" + LF, new String(res.getStderr().toByteArray()));
	}

	@Test
	public void testAllTogetherBinExecute()
			throws IOException, InterruptedException {
		String inputStr = "a\nb\rc\r\nd";
		File script = writeTempFile(
				"echo $#,$1,$2,$3,$4,$5,$6 >&2 ; cat -; exit 5");
		ProcessBuilder pb = new ProcessBuilder("sh", script.getPath(), "a",
				"b", "c");
		ExecutionResult res = FS.DETECTED.execute(pb,
				new ByteArrayInputStream(inputStr.getBytes()));
		assertEquals(5, res.getRc());
		assertEquals(inputStr, new String(res.getStdout().toByteArray()));
		assertEquals("3,a,b,c,,," + LF,
				new String(res.getStderr().toByteArray()));
	}

	private File writeTempFile(String body) throws IOException {
		File f = File.createTempFile("RunProcessTestScript_", "");
		JGitTestUtil.write(f, body);
		return f;
	}
}

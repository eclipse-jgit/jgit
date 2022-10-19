/*
 * Copyright (C) 2015, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RunExternalScriptTest {
	private static final String LF = "\n";

	private ByteArrayOutputStream out;

	private ByteArrayOutputStream err;

	@BeforeEach
	public void setUp() throws Exception {
		out = new ByteArrayOutputStream();
		err = new ByteArrayOutputStream();
	}

	@Test
	void testCopyStdIn() throws IOException, InterruptedException {
		String inputStr = "a\nb\rc\r\nd";
		File script = writeTempFile("cat -");
		int rc = FS.DETECTED.runProcess(
				new ProcessBuilder("sh", script.getPath()), out, err,
				new ByteArrayInputStream(inputStr.getBytes(UTF_8)));
		assertEquals(0, rc);
		assertEquals(inputStr, new String(out.toByteArray(), UTF_8));
		assertEquals("", new String(err.toByteArray(), UTF_8));
	}

	@Test
	void testCopyNullStdIn() throws IOException, InterruptedException {
		File script = writeTempFile("cat -");
		int rc = FS.DETECTED.runProcess(
				new ProcessBuilder("sh", script.getPath()), out, err,
				(InputStream) null);
		assertEquals(0, rc);
		assertEquals("", new String(out.toByteArray(), UTF_8));
		assertEquals("", new String(err.toByteArray(), UTF_8));
	}

	@Test
	void testArguments() throws IOException, InterruptedException {
		File script = writeTempFile("echo $#,$1,$2,$3,$4,$5,$6");
		int rc = FS.DETECTED.runProcess(
				new ProcessBuilder("sh",
						script.getPath(), "a", "b", "c"), out, err, (InputStream) null);
		assertEquals(0, rc);
		assertEquals("3,a,b,c,,,\n", new String(out.toByteArray(), UTF_8));
		assertEquals("", new String(err.toByteArray(), UTF_8));
	}

	@Test
	void testRc() throws IOException, InterruptedException {
		File script = writeTempFile("exit 3");
		int rc = FS.DETECTED.runProcess(
				new ProcessBuilder("sh", script.getPath(), "a", "b", "c"),
				out, err, (InputStream) null);
		assertEquals(3, rc);
		assertEquals("", new String(out.toByteArray(), UTF_8));
		assertEquals("", new String(err.toByteArray(), UTF_8));
	}

	@Test
	void testNullStdout() throws IOException, InterruptedException {
		File script = writeTempFile("echo hi");
		int rc = FS.DETECTED.runProcess(
				new ProcessBuilder("sh", script.getPath()), null, err,
				(InputStream) null);
		assertEquals(0, rc);
		assertEquals("", new String(out.toByteArray(), UTF_8));
		assertEquals("", new String(err.toByteArray(), UTF_8));
	}

	@Test
	void testStdErr() throws IOException, InterruptedException {
		File script = writeTempFile("echo hi >&2");
		int rc = FS.DETECTED.runProcess(
				new ProcessBuilder("sh", script.getPath()), null, err,
				(InputStream) null);
		assertEquals(0, rc);
		assertEquals("", new String(out.toByteArray(), UTF_8));
		assertEquals("hi" + LF, new String(err.toByteArray(), UTF_8));
	}

	@Test
	void testAllTogetherBin() throws IOException, InterruptedException {
		String inputStr = "a\nb\rc\r\nd";
		File script = writeTempFile("echo $#,$1,$2,$3,$4,$5,$6 >&2 ; cat -; exit 5");
		int rc = FS.DETECTED.runProcess(
				new ProcessBuilder("sh", script.getPath(), "a", "b", "c"),
				out, err, new ByteArrayInputStream(inputStr.getBytes(UTF_8)));
		assertEquals(5, rc);
		assertEquals(inputStr, new String(out.toByteArray(), UTF_8));
		assertEquals("3,a,b,c,,," + LF, new String(err.toByteArray(), UTF_8));
	}

	@Test
	void testWrongSh() {
		assertThrows(IOException.class, () -> {
			File script = writeTempFile("cat -");
			FS.DETECTED.runProcess(
					new ProcessBuilder("/bin/sh-foo", script.getPath(), "a", "b",
							"c"), out, err, (InputStream) null);
		});
	}

	@Test
	void testWrongScript() throws IOException, InterruptedException {
		File script = writeTempFile("cat-foo -");
		int rc = FS.DETECTED.runProcess(
				new ProcessBuilder("sh", script.getPath(), "a", "b", "c"),
				out, err, (InputStream) null);
		assertEquals(127, rc);
	}

	@Test
	void testCopyStdInExecute()
			throws IOException, InterruptedException {
		String inputStr = "a\nb\rc\r\nd";
		File script = writeTempFile("cat -");
		ProcessBuilder pb = new ProcessBuilder("sh", script.getPath());
		ExecutionResult res = FS.DETECTED.execute(pb,
				new ByteArrayInputStream(inputStr.getBytes(UTF_8)));
		assertEquals(0, res.getRc());
		assertEquals(inputStr,
				new String(res.getStdout().toByteArray(), UTF_8));
		assertEquals("", new String(res.getStderr().toByteArray(), UTF_8));
	}

	@Test
	void testStdErrExecute() throws IOException, InterruptedException {
		File script = writeTempFile("echo hi >&2");
		ProcessBuilder pb = new ProcessBuilder("sh", script.getPath());
		ExecutionResult res = FS.DETECTED.execute(pb, null);
		assertEquals(0, res.getRc());
		assertEquals("", new String(res.getStdout().toByteArray(), UTF_8));
		assertEquals("hi" + LF,
				new String(res.getStderr().toByteArray(), UTF_8));
	}

	@Test
	void testAllTogetherBinExecute()
			throws IOException, InterruptedException {
		String inputStr = "a\nb\rc\r\nd";
		File script = writeTempFile(
				"echo $#,$1,$2,$3,$4,$5,$6 >&2 ; cat -; exit 5");
		ProcessBuilder pb = new ProcessBuilder("sh", script.getPath(), "a",
				"b", "c");
		ExecutionResult res = FS.DETECTED.execute(pb,
				new ByteArrayInputStream(inputStr.getBytes(UTF_8)));
		assertEquals(5, res.getRc());
		assertEquals(inputStr,
				new String(res.getStdout().toByteArray(), UTF_8));
		assertEquals("3,a,b,c,,," + LF,
				new String(res.getStderr().toByteArray(), UTF_8));
	}

	private File writeTempFile(String body) throws IOException {
		File f = File.createTempFile("RunProcessTestScript_", "");
		JGitTestUtil.write(f, body);
		return f;
	}
}

/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.CoreConfig.EolStreamType;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.TreeWalk.OperationType;
import org.eclipse.jgit.util.SystemReader;
import org.junit.Test;

public class CrLfNativeTest extends RepositoryTestCase {

	@Test
	public void checkoutWithCrLfNativeUnix() throws Exception {
		verifyNativeCheckout(new MockSystemReader() {
			{
				setUnix();
			}
		});
	}

	@Test
	public void checkoutWithCrLfNativeWindows() throws Exception {
		verifyNativeCheckout(new MockSystemReader() {
			{
				setWindows();
			}
		});
	}

	private void verifyNativeCheckout(SystemReader systemReader)
			throws Exception {
		SystemReader.setInstance(systemReader);
		Git git = Git.wrap(db);
		FileBasedConfig config = db.getConfig();
		config.setString("core", null, "autocrlf", "false");
		config.setString("core", null, "eol", "native");
		config.save();
		// core.eol is active only if text is set, or if text=auto
		writeTrashFile(".gitattributes", "*.txt text\n");
		File file = writeTrashFile("file.txt", "line 1\nline 2\n");
		git.add().addFilepattern("file.txt").addFilepattern(".gitattributes")
				.call();
		git.commit().setMessage("Initial").call();
		// Check-in with core.eol=native normalization
		assertEquals(
				"[.gitattributes, mode:100644, content:*.txt text\n]"
						+ "[file.txt, mode:100644, content:line 1\nline 2\n]",
				indexState(CONTENT));
		writeTrashFile("file.txt", "something else");
		git.add().addFilepattern("file.txt").call();
		git.commit().setMessage("New commit").call();
		git.reset().setMode(ResetType.HARD).setRef("HEAD~").call();
		// Check-out should convert to the native line separator
		checkFile(file, systemReader.isWindows() ? "line 1\r\nline 2\r\n"
				: "line 1\nline 2\n");
		Status status = git.status().call();
		assertTrue("git status should be clean", status.isClean());
	}

	/**
	 * Verifies the handling of the crlf attribute: crlf == text, -crlf ==
	 * -text, crlf=input == eol=lf
	 *
	 * @throws Exception
	 */
	@Test
	public void testCrLfAttribute() throws Exception {
		FileBasedConfig config = db.getConfig();
		config.setString("core", null, "autocrlf", "false");
		config.setString("core", null, "eol", "crlf");
		config.save();
		writeTrashFile(".gitattributes",
				"*.txt text\n*.crlf crlf\n*.bin -text\n*.nocrlf -crlf\n*.input crlf=input\n*.eol eol=lf");
		writeTrashFile("foo.txt", "");
		writeTrashFile("foo.crlf", "");
		writeTrashFile("foo.bin", "");
		writeTrashFile("foo.nocrlf", "");
		writeTrashFile("foo.input", "");
		writeTrashFile("foo.eol", "");
		Map<String, EolStreamType> inTypes = new HashMap<>();
		Map<String, EolStreamType> outTypes = new HashMap<>();
		try (TreeWalk walk = new TreeWalk(db)) {
			walk.addTree(new FileTreeIterator(db));
			while (walk.next()) {
				String path = walk.getPathString();
				if (".gitattributes".equals(path)) {
					continue;
				}
				EolStreamType in = walk
						.getEolStreamType(OperationType.CHECKIN_OP);
				EolStreamType out = walk
						.getEolStreamType(OperationType.CHECKOUT_OP);
				inTypes.put(path, in);
				outTypes.put(path, out);
			}
		}
		assertEquals("", checkTypes("check-in", inTypes));
		assertEquals("", checkTypes("check-out", outTypes));
	}

	private String checkTypes(String prefix, Map<String, EolStreamType> types) {
		StringBuilder result = new StringBuilder();
		EolStreamType a = types.get("foo.crlf");
		EolStreamType b = types.get("foo.txt");
		report(result, prefix, "crlf != text", a, b);
		a = types.get("foo.nocrlf");
		b = types.get("foo.bin");
		report(result, prefix, "-crlf != -text", a, b);
		a = types.get("foo.input");
		b = types.get("foo.eol");
		report(result, prefix, "crlf=input != eol=lf", a, b);
		return result.toString();
	}

	private void report(StringBuilder result, String prefix, String label,
			EolStreamType a,
			EolStreamType b) {
		if (a == null || b == null || !a.equals(b)) {
			result.append(prefix).append(' ').append(label).append(": ")
					.append(toString(a)).append(" != ").append(toString(b))
					.append('\n');
		}
	}

	private String toString(EolStreamType type) {
		return type == null ? "null" : type.name();
	}
}

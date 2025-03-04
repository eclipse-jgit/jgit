/*
 * Copyright (c) 2024 Qualcomm Innovation Center, Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.diff;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.junit.Test;

public class DiffFormatterBuiltInDriverTest extends RepositoryTestCase {
	@Test
	public void testCppDriver() throws Exception {
		String fileName = "greeting.c";
		String body = Files.readString(
				Path.of(JGitTestUtil.getTestResourceFile(fileName)
						.getAbsolutePath()));
		RevCommit c1;
		RevCommit c2;
		try (Git git = new Git(db)) {
			createCommit(git, ".gitattributes", "*.c   diff=cpp");
			c1 = createCommit(git, fileName, body);
			c2 = createCommit(git, fileName,
					body.replace("Good day", "Greetings")
							.replace("baz", "qux"));
		}
		try (ByteArrayOutputStream os = new ByteArrayOutputStream();
				DiffFormatter diffFormatter = new DiffFormatter(os)) {
			String actual = getHunkHeaders(c1, c2, os, diffFormatter);
			String expected =
					"@@ -27,7 +27,7 @@ void getPersonalizedGreeting(char *result, const char *name, const char *timeOfD\n"
							+ "@@ -37,7 +37,7 @@ int main() {";
			assertEquals(expected, actual);
		}
	}

	@Test
	public void testDtsDriver() throws Exception {
		String fileName = "sample.dtsi";
		String body = Files.readString(
				Path.of(JGitTestUtil.getTestResourceFile(fileName)
						.getAbsolutePath()));
		RevCommit c1;
		RevCommit c2;
		try (Git git = new Git(db)) {
			createCommit(git, ".gitattributes", "*.dtsi   diff=dts");
			c1 = createCommit(git, fileName, body);
			c2 = createCommit(git, fileName,
					body.replace("clock-frequency = <24000000>",
							"clock-frequency = <48000000>"));
		}
		try (ByteArrayOutputStream os = new ByteArrayOutputStream();
				DiffFormatter diffFormatter = new DiffFormatter(os)) {
			String actual = getHunkHeaders(c1, c2, os, diffFormatter);
			String expected = "@@ -20,6 +20,6 @@ uart0: uart@101f1000 {";
			assertEquals(expected, actual);
		}
	}

	@Test
	public void testJavaDriver() throws Exception {
		String resourceName = "greeting.javasource";
		String body = Files.readString(
				Path.of(JGitTestUtil.getTestResourceFile(resourceName)
						.getAbsolutePath()));
		RevCommit c1;
		RevCommit c2;
		try (Git git = new Git(db)) {
			createCommit(git, ".gitattributes", "*.java   diff=java");
			String fileName = "Greeting.java";
			c1 = createCommit(git, fileName, body);
			c2 = createCommit(git, fileName,
					body.replace("Good day", "Greetings")
							.replace("baz", "qux"));
		}
		try (ByteArrayOutputStream os = new ByteArrayOutputStream();
				DiffFormatter diffFormatter = new DiffFormatter(os)) {
			String actual = getHunkHeaders(c1, c2, os, diffFormatter);
			String expected =
					"@@ -22,7 +22,7 @@ public String getPersonalizedGreeting(String name, String timeOfDay) {\n"
							+ "@@ -32,6 +32,6 @@ public static void main(String[] args) {";
			assertEquals(expected, actual);
		}
	}

	@Test
	public void testPythonDriver() throws Exception {
		String fileName = "greeting.py";
		String body = Files.readString(
				Path.of(JGitTestUtil.getTestResourceFile(fileName)
						.getAbsolutePath()));
		RevCommit c1;
		RevCommit c2;
		try (Git git = new Git(db)) {
			createCommit(git, ".gitattributes", "*.py   diff=python");
			c1 = createCommit(git, fileName, body);
			c2 = createCommit(git, fileName,
					body.replace("Good day", "Greetings"));
		}
		try (ByteArrayOutputStream os = new ByteArrayOutputStream();
				DiffFormatter diffFormatter = new DiffFormatter(os)) {
			String actual = getHunkHeaders(c1, c2, os, diffFormatter);
			String expected = "@@ -16,7 +16,7 @@ def get_personalized_greeting(self, name, time_of_day):";
			assertEquals(expected, actual);
		}
	}

	@Test
	public void testRustDriver() throws Exception {
		String fileName = "greeting.rs";
		String body = Files.readString(
				Path.of(JGitTestUtil.getTestResourceFile(fileName)
						.getAbsolutePath()));
		RevCommit c1;
		RevCommit c2;
		try (Git git = new Git(db)) {
			createCommit(git, ".gitattributes", "*.rs   diff=rust");
			c1 = createCommit(git, fileName, body);
			c2 = createCommit(git, fileName,
					body.replace("Good day", "Greetings")
							.replace("baz", "qux"));
		}
		try (ByteArrayOutputStream os = new ByteArrayOutputStream();
				DiffFormatter diffFormatter = new DiffFormatter(os)) {
			String actual = getHunkHeaders(c1, c2, os, diffFormatter);
			String expected =
					"@@ -14,7 +14,7 @@ fn get_personalized_greeting(&self, name: &str, time_of_day: &str) -> String {\n"
							+ "@@ -23,5 +23,5 @@ fn main() {";
			assertEquals(expected, actual);
		}
	}

	private String getHunkHeaders(RevCommit c1, RevCommit c2,
			ByteArrayOutputStream os, DiffFormatter diffFormatter)
			throws IOException {
		diffFormatter.setRepository(db);
		diffFormatter.format(new CanonicalTreeParser(null, db.newObjectReader(),
						c1.getTree()),
				new CanonicalTreeParser(null, db.newObjectReader(),
						c2.getTree()));
		diffFormatter.flush();
		return Arrays.stream(os.toString(StandardCharsets.UTF_8).split("\n"))
				.filter(line -> line.startsWith("@@"))
				.collect(Collectors.joining("\n"));
	}

	private RevCommit createCommit(Git git, String fileName, String body)
			throws IOException, GitAPIException {
		writeTrashFile(fileName, body);
		git.add().addFilepattern(".").call();
		return git.commit().setMessage("message").call();
	}
}

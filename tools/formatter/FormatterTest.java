/*
 * Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.format;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

public class FormatterTest {
	@Test
	public void wholeFileFormatterRejectsChangedLines() {
		String errorText = runError("--check", "--changed-lines", "HEAD",
				"Example.java");
		assertTrue(errorText, errorText.contains("not a valid option"));
	}

	@Test
	public void wholeFileFormattingStripsTrailingWhitespace() throws Exception {
		JdtFormatter formatter = new JdtFormatter(formatterProfile());
		for (String delimiter : List.of("\n", "\r\n", "\r")) {
			String source = String.join(delimiter, "class Example {", "\t/**",
					"\t * Return the value.", "\t * @return the value", "\t */",
					"\tint value() {", "\t\treturn 1;", "\t}", "}", "");
			String expected = String.join(delimiter, "class Example {", "\t/**",
					"\t * Return the value.", "\t *", "\t * @return the value",
					"\t */", "\tint value() {", "\t\treturn 1;", "\t}", "}",
					"");

			assertEquals(expected, formatter.format(source));
		}
	}

	@Test
	public void checkRequiresModeAndExplicitJavaFiles() {
		assertTrue(runError().contains("--check"));
		assertTrue(runError("--check").contains("file.java..."));
		assertTrue(runError("--check", "README.md").contains("README.md"));
		String conflictingModes = runError("--check", "--write",
				"Example.java");
		assertTrue(conflictingModes, conflictingModes.contains("--check"));
		assertTrue(conflictingModes, conflictingModes.contains("--write"));
	}

	@Test
	public void invalidOptionsPrintGeneratedUsage() {
		String errorText = runError("--unknown", "Example.java");

		assertTrue(errorText, errorText.contains("--unknown"));
		assertTrue(errorText, errorText.contains("--check"));
		assertTrue(errorText, errorText.contains("--write"));
		assertTrue(errorText, errorText.contains("--profile"));
		assertTrue(errorText, errorText.contains("file.java..."));
		assertTrue(errorText, errorText
				.contains("Check formatting without modifying files."));
	}

	@Test
	public void missingOptionValuesUseArgs4jErrorsAndGeneratedUsage() {
		for (String option : List.of("--profile")) {
			String errorText = runError("--check", option);
			assertTrue(errorText, errorText.contains(option));
			assertTrue(errorText, errorText.contains("takes an operand"));
			assertTrue(errorText, errorText.contains("--check"));
			assertTrue(errorText, errorText.contains("file.java..."));
		}
	}

	@Test
	public void separatorAndAtPrefixedFilePathsRemainLiteral()
			throws Exception {
		Path directory = Files.createTempDirectory("jgit-format-separator");
		Files.writeString(directory.resolve("--Example.java"),
				"class Example {\n}\n");
		Files.writeString(directory.resolve("@Other.java"),
				"class Other {\n}\n");
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ByteArrayOutputStream error = new ByteArrayOutputStream();

		int result = FormatterMain.run(
				new String[] { "--check", "--profile",
						formatterProfile().toString(), "@Other.java", "--",
						"--Example.java" },
				directory, new PrintStream(output), new PrintStream(error));

		assertEquals(error.toString(StandardCharsets.UTF_8), 0, result);
	}

	@Test
	public void profileAcceptsInlineEqualsSyntax() throws Exception {
		Path directory = Files.createTempDirectory("jgit-format-inline-option");
		Path source = directory.resolve("Example.java");
		Files.writeString(source, "class Example {\n}\n");
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ByteArrayOutputStream error = new ByteArrayOutputStream();

		int result = FormatterMain.run(
				new String[] { "--check", "--profile=" + formatterProfile(),
						source.toString() },
				directory, new PrintStream(output), new PrintStream(error));

		assertEquals(error.toString(StandardCharsets.UTF_8), 0, result);
	}

	@Test
	public void wholeFileFormattingPreservesLineDelimiters() throws Exception {
		JdtFormatter formatter = new JdtFormatter(formatterProfile());
		for (String delimiter : List.of("\n", "\r\n", "\r")) {
			String source = String.join(delimiter, "package p;",
					"class Example{", "void first(){int first=1;}", "}");
			String formatted = formatter.format(source);
			assertTrue(formatted, formatted.contains("int first = 1;"));
			String withoutExpectedDelimiters = formatted.replace(delimiter, "");
			assertFalse(withoutExpectedDelimiters.contains("\n"));
			assertFalse(withoutExpectedDelimiters.contains("\r"));
		}
	}

	@Test
	public void checkDoesNotModifyAnUnformattedFile() throws Exception {
		Path source = Files.createTempFile("jgit-format-check", ".java");
		byte[] original = "class Example{\n}\n"
				.getBytes(StandardCharsets.UTF_8);
		Files.write(source, original);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ByteArrayOutputStream error = new ByteArrayOutputStream();

		int result = FormatterMain.run(
				new String[] { "--check", "--profile",
						formatterProfile().toString(), source.toString() },
				new PrintStream(output), new PrintStream(error));

		assertEquals(error.toString(StandardCharsets.UTF_8), 1, result);
		assertArrayEquals(original, Files.readAllBytes(source));
	}

	@Test
	public void relativeFilesAreResolvedFromTheInvocationDirectory()
			throws Exception {
		Path workingDirectory = Files
				.createTempDirectory("jgit-format-relative");
		Path source = workingDirectory.resolve("Example.java");
		byte[] original = "class Example{\n}\n"
				.getBytes(StandardCharsets.UTF_8);
		Files.write(source, original);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ByteArrayOutputStream error = new ByteArrayOutputStream();

		int result = FormatterMain.run(
				new String[] { "--check", "--profile",
						formatterProfile().toString(), "Example.java" },
				workingDirectory, new PrintStream(output),
				new PrintStream(error));

		assertEquals(error.toString(StandardCharsets.UTF_8), 1, result);
		assertArrayEquals(original, Files.readAllBytes(source));
	}

	@Test
	public void writeFormatsOnlyWhenRequested() throws Exception {
		Path source = Files.createTempFile("jgit-format-write", ".java");
		byte[] original = "class Example{\n}\n"
				.getBytes(StandardCharsets.UTF_8);
		Files.write(source, original);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ByteArrayOutputStream error = new ByteArrayOutputStream();
		String[] writeArgs = { "--write", "--profile",
				formatterProfile().toString(), source.toString() };

		assertEquals(error.toString(StandardCharsets.UTF_8), 0,
				FormatterMain.run(writeArgs, new PrintStream(output),
						new PrintStream(error)));
		byte[] formatted = Files.readAllBytes(source);
		assertNotEquals(new String(original, StandardCharsets.UTF_8),
				new String(formatted, StandardCharsets.UTF_8));
		assertEquals(error.toString(StandardCharsets.UTF_8), 0,
				FormatterMain.run(new String[] { "--check", "--profile",
						formatterProfile().toString(), source.toString() },
						new PrintStream(output), new PrintStream(error)));
	}

	private static String runError(String... args) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ByteArrayOutputStream error = new ByteArrayOutputStream();
		assertEquals(2, FormatterMain.run(args, new PrintStream(output),
				new PrintStream(error)));
		return error.toString(StandardCharsets.UTF_8);
	}

	private static Path formatterProfile() throws Exception {
		String runfilesDir = System.getenv("TEST_SRCDIR");
		if (runfilesDir == null) {
			throw new IllegalStateException("TEST_SRCDIR is not set");
		}
		Path runfiles = Path.of(runfilesDir);
		String workspace = System.getenv("TEST_WORKSPACE");
		for (String candidate : List.of(workspace == null ? "" : workspace,
				"_main", "jgit")) {
			Path profile = runfiles.resolve(candidate)
					.resolve("tools/eclipse-JGit-Format.xml");
			if (Files.isRegularFile(profile)) {
				return profile;
			}
		}
		throw new IllegalStateException(
				"Cannot locate tools/eclipse-JGit-Format.xml in runfiles");
	}
}

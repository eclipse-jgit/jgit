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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class FormatterTest {
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
	public void changedLinesRequiresNonblankBase() {
		String errorText = runError("--check", "--changed-lines", " ",
				"Example.java");

		assertTrue(errorText, errorText
				.contains("--changed-lines requires a non-empty Git base"));
	}

	@Test
	public void invalidOptionsPrintGeneratedUsage() {
		String errorText = runError("--unknown", "Example.java");

		assertTrue(errorText, errorText.contains("--unknown"));
		assertTrue(errorText, errorText.contains("--check"));
		assertTrue(errorText, errorText.contains("--write"));
		assertTrue(errorText, errorText.contains("--profile"));
		assertTrue(errorText, errorText.contains("--changed-lines"));
		assertTrue(errorText, errorText.contains("file.java..."));
		assertTrue(errorText, errorText
				.contains("Check formatting without modifying files."));
	}

	@Test
	public void missingOptionValuesUseArgs4jErrorsAndGeneratedUsage() {
		for (String option : List.of("--profile", "--changed-lines")) {
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
	public void formattingMultipleRangesPreservesLineDelimiters()
			throws Exception {
		JdtFormatter formatter = new JdtFormatter(formatterProfile());
		for (String delimiter : List.of("\n", "\r\n", "\r")) {
			String source = String.join(delimiter, "package p;",
					"class Example{", "void first(){int first=1;}",
					"void second(){int second=2;}", "}");

			String formatted = formatter.format(source, List.of(
					new GitDiff.LineRange(3, 1), new GitDiff.LineRange(4, 1)));

			assertTrue(formatted, formatted.contains("int first = 1;"));
			assertTrue(formatted, formatted.contains("int second = 2;"));
			String withoutExpectedDelimiters = formatted.replace(delimiter, "");
			assertFalse(withoutExpectedDelimiters.contains("\n"));
			assertFalse(withoutExpectedDelimiters.contains("\r"));
		}
	}

	@Test
	public void changedRangesIncludeStagedAndUnstagedChangesAndUntrackedFiles()
			throws Exception {
		Path repository = Files.createTempDirectory("jgit-format-repository");
		Path source = repository.resolve("Example.java");
		Path ignoreFile = repository.resolve(".gitignore");
		Files.writeString(ignoreFile, "ignored-dir/\n");
		Files.writeString(source, "one\ntwo\nthree\nfour\n");

		try (Git git = initRepository(repository)) {
			git.add().addFilepattern(".gitignore")
					.addFilepattern("Example.java").call();
			RevCommit base = commit(git, "base");

			Files.writeString(source, "one\nstaged\ntwo\nthree\nfour\n");
			git.add().addFilepattern("Example.java").call();
			Files.writeString(source, "one\nstaged\ntwo\nnew\nthree\nfour\n");

			assertEquals(
					List.of(new GitDiff.LineRange(2, 1),
							new GitDiff.LineRange(4, 1)),
					GitDiff.changedRanges(repository, base.getName(), source));

			Path untracked = repository.resolve("Untracked.java");
			Files.writeString(untracked, "class New {}\n");
			assertEquals(List.of(new GitDiff.LineRange(1, 1)), GitDiff
					.changedRanges(repository, base.getName(), untracked));

			Path ignored = repository.resolve("ignored-dir/Ignored.java");
			Files.createDirectories(ignored.getParent());
			Files.writeString(ignored, "class Ignored {}\n");
			assertEquals(List.of(new GitDiff.LineRange(1, 1)),
					GitDiff.changedRanges(repository, base.getName(), ignored));
		}
	}

	@Test
	public void deletionOnlyChangesHaveNoNewSideRanges() throws Exception {
		Path repository = Files.createTempDirectory("jgit-format-deletion");
		Path source = repository.resolve("Example.java");
		Files.writeString(source, "one\ntwo\nthree\n");

		try (Git git = initRepository(repository)) {
			git.add().addFilepattern("Example.java").call();
			RevCommit base = commit(git, "base");
			Files.writeString(source, "one\nthree\n");

			assertEquals(List.of(),
					GitDiff.changedRanges(repository, base.getName(), source));
		}
	}

	@Test
	public void changedRangesMatchMyersDefaultDiffAlignment() throws Exception {
		Path repository = Files.createTempDirectory("jgit-format-alignment");
		Path source = repository.resolve("Example.java");
		Files.writeString(source, "a\n");

		try (Git git = initRepository(repository)) {
			git.add().addFilepattern("Example.java").call();
			RevCommit base = commit(git, "base");
			Files.writeString(source, "b\na\na\na\nb\n");

			assertEquals(
					List.of(new GitDiff.LineRange(1, 3),
							new GitDiff.LineRange(5, 1)),
					GitDiff.changedRanges(repository, base.getName(), source));
		}
	}

	@Test
	public void repositoryDiscoverySupportsLinkedWorktrees() throws Exception {
		Path repository = Files.createTempDirectory("jgit-format-main");
		Path linkedWorktree = Files.createTempDirectory("jgit-format-linked");
		try (Git ignored = initRepository(repository)) {
			Path linkedGitDirectory = repository
					.resolve(".git/worktrees/formatter-test");
			Files.createDirectories(linkedGitDirectory);
			Files.writeString(linkedGitDirectory.resolve("commondir"),
					"../..\n");
			Files.writeString(linkedGitDirectory.resolve("gitdir"),
					linkedWorktree.resolve(".git") + "\n");
			Files.writeString(linkedGitDirectory.resolve("HEAD"),
					"ref: refs/heads/formatter-test\n");
			Files.writeString(linkedWorktree.resolve(".git"),
					"gitdir: " + linkedGitDirectory + "\n");
			Files.createDirectories(linkedWorktree.resolve("nested"));
		}

		assertEquals(linkedWorktree.toAbsolutePath().normalize(),
				GitDiff.repositoryRoot(linkedWorktree.resolve("nested")));
	}

	@Test
	public void malformedChangedLinesBaseReportsArgumentError()
			throws Exception {
		Path repository = Files.createTempDirectory("jgit-format-invalid-base");
		Path source = repository.resolve("Example.java");
		Files.writeString(source, "class Example {\n}\n");
		try (Git git = initRepository(repository)) {
			git.add().addFilepattern("Example.java").call();
			commit(git, "base");
		}
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ByteArrayOutputStream error = new ByteArrayOutputStream();
		String base = "HEAD^{invalid}";
		int result = FormatterMain.run(
				new String[] { "--check", "--changed-lines", base, "--profile",
						formatterProfile().toString(), source.toString() },
				repository, new PrintStream(output), new PrintStream(error));
		assertEquals(2, result);
		String errorText = error.toString(StandardCharsets.UTF_8);
		assertTrue(errorText,
				errorText.contains("Cannot resolve Git base: " + base));
	}

	@Test
	public void changedLinesDoesNotRequireNativeGit() throws Exception {
		Path repository = Files.createTempDirectory("jgit-format-headless");
		Path source = repository.resolve("Example.java");
		Files.writeString(source, "class Example {\n}\n");

		try (Git git = initRepository(repository)) {
			git.add().addFilepattern("Example.java").call();
			RevCommit base = commit(git, "base");
			Files.writeString(source, "class Example {\nint value=1;\n}\n");

			ProcessBuilder processBuilder = new ProcessBuilder(
					Path.of(System.getProperty("java.home"), "bin", "java")
							.toString(),
					"-cp", System.getProperty("java.class.path"),
					FormatterMain.class.getName(), "--check", "--changed-lines",
					base.getName(), "--profile", formatterProfile().toString(),
					source.toString());
			processBuilder.environment().put("PATH",
					Files.createTempDirectory("jgit-format-no-git").toString());
			processBuilder.environment().put("BUILD_WORKING_DIRECTORY",
					repository.toString());
			processBuilder.environment().put("BUILD_WORKSPACE_DIRECTORY",
					repository.toString());
			processBuilder.redirectErrorStream(true);
			Process process = processBuilder.start();
			String output = new String(process.getInputStream().readAllBytes(),
					StandardCharsets.UTF_8);

			assertEquals(output, 1, process.waitFor());
			assertTrue(output, output.contains("Needs formatting:"));
		}
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

	private static Git initRepository(Path directory) throws Exception {
		return Git.init().setDirectory(directory.toFile()).call();
	}

	private static RevCommit commit(Git git, String message) throws Exception {
		return git.commit().setMessage(message)
				.setAuthor("Formatter Test", "formatter@example.com")
				.setCommitter("Formatter Test", "formatter@example.com").call();
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

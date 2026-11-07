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

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.Repository;

/** Command-line entry point for the standalone JGit formatter. */
public final class FormatterMain {
	private static final Path DEFAULT_PROFILE = Path
			.of("tools/eclipse-JGit-Format.xml");

	enum Mode {
		CHECK, WRITE
	}

	record Options(Mode mode, Path profile, String changedLinesBase,
			List<Path> files) {
	}

	private FormatterMain() {
	}

	/** Runs the formatter and returns its process exit code. */
	public static int run(String[] args, PrintStream out, PrintStream err) {
		Path currentDirectory = Path.of("").toAbsolutePath();
		Path workingDirectory = environmentPath("BUILD_WORKING_DIRECTORY",
				currentDirectory);
		Path workspaceDirectory = environmentPath("BUILD_WORKSPACE_DIRECTORY",
				workingDirectory);
		return run(args, workingDirectory, workspaceDirectory, out, err);
	}

	static int run(String[] args, Path workingDirectory, PrintStream out,
			PrintStream err) {
		return run(args, workingDirectory, workingDirectory, out, err);
	}

	private static int run(String[] args, Path workingDirectory,
			Path workspaceDirectory, PrintStream out, PrintStream err) {
		try {
			Options options = parseArguments(List.of(args));
			Path profile = resolve(options.profile(), workspaceDirectory);
			JdtFormatter formatter = new JdtFormatter(profile);
			try (Repository repository = options.changedLinesBase() == null
					? null
					: GitDiff.openRepository(workspaceDirectory)) {
				Path repositoryRoot = repository == null ? null
						: GitDiff.repositoryRoot(repository);
				boolean needsFormatting = false;
				for (Path fileArgument : options.files()) {
					Path file = resolve(fileArgument, workingDirectory);
					if (!Files.isRegularFile(file)) {
						throw new IOException(
								"Not a regular file: " + fileArgument);
					}
					String original = Files.readString(file,
							StandardCharsets.UTF_8);
					List<GitDiff.LineRange> ranges;
					if (repository == null) {
						int lineCount = (int) original.lines().count();
						ranges = lineCount == 0 ? List.of()
								: List.of(new GitDiff.LineRange(1, lineCount));
					} else {
						if (!file.startsWith(repositoryRoot)) {
							throw new IOException(
									"File is outside Git repository: "
											+ fileArgument);
						}
						ranges = GitDiff.changedRanges(repository,
								options.changedLinesBase(), file);
					}
					if (ranges.isEmpty()) {
						continue;
					}
					String formatted = formatter.format(original, ranges);
					if (original.equals(formatted)) {
						continue;
					}
					if (options.mode() == Mode.WRITE) {
						Files.writeString(file, formatted,
								StandardCharsets.UTF_8);
						out.println("Formatted " + fileArgument);
					} else {
						out.println("Needs formatting: " + fileArgument);
						needsFormatting = true;
					}
				}
				return needsFormatting ? 1 : 0;
			}
		} catch (IllegalArgumentException e) {
			err.println("jgit-formatter: " + e.getMessage());
			err.println(usage());
			return 2;
		} catch (IOException e) {
			err.println("jgit-formatter: " + e.getMessage());
			return 2;
		}
	}

	static Options parseArguments(List<String> arguments) {
		Mode mode = null;
		Path profile = DEFAULT_PROFILE;
		boolean profileSpecified = false;
		String changedLinesBase = null;
		List<Path> files = new ArrayList<>();
		boolean positionalOnly = false;

		for (int i = 0; i < arguments.size(); i++) {
			String argument = arguments.get(i);
			if (!positionalOnly && "--".equals(argument)) {
				positionalOnly = true;
			} else if (!positionalOnly && "--check".equals(argument)) {
				if (mode != null) {
					throw new IllegalArgumentException(
							"Choose exactly one of --check and --write");
				}
				mode = Mode.CHECK;
			} else if (!positionalOnly && "--write".equals(argument)) {
				if (mode != null) {
					throw new IllegalArgumentException(
							"Choose exactly one of --check and --write");
				}
				mode = Mode.WRITE;
			} else if (!positionalOnly && "--profile".equals(argument)) {
				if (profileSpecified) {
					throw new IllegalArgumentException(
							"--profile may be specified only once");
				}
				profileSpecified = true;
				profile = Path.of(requireValue(arguments, ++i, "--profile"));
			} else if (!positionalOnly && "--changed-lines".equals(argument)) {
				if (changedLinesBase != null) {
					throw new IllegalArgumentException(
							"--changed-lines may be specified only once");
				}
				changedLinesBase = requireValue(arguments, ++i,
						"--changed-lines");
				if (changedLinesBase.isBlank()) {
					throw new IllegalArgumentException(
							"--changed-lines requires a non-empty Git base");
				}
			} else if (!positionalOnly && argument.startsWith("-")) {
				throw new IllegalArgumentException(
						"Unknown option: " + argument);
			} else {
				Path file = Path.of(argument);
				if (file.getFileName() == null
						|| !file.getFileName().toString().endsWith(".java")) {
					throw new IllegalArgumentException(
							"Expected an explicit .java file: " + file);
				}
				files.add(file);
			}
		}

		if (mode == null) {
			throw new IllegalArgumentException(
					"Specify either --check or --write");
		}
		if (files.isEmpty()) {
			throw new IllegalArgumentException(
					"Specify one or more Java source files");
		}
		return new Options(mode, profile, changedLinesBase, List.copyOf(files));
	}

	private static Path resolve(Path path, Path baseDirectory) {
		if (path.isAbsolute()) {
			return path.normalize();
		}
		return baseDirectory.resolve(path).normalize();
	}

	private static Path environmentPath(String variable, Path fallback) {
		String value = System.getenv(variable);
		if (value == null || value.isBlank()) {
			return fallback;
		}
		return Path.of(value).toAbsolutePath().normalize();
	}

	private static String requireValue(List<String> arguments, int index,
			String option) {
		if (index >= arguments.size()
				|| arguments.get(index).startsWith("--")) {
			throw new IllegalArgumentException(option + " requires a value");
		}
		return arguments.get(index);
	}

	private static String usage() {
		return "Usage: format-java (--check | --write) [--changed-lines <base>] "
				+ "[--profile <file>] <file.java>...";
	}

	/** Main method used by the dedicated Bazel formatter target. */
	public static void main(String[] args) {
		int status = run(args, System.out, System.err);
		if (status != 0) {
			System.exit(status);
		}
	}
}

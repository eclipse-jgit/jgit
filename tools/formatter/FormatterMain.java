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

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.ParserProperties;

/** Command-line entry point for the standalone JGit formatter. */
public final class FormatterMain {
	private static final Path DEFAULT_PROFILE = Path
			.of("tools/eclipse-JGit-Format.xml");

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
		Options options = new Options();
		CmdLineParser parser = new CmdLineParser(options,
				ParserProperties.defaults().withAtSyntax(false));
		try {
			List<String> arguments = List.of(args);
			int separator = arguments.indexOf("--");
			int optionEnd = separator < 0 ? arguments.size() : separator;
			parser.parseArgument(arguments.subList(0, optionEnd));
			if (separator >= 0) {
				parser.stopOptionParsing();
				parser.parseArgument(
						arguments.subList(separator + 1, arguments.size()));
			}
			options.validate();
		} catch (CmdLineException | IllegalArgumentException e) {
			reportArgumentError(e.getMessage(), parser, err);
			return 2;
		}
		try {
			return runFormatter(options, workingDirectory, workspaceDirectory,
					out);
		} catch (IOException e) {
			err.println("jgit-formatter: " + e.getMessage());
			return 2;
		}
	}

	private static int runFormatter(Options options, Path workingDirectory,
			Path workspaceDirectory, PrintStream out) throws IOException {
		Path profile = resolve(options.profile.toPath(), workspaceDirectory);
		JdtFormatter formatter = new JdtFormatter(profile);
		try (Repository repository = options.changedLinesBase == null ? null
				: GitDiff.openRepository(workspaceDirectory)) {
			boolean needsFormatting = false;
			for (File fileArgument : options.files) {
				Path file = resolve(fileArgument.toPath(), workingDirectory);
				String formatted = formatFile(file, repository,
						options.changedLinesBase, formatter);
				if (formatted == null) {
					continue;
				}
				if (options.write) {
					Files.writeString(file, formatted, StandardCharsets.UTF_8);
					out.println("Formatted " + fileArgument);
				} else {
					out.println("Needs formatting: " + fileArgument);
					needsFormatting = true;
				}
			}
			return needsFormatting ? 1 : 0;
		}
	}

	private static String formatFile(Path file, Repository repository,
			String base, JdtFormatter formatter) throws IOException {
		if (!Files.isRegularFile(file)) {
			throw new IOException("Not a regular file: " + file);
		}
		String original = Files.readString(file, StandardCharsets.UTF_8);
		String formatted;
		if (repository == null) {
			formatted = formatter.format(original);
		} else {
			List<GitDiff.LineRange> ranges = GitDiff.changedRanges(repository,
					base, file);
			if (ranges.isEmpty()) {
				return null;
			}
			formatted = formatter.format(original, ranges);
		}
		return original.equals(formatted) ? null : formatted;
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

	private static void reportArgumentError(String message,
			CmdLineParser parser, PrintStream err) {
		err.println("jgit-formatter: " + message);
		StringWriter usage = new StringWriter();
		parser.printUsage(usage, null);
		err.print(usage.toString());
	}

	private static final class Options {
		@Option(name = "--check", forbids = "--write", usage = "Check formatting without modifying files.")
		private boolean check;

		@Option(name = "--write", forbids = "--check", usage = "Write formatted files.")
		private boolean write;

		@Option(name = "--profile", metaVar = "FILE", usage = "Formatter profile XML file.")
		private File profile = DEFAULT_PROFILE.toFile();

		@Option(name = "--changed-lines", metaVar = "BASE", usage = "Format only lines changed since this Git base.")
		private String changedLinesBase;

		@Argument(index = 0, metaVar = "file.java...", usage = "Java source files to format.", multiValued = true)
		private List<File> files = new ArrayList<>();

		private void validate() {
			if (check == write) {
				throw new IllegalArgumentException(
						check ? "Choose exactly one of --check and --write"
								: "Specify either --check or --write");
			}
			if (changedLinesBase != null && changedLinesBase.isBlank()) {
				throw new IllegalArgumentException(
						"--changed-lines requires a non-empty Git base");
			}
			if (files.isEmpty()) {
				throw new IllegalArgumentException(
						"Specify one or more Java source files");
			}
			for (File file : files) {
				Path path = file.toPath();
				if (path.getFileName() == null
						|| !path.getFileName().toString().endsWith(".java")) {
					throw new IllegalArgumentException(
							"Expected an explicit .java file: " + path);
				}
			}
		}
	}

	/** Main method used by the dedicated Bazel formatter target. */
	public static void main(String[] args) {
		int status = run(args, System.out, System.err);
		if (status != 0) {
			System.exit(status);
		}
	}
}

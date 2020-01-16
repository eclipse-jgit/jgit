/*
 * Copyright (C) 2015, Sebastien Arod <sebastien.arod@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.ignore;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * This test generates random ignore patterns and random path and compares the
 * output of Cgit check-ignore to the output of {@link FastIgnoreRule}.
 */
public class CGitVsJGitRandomIgnorePatternTest {

	private static class PseudoRandomPatternGenerator {

		private static final int DEFAULT_MAX_FRAGMENTS_PER_PATTERN = 15;

		/**
		 * Generates 75% Special fragments and 25% "standard" characters
		 */
		private static final double DEFAULT_SPECIAL_FRAGMENTS_FREQUENCY = 0.75d;

		private static final List<String> SPECIAL_FRAGMENTS = Arrays.asList(
				"\\", "!", "#", "[", "]", "|", "/", "*", "?", "{", "}", "(",
				")", "\\d", "(", "**", "[a\\]]", "\\ ", "+", "-", "^", "$", ".",
				":", "=", "[[:", ":]]"

		);

		private static final String STANDARD_CHARACTERS = new String(
				"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");

		private final Random random = new Random();

		private final int maxFragmentsPerPattern;

		private final double specialFragmentsFrequency;

		public PseudoRandomPatternGenerator() {
			this(DEFAULT_MAX_FRAGMENTS_PER_PATTERN,
					DEFAULT_SPECIAL_FRAGMENTS_FREQUENCY);
		}

		public PseudoRandomPatternGenerator(int maxFragmentsPerPattern,
				double specialFragmentsFrequency) {
			this.maxFragmentsPerPattern = maxFragmentsPerPattern;
			this.specialFragmentsFrequency = specialFragmentsFrequency;
		}

		public String nextRandomString() {
			StringBuilder builder = new StringBuilder();
			int length = randomFragmentCount();
			for (int i = 0; i < length; i++) {
				if (useSpecialFragment()) {
					builder.append(randomSpecialFragment());
				} else {
					builder.append(randomStandardCharacters());
				}

			}
			return builder.toString();
		}

		private int randomFragmentCount() {
			// We want at least one fragment
			return 1 + random.nextInt(maxFragmentsPerPattern - 1);
		}

		private char randomStandardCharacters() {
			return STANDARD_CHARACTERS
					.charAt(random.nextInt(STANDARD_CHARACTERS.length()));
		}

		private boolean useSpecialFragment() {
			return random.nextDouble() < specialFragmentsFrequency;
		}

		private String randomSpecialFragment() {
			return SPECIAL_FRAGMENTS
					.get(random.nextInt(SPECIAL_FRAGMENTS.size()));
		}
	}

	@SuppressWarnings("serial")
	public static class CgitFatalException extends Exception {

		public CgitFatalException(int cgitExitCode, String pattern, String path,
				String cgitStdError) {
			super("CgitFatalException (" + cgitExitCode + ") for pattern:["
					+ pattern + "] and path:[" + path + "]\n" + cgitStdError);
		}

	}

	public static class CGitIgnoreRule {

		private File gitDir;

		private String pattern;

		public CGitIgnoreRule(File gitDir, String pattern) throws IOException {
			this.gitDir = gitDir;
			this.pattern = pattern;
			Files.write(FileUtils.toPath(new File(gitDir, ".gitignore")),
					(pattern + "\n").getBytes(UTF_8), StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE);
		}

		public boolean isMatch(String path)
				throws IOException, InterruptedException, CgitFatalException {
			Process proc = startCgitCheckIgnore(path);

			String cgitStdOutput = readProcessStream(proc.getInputStream());
			String cgitStdError = readProcessStream(proc.getErrorStream());

			int cgitExitCode = proc.waitFor();

			if (cgitExitCode == 128) {
				throw new CgitFatalException(cgitExitCode, pattern, path,
						cgitStdError);
			}
			return !cgitStdOutput.startsWith("::");
		}

		private Process startCgitCheckIgnore(String path) throws IOException {
			// Use --stdin instead of using argument otherwise paths starting
			// with "-" were interpreted as
			// options by git check-ignore
			String[] command = new String[] { "git", "check-ignore",
					"--no-index", "-v", "-n", "--stdin" };
			Process proc = Runtime.getRuntime().exec(command, new String[0],
					gitDir);
			try (OutputStream out = proc.getOutputStream()) {
				out.write((path + "\n").getBytes(UTF_8));
				out.flush();
			}
			return proc;
		}

		private String readProcessStream(InputStream processStream)
				throws IOException {
			try (BufferedReader stdOut = new BufferedReader(
					new InputStreamReader(processStream))) {

				StringBuilder out = new StringBuilder();
				String s;
				while ((s = stdOut.readLine()) != null) {
					out.append(s);
				}
				return out.toString();
			}
		}
	}

	private static final int NB_PATTERN = 1000;

	private static final int PATH_PER_PATTERN = 1000;

	@Test
	public void testRandomPatterns() throws Exception {
		// Initialize new git repo
		File gitDir = Files.createTempDirectory("jgit").toFile();
		Git.init().setDirectory(gitDir).call();
		PseudoRandomPatternGenerator generator = new PseudoRandomPatternGenerator();

		// Generate random patterns and paths
		for (int i = 0; i < NB_PATTERN; i++) {
			String pattern = generator.nextRandomString();

			FastIgnoreRule jgitIgnoreRule = new FastIgnoreRule(pattern);
			CGitIgnoreRule cgitIgnoreRule = new CGitIgnoreRule(gitDir, pattern);

			// Test path with pattern as path
			assertCgitAndJgitMatch(pattern, jgitIgnoreRule, cgitIgnoreRule,
					pattern);

			for (int p = 0; p < PATH_PER_PATTERN; p++) {
				String path = generator.nextRandomString();
				assertCgitAndJgitMatch(pattern, jgitIgnoreRule, cgitIgnoreRule,
						path);
			}
		}
	}

	@SuppressWarnings({ "boxing" })
	private void assertCgitAndJgitMatch(String pattern,
			FastIgnoreRule jgitIgnoreRule, CGitIgnoreRule cgitIgnoreRule,
			String pathToTest) throws IOException, InterruptedException {

		try {
			boolean cgitMatch = cgitIgnoreRule.isMatch(pathToTest);
			boolean jgitMatch = jgitIgnoreRule.isMatch(pathToTest,
					pathToTest.endsWith("/"));
			if (cgitMatch != jgitMatch) {
				System.err.println(
						buildAssertionToAdd(pattern, pathToTest, cgitMatch));
			}
			Assert.assertEquals("jgit:" + jgitMatch + " <> cgit:" + cgitMatch
					+ " for pattern:[" + pattern + "] and path:[" + pathToTest
					+ "]", cgitMatch, jgitMatch);
		} catch (CgitFatalException e) {
			// Lots of generated patterns or path are rejected by Cgit with a
			// fatal error. We want to ignore them.
		}
	}

	private String buildAssertionToAdd(String pattern, String pathToTest,
			boolean cgitMatch) {
		return "assertMatch(" + toJavaString(pattern) + ", "
				+ toJavaString(pathToTest) + ", " + cgitMatch
				+ " /*cgit result*/);";
	}

	private String toJavaString(String pattern2) {
		return "\"" + pattern2.replace("\\", "\\\\").replace("\"", "\\\"")
				+ "\"";
	}
}

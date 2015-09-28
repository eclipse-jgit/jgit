/*
 * Copyright (C) 2015, Sebastien Arod <sebastien.arod@gmail.com>
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
package org.eclipse.jgit.ignore;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.eclipse.jgit.api.Git;
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

		public CGitIgnoreRule(File gitDir, String pattern)
				throws UnsupportedEncodingException, IOException {
			this.gitDir = gitDir;
			this.pattern = pattern;
			Files.write(new File(gitDir, ".gitignore").toPath(),
					(pattern + "\n").getBytes("UTF-8"),
					StandardOpenOption.CREATE,
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
			OutputStream out = proc.getOutputStream();
			out.write((path + "\n").getBytes("UTF-8"));
			out.flush();
			out.close();
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

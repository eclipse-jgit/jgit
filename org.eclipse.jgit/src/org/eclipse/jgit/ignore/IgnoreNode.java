/*
 * Copyright (C) 2010, 2021 Red Hat Inc. and others
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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.internal.JGitText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a bundle of ignore rules inherited from a base directory.
 *
 * This class is not thread safe, it maintains state about the last match.
 */
public class IgnoreNode {

	private static final Logger LOG = LoggerFactory.getLogger(IgnoreNode.class);

	/** Result from {@link IgnoreNode#isIgnored(String, boolean)}. */
	public enum MatchResult {
		/** The file is not ignored, due to a rule saying its not ignored. */
		NOT_IGNORED,

		/** The file is ignored due to a rule in this node. */
		IGNORED,

		/** The ignore status is unknown, check inherited rules. */
		CHECK_PARENT,

		/**
		 * The first previous (parent) ignore rule match (if any) should be
		 * negated, and then inherited rules applied.
		 *
		 * @since 3.6
		 */
		CHECK_PARENT_NEGATE_FIRST_MATCH;
	}

	/** The rules that have been parsed into this node. */
	private final List<FastIgnoreRule> rules;

	/**
	 * Create an empty ignore node with no rules.
	 */
	public IgnoreNode() {
		this(new ArrayList<>());
	}

	/**
	 * Create an ignore node with given rules.
	 *
	 * @param rules
	 *            list of rules.
	 */
	public IgnoreNode(List<FastIgnoreRule> rules) {
		this.rules = rules;
	}

	/**
	 * Parse files according to gitignore standards.
	 *
	 * @param in
	 *            input stream holding the standard ignore format. The caller is
	 *            responsible for closing the stream.
	 * @throws java.io.IOException
	 *             Error thrown when reading an ignore file.
	 */
	public void parse(InputStream in) throws IOException {
		parse(null, in);
	}

	/**
	 * Parse files according to gitignore standards.
	 *
	 * @param sourceName
	 *            identifying the source of the stream
	 * @param in
	 *            input stream holding the standard ignore format. The caller is
	 *            responsible for closing the stream.
	 * @throws java.io.IOException
	 *             Error thrown when reading an ignore file.
	 * @since 5.11
	 */
	public void parse(String sourceName, InputStream in) throws IOException {
		// Use a custom line reader that treats only '\n' and '\r\n' as line
		// terminators, matching git's own behaviour. BufferedReader.readLine()
		// also treats a bare '\r' as a line terminator, which would split a
		// gitignore pattern like "Icon[\r]" (used by the macOS community
		// gitignore to match the macOS "Icon\r" file) into two lines "Icon["
		// and "]", causing an InvalidPatternException ("Not closed bracket?").
		BufferedReader br = asReader(in);
		String txt;
		int lineNumber = 1;
		while ((txt = readLinePreservingCR(br)) != null) {
			if (txt.length() > 0 && !txt.startsWith("#") && !txt.equals("/")) { //$NON-NLS-1$ //$NON-NLS-2$
				FastIgnoreRule rule = new FastIgnoreRule();
				try {
					rule.parse(txt);
				} catch (InvalidPatternException e) {
					if (sourceName != null) {
						LOG.error(MessageFormat.format(
								JGitText.get().badIgnorePatternFull, sourceName,
								Integer.toString(lineNumber), e.getPattern(),
								e.getLocalizedMessage()), e);
					} else {
						LOG.error(MessageFormat.format(
								JGitText.get().badIgnorePattern,
								e.getPattern()), e);
					}
				}
				if (!rule.isEmpty()) {
					rules.add(rule);
				}
			}
			lineNumber++;
		}
	}

	/**
	 * Read the next line from a {@link BufferedReader}, treating only {@code \n}
	 * and {@code \r\n} as line terminators. Unlike
	 * {@link BufferedReader#readLine()}, a bare {@code \r} that is not followed
	 * by {@code \n} is preserved as part of the line content. This matches
	 * git's own line-reading behaviour for gitignore files.
	 *
	 * @param br
	 *            the reader to read from
	 * @return the next line (without the line terminator), or {@code null} at
	 *         end-of-stream
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	private static String readLinePreservingCR(BufferedReader br)
			throws IOException {
		StringBuilder line = null;
		int ch;
		while ((ch = br.read()) != -1) {
			if (line == null) {
				line = new StringBuilder();
			}
			if (ch == '\n') {
				// LF: end of line (strip the '\n' itself)
				break;
			}
			if (ch == '\r') {
				// Peek at the next character: if it is '\n', consume it and
				// end the line (CRLF ending). If it is not '\n', the '\r' is
				// part of the line content (e.g. inside "Icon[\r]").
				br.mark(1);
				int next = br.read();
				if (next == '\n') {
					// CRLF: end of line
					break;
				}
				if (next != -1) {
					br.reset();
				}
				line.append((char) ch);
				continue;
			}
			line.append((char) ch);
		}
		return line == null ? null : line.toString();
	}

	private static BufferedReader asReader(InputStream in) {
		return new BufferedReader(new InputStreamReader(in, UTF_8));
	}

	/**
	 * Get list of all ignore rules held by this node
	 *
	 * @return list of all ignore rules held by this node
	 */
	public List<FastIgnoreRule> getRules() {
		return Collections.unmodifiableList(rules);
	}

	/**
	 * Determine if an entry path matches an ignore rule.
	 *
	 * @param entryPath
	 *            the path to test. The path must be relative to this ignore
	 *            node's own repository path, and in repository path format
	 *            (uses '/' and not '\').
	 * @param isDirectory
	 *            true if the target item is a directory.
	 * @return status of the path.
	 */
	public MatchResult isIgnored(String entryPath, boolean isDirectory) {
		final Boolean result = checkIgnored(entryPath, isDirectory);
		if (result == null) {
			return MatchResult.CHECK_PARENT;
		}

		return result.booleanValue() ? MatchResult.IGNORED
				: MatchResult.NOT_IGNORED;
	}

	/**
	 * Determine if an entry path matches an ignore rule.
	 *
	 * @param entryPath
	 *            the path to test. The path must be relative to this ignore
	 *            node's own repository path, and in repository path format
	 *            (uses '/' and not '\').
	 * @param isDirectory
	 *            true if the target item is a directory.
	 * @return Boolean.TRUE, if the entry is ignored; Boolean.FALSE, if the
	 *         entry is forced to be not ignored (negated match); or null, if
	 *         undetermined
	 * @since 4.11
	 */
	public @Nullable Boolean checkIgnored(String entryPath,
			boolean isDirectory) {
		// Parse rules in the reverse order that they were read because later
		// rules have higher priority
		for (int i = rules.size() - 1; i > -1; i--) {
			FastIgnoreRule rule = rules.get(i);
			if (rule.isMatch(entryPath, isDirectory, true)) {
				return Boolean.valueOf(rule.getResult());
			}
		}
		return null;
	}

	@Override
	public String toString() {
		return rules.toString();
	}
}

/*
 * Copyright (C) 2010, Red Hat Inc.
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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.lib.Constants;

/**
 * Represents a bundle of ignore rules inherited from a base directory.
 *
 * This class is not thread safe, it maintains state about the last match.
 */
public class IgnoreNode {
	/** Result from {@link IgnoreNode#isIgnored(String, boolean)}. */
	public static enum MatchResult {
		/** The file is not ignored, due to a rule saying its not ignored. */
		NOT_IGNORED,

		/** The file is ignored due to a rule in this node. */
		IGNORED,

		/** The ignore status is unknown, check inherited rules. */
		CHECK_PARENT;
	}

	/** The rules that have been parsed into this node. */
	private final List<IgnoreRule> rules;

	/** Create an empty ignore node with no rules. */
	public IgnoreNode() {
		rules = new ArrayList<IgnoreRule>();
	}

	/**
	 * Create an ignore node with given rules.
	 *
	 * @param rules
	 *            list of rules.
	 **/
	public IgnoreNode(List<IgnoreRule> rules) {
		this.rules = rules;
	}

	/**
	 * Parse files according to gitignore standards.
	 *
	 * @param in
	 *            input stream holding the standard ignore format. The caller is
	 *            responsible for closing the stream.
	 * @throws IOException
	 *             Error thrown when reading an ignore file.
	 */
	public void parse(InputStream in) throws IOException {
		BufferedReader br = asReader(in);
		String txt;
		while ((txt = br.readLine()) != null) {
			txt = txt.trim();
			if (txt.length() > 0 && !txt.startsWith("#") && !txt.equals("/")) //$NON-NLS-1$ //$NON-NLS-2$
				rules.add(new IgnoreRule(txt));
		}
	}

	private static BufferedReader asReader(InputStream in) {
		return new BufferedReader(new InputStreamReader(in, Constants.CHARSET));
	}

	/** @return list of all ignore rules held by this node. */
	public List<IgnoreRule> getRules() {
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
		if (rules.isEmpty())
			return MatchResult.CHECK_PARENT;

		// Parse rules in the reverse order that they were read
		for (int i = rules.size() - 1; i > -1; i--) {
			IgnoreRule rule = rules.get(i);
			if (rule.isMatch(entryPath, isDirectory)) {
				if (rule.getResult())
					return MatchResult.IGNORED;
				else
					return MatchResult.NOT_IGNORED;
			}
		}
		return MatchResult.CHECK_PARENT;
	}
}

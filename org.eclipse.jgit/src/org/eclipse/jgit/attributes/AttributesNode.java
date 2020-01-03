/*
 * Copyright (C) 2010, Red Hat Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.attributes;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a bundle of attributes inherited from a base directory.
 *
 * This class is not thread safe, it maintains state about the last match.
 *
 * @since 3.7
 */
public class AttributesNode {
	/** The rules that have been parsed into this node. */
	private final List<AttributesRule> rules;

	/**
	 * Create an empty ignore node with no rules.
	 */
	public AttributesNode() {
		rules = new ArrayList<>();
	}

	/**
	 * Create an ignore node with given rules.
	 *
	 * @param rules
	 *            list of rules.
	 */
	public AttributesNode(List<AttributesRule> rules) {
		this.rules = rules;
	}

	/**
	 * Parse files according to gitattribute standards.
	 *
	 * @param in
	 *            input stream holding the standard ignore format. The caller is
	 *            responsible for closing the stream.
	 * @throws java.io.IOException
	 *             Error thrown when reading an ignore file.
	 */
	public void parse(InputStream in) throws IOException {
		BufferedReader br = asReader(in);
		String txt;
		while ((txt = br.readLine()) != null) {
			txt = txt.trim();
			if (txt.length() > 0 && !txt.startsWith("#") /* Comments *///$NON-NLS-1$
					&& !txt.startsWith("!") /* Negative pattern forbidden for attributes */) { //$NON-NLS-1$
				int patternEndSpace = txt.indexOf(' ');
				int patternEndTab = txt.indexOf('\t');

				final int patternEnd;
				if (patternEndSpace == -1)
					patternEnd = patternEndTab;
				else if (patternEndTab == -1)
					patternEnd = patternEndSpace;
				else
					patternEnd = Math.min(patternEndSpace, patternEndTab);

				if (patternEnd > -1)
					rules.add(new AttributesRule(txt.substring(0, patternEnd),
							txt.substring(patternEnd + 1).trim()));
			}
		}
	}

	private static BufferedReader asReader(InputStream in) {
		return new BufferedReader(new InputStreamReader(in, UTF_8));
	}

	/**
	 * Getter for the field <code>rules</code>.
	 *
	 * @return list of all ignore rules held by this node
	 */
	public List<AttributesRule> getRules() {
		return Collections.unmodifiableList(rules);
	}

}

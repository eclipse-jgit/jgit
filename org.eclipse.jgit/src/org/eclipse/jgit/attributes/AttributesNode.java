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
package org.eclipse.jgit.attributes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.lib.Constants;

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
		return new BufferedReader(new InputStreamReader(in, Constants.CHARSET));
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

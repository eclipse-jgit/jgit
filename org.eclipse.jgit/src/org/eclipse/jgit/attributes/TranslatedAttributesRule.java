/*
 * Copyright (C) 2016, Ivan Motsch <ivan.motsch@bsiag.com>
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

import java.util.List;

/**
 * A wrapper around an {@link AttributesRule} in order to provide a full
 * repository patch matcher.
 * <p>
 * The {@link AttributesRule#isMatch(String, boolean)} matches against the path
 * relative to the location of the declaration of the rules.
 * <p>
 * This wrapper matches against the filePath relative to the repository root,
 * where the root has path "".
 *
 * @since 4.3
 */
public class TranslatedAttributesRule {

	private final String ruleDeclarationPath;

	private final AttributesRule rule;

	/**
	 * @param ruleDeclarationPath
	 *            is the directory path relative to the repository root where
	 *            the rule is declared
	 * @param rule
	 */
	public TranslatedAttributesRule(String ruleDeclarationPath,
			AttributesRule rule) {
		this.ruleDeclarationPath = ruleDeclarationPath;
		this.rule = rule;
	}

	/**
	 * Returns the attributes.
	 *
	 * @return an unmodifiable list of attributes (never returns
	 *         <code>null</code>)
	 */
	public List<Attribute> getAttributes() {
		return rule.getAttributes();
	}

	/**
	 * Returns <code>true</code> if a match was made.
	 *
	 * @param entryPath
	 *            path relative to the repository root, the root itself has path
	 *            ""
	 * @param isDirectory
	 *            Whether the target file is a directory or not
	 * @return True if a match was made.
	 */
	public boolean isMatch(String entryPath, boolean isDirectory) {
		if (entryPath == null)
			return false;
		if (entryPath.length() == 0)
			return false;
		if (!entryPath.startsWith(ruleDeclarationPath)) {
			return false;
		}
		String relativeTarget = entryPath
				.substring(ruleDeclarationPath.length());
		if (relativeTarget.length() == 0 || ruleDeclarationPath.length() == 0) {
			return rule.isMatch(relativeTarget, isDirectory);
		}
		if (relativeTarget.charAt(0) == '/') {
			return rule.isMatch(relativeTarget.substring(1), isDirectory);
		}
		// false positive in prefix match, e.g. entryPath="foo/bar",
		// ruleDeclarationPath="foo/b"
		return false;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(ruleDeclarationPath);
		sb.append(": "); //$NON-NLS-1$
		sb.append(rule);
		return sb.toString();

	}
}
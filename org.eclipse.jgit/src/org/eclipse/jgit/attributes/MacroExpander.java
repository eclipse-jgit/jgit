/*
 * Copyright (C) 2015, Ivan Motsch <ivan.motsch@bsiag.com>
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

import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.attributes.Attribute.State;

/**
 * Attributes macro expander as described in
 * http://git-scm.com/docs/gitattributes
 *
 * @since 4.2
 */
public class MacroExpander {
	private static final String MACRO_PREFIX = "[attr]"; //$NON-NLS-1$

	private static final String BINARY_RULE_KEY = "binary"; //$NON-NLS-1$

	// [attr]binary -diff -merge -text
	private static final List<Attribute> BINARY_RULE_ATTRIBUTES = new AttributesRule(
			MACRO_PREFIX + BINARY_RULE_KEY, "-diff -merge -text") //$NON-NLS-1$
					.getAttributes();

	private final AttributesNode globalNode;

	private final AttributesNode infoNode;

	private final Map<String, List<Attribute>> expansions = new HashMap<>();

	/**
	 * Create a {@link MacroExpander} with only the default rules
	 */
	public MacroExpander() {
		expansions.put(BINARY_RULE_KEY, BINARY_RULE_ATTRIBUTES);
		globalNode = null;
		infoNode = null;
	}

	/**
	 * Create a {@link MacroExpander} with the default rules and merged rules
	 * from global, info and worktree root attributes
	 *
	 * @param globalNode
	 * @param infoNode
	 * @param rootNode
	 */
	public MacroExpander(@Nullable AttributesNode globalNode,
			@Nullable AttributesNode infoNode,
			@Nullable AttributesNode rootNode) {
		this.globalNode = globalNode;
		this.infoNode = infoNode;
		expansions.put(BINARY_RULE_KEY, BINARY_RULE_ATTRIBUTES);
		for (AttributesNode node : new AttributesNode[] { globalNode, rootNode,
				infoNode }) {
			if (node == null) {
				continue;
			}
			for (AttributesRule rule : node.getRules()) {
				if (rule.getPattern().startsWith(MACRO_PREFIX)) {
					expansions.put(rule.getPattern()
							.substring(MACRO_PREFIX.length()).trim(),
							rule.getAttributes());
				}
			}
		}
	}

	/**
	 * Merges the matching GLOBAL attributes for an entry path.
	 *
	 * @param entryPath
	 *            the path to test. The path must be relative to this attribute
	 *            node's own repository path, and in repository path format
	 *            (uses '/' and not '\').
	 * @param isDirectory
	 *            true if the target item is a directory.
	 * @param result
	 *            that will hold the attributes matching this entry path. This
	 *            method will NOT override any existing entry in attributes.
	 */
	public void mergeGlobalAttributes(String entryPath, boolean isDirectory,
			Attributes result) {
		mergeAttributes(globalNode, entryPath, isDirectory, result);
	}

	/**
	 * Merges the matching INFO attributes for an entry path.
	 *
	 * @param entryPath
	 *            the path to test. The path must be relative to this attribute
	 *            node's own repository path, and in repository path format
	 *            (uses '/' and not '\').
	 * @param isDirectory
	 *            true if the target item is a directory.
	 * @param result
	 *            that will hold the attributes matching this entry path. This
	 *            method will NOT override any existing entry in attributes.
	 */
	public void mergeInfoAttributes(String entryPath, boolean isDirectory,
			Attributes result) {
		mergeAttributes(infoNode, entryPath, isDirectory, result);
	}

	/**
	 * Merges the matching node attributes for an entry path.
	 *
	 * @param node
	 *            the node to scan for matches to entryPath
	 * @param entryPath
	 *            the path to test. The path must be relative to this attribute
	 *            node's own repository path, and in repository path format
	 *            (uses '/' and not '\').
	 * @param isDirectory
	 *            true if the target item is a directory.
	 * @param result
	 *            that will hold the attributes matching this entry path. This
	 *            method will NOT override any existing entry in attributes.
	 */
	public void mergeAttributes(@Nullable AttributesNode node, String entryPath,
			boolean isDirectory, Attributes result) {
		if (node == null)
			return;
		List<AttributesRule> rules = node.getRules();
		// Parse rules in the reverse order that they were read since the last
		// entry should be used
		ListIterator<AttributesRule> ruleIterator = rules
				.listIterator(rules.size());
		while (ruleIterator.hasPrevious()) {
			AttributesRule rule = ruleIterator.previous();
			if (rule.isMatch(entryPath, isDirectory)) {
				ListIterator<Attribute> attributeIte = rule.getAttributes()
						.listIterator(rule.getAttributes().size());
				// Parses the attributes in the reverse order that they were
				// read since the last entry should be used
				while (attributeIte.hasPrevious()) {
					expandMacro(attributeIte.previous(), result);
				}
			}
		}
	}

	/**
	 * @param attr
	 * @param result
	 *            contains the (recursive) expanded and merged macro attributes
	 *            including the attribute iself
	 */
	protected void expandMacro(Attribute attr, Attributes result) {
		// loop detection = exists check
		if (result.containsKey(attr.getKey()))
			return;

		// also add macro to result set, same does native git
		result.put(attr);

		List<Attribute> expansion = expansions.get(attr.getKey());
		if (expansion == null) {
			return;
		}
		switch (attr.getState()) {
		case UNSET: {
			for (Attribute e : expansion) {
				switch (e.getState()) {
				case SET:
					expandMacro(new Attribute(e.getKey(), State.UNSET), result);
					break;
				case UNSET:
					expandMacro(new Attribute(e.getKey(), State.SET), result);
					break;
				case UNSPECIFIED:
					expandMacro(new Attribute(e.getKey(), State.UNSPECIFIED),
							result);
					break;
				case CUSTOM:
				default:
					expandMacro(e, result);
				}
			}
			break;
		}
		case CUSTOM: {
			for (Attribute e : expansion) {
				switch (e.getState()) {
				case SET:
				case UNSET:
				case UNSPECIFIED:
					expandMacro(e, result);
					break;
				case CUSTOM:
				default:
					expandMacro(new Attribute(e.getKey(), attr.getValue()),
							result);
				}
			}
			break;
		}
		case UNSPECIFIED: {
			for (Attribute e : expansion) {
				expandMacro(new Attribute(e.getKey(), State.UNSPECIFIED),
						result);
			}
			break;
		}
		case SET:
		default:
			for (Attribute e : expansion) {
				expandMacro(e, result);
			}
			break;
		}
	}
}

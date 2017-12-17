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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.attributes.Attribute.State;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.TreeWalk.OperationType;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;

/**
 * The attributes handler knows how to retrieve, parse and merge attributes from
 * the various gitattributes files. Furthermore it collects and expands macro
 * expressions. The method {@link #getAttributes()} yields the ready processed
 * attributes for the current path represented by the
 * {@link org.eclipse.jgit.treewalk.TreeWalk}
 * <p>
 * The implementation is based on the specifications in
 * http://git-scm.com/docs/gitattributes
 *
 * @since 4.3
 */
public class AttributesHandler {
	private static final String MACRO_PREFIX = "[attr]"; //$NON-NLS-1$

	private static final String BINARY_RULE_KEY = "binary"; //$NON-NLS-1$

	/**
	 * This is the default <b>binary</b> rule that is present in any git folder
	 * <code>[attr]binary -diff -merge -text</code>
	 */
	private static final List<Attribute> BINARY_RULE_ATTRIBUTES = new AttributesRule(
			MACRO_PREFIX + BINARY_RULE_KEY, "-diff -merge -text") //$NON-NLS-1$
					.getAttributes();

	private final TreeWalk treeWalk;

	private final AttributesNode globalNode;

	private final AttributesNode infoNode;

	private final Map<String, List<Attribute>> expansions = new HashMap<>();

	/**
	 * Create an {@link org.eclipse.jgit.attributes.AttributesHandler} with
	 * default rules as well as merged rules from global, info and worktree root
	 * attributes
	 *
	 * @param treeWalk
	 *            a {@link org.eclipse.jgit.treewalk.TreeWalk}
	 * @throws java.io.IOException
	 */
	public AttributesHandler(TreeWalk treeWalk) throws IOException {
		this.treeWalk = treeWalk;
		AttributesNodeProvider attributesNodeProvider =treeWalk.getAttributesNodeProvider();
		this.globalNode = attributesNodeProvider != null
				? attributesNodeProvider.getGlobalAttributesNode() : null;
		this.infoNode = attributesNodeProvider != null
				? attributesNodeProvider.getInfoAttributesNode() : null;

		AttributesNode rootNode = attributesNode(treeWalk,
				rootOf(
						treeWalk.getTree(WorkingTreeIterator.class)),
				rootOf(
						treeWalk.getTree(DirCacheIterator.class)),
				rootOf(treeWalk
						.getTree(CanonicalTreeParser.class)));

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
	 * See {@link org.eclipse.jgit.treewalk.TreeWalk#getAttributes()}
	 *
	 * @return the {@link org.eclipse.jgit.attributes.Attributes} for the
	 *         current path represented by the
	 *         {@link org.eclipse.jgit.treewalk.TreeWalk}
	 * @throws java.io.IOException
	 */
	public Attributes getAttributes() throws IOException {
		String entryPath = treeWalk.getPathString();
		boolean isDirectory = (treeWalk.getFileMode() == FileMode.TREE);
		Attributes attributes = new Attributes();

		// Gets the info attributes
		mergeInfoAttributes(entryPath, isDirectory, attributes);

		// Gets the attributes located on the current entry path
		mergePerDirectoryEntryAttributes(entryPath, entryPath.lastIndexOf('/'),
				isDirectory,
				treeWalk.getTree(WorkingTreeIterator.class),
				treeWalk.getTree(DirCacheIterator.class),
				treeWalk.getTree(CanonicalTreeParser.class),
				attributes);

		// Gets the attributes located in the global attribute file
		mergeGlobalAttributes(entryPath, isDirectory, attributes);

		// now after all attributes are collected - in the correct hierarchy
		// order - remove all unspecified entries (the ! marker)
		for (Attribute a : attributes.getAll()) {
			if (a.getState() == State.UNSPECIFIED)
				attributes.remove(a.getKey());
		}

		return attributes;
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
	private void mergeGlobalAttributes(String entryPath, boolean isDirectory,
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
	private void mergeInfoAttributes(String entryPath, boolean isDirectory,
			Attributes result) {
		mergeAttributes(infoNode, entryPath, isDirectory, result);
	}

	/**
	 * Merges the matching working directory attributes for an entry path.
	 *
	 * @param entryPath
	 *            the path to test. The path must be relative to this attribute
	 *            node's own repository path, and in repository path format
	 *            (uses '/' and not '\').
	 * @param nameRoot
	 *            index of the '/' preceeding the current level, or -1 if none
	 * @param isDirectory
	 *            true if the target item is a directory.
	 * @param workingTreeIterator
	 * @param dirCacheIterator
	 * @param otherTree
	 * @param result
	 *            that will hold the attributes matching this entry path. This
	 *            method will NOT override any existing entry in attributes.
	 * @throws IOException
	 */
	private void mergePerDirectoryEntryAttributes(String entryPath,
			int nameRoot, boolean isDirectory,
			@Nullable WorkingTreeIterator workingTreeIterator,
			@Nullable DirCacheIterator dirCacheIterator,
			@Nullable CanonicalTreeParser otherTree, Attributes result)
					throws IOException {
		// Prevents infinite recurrence
		if (workingTreeIterator != null || dirCacheIterator != null
				|| otherTree != null) {
			AttributesNode attributesNode = attributesNode(
					treeWalk, workingTreeIterator, dirCacheIterator, otherTree);
			if (attributesNode != null) {
				mergeAttributes(attributesNode,
						entryPath.substring(nameRoot + 1), isDirectory,
						result);
			}
			mergePerDirectoryEntryAttributes(entryPath,
					entryPath.lastIndexOf('/', nameRoot - 1), isDirectory,
					parentOf(workingTreeIterator), parentOf(dirCacheIterator),
					parentOf(otherTree), result);
		}
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
	protected void mergeAttributes(@Nullable AttributesNode node,
			String entryPath,
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
	 * Expand a macro
	 *
	 * @param attr
	 *            a {@link org.eclipse.jgit.attributes.Attribute}
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

	/**
	 * Get the {@link AttributesNode} for the current entry.
	 * <p>
	 * This method implements the fallback mechanism between the index and the
	 * working tree depending on the operation type
	 * </p>
	 *
	 * @param treeWalk
	 * @param workingTreeIterator
	 * @param dirCacheIterator
	 * @param otherTree
	 * @return a {@link AttributesNode} of the current entry,
	 *         {@link NullPointerException} otherwise.
	 * @throws IOException
	 *             It raises an {@link IOException} if a problem appears while
	 *             parsing one on the attributes file.
	 */
	private static AttributesNode attributesNode(TreeWalk treeWalk,
			@Nullable WorkingTreeIterator workingTreeIterator,
			@Nullable DirCacheIterator dirCacheIterator,
			@Nullable CanonicalTreeParser otherTree) throws IOException {
		AttributesNode attributesNode = null;
		switch (treeWalk.getOperationType()) {
		case CHECKIN_OP:
			if (workingTreeIterator != null) {
				attributesNode = workingTreeIterator.getEntryAttributesNode();
			}
			if (attributesNode == null && dirCacheIterator != null) {
				attributesNode = dirCacheIterator
						.getEntryAttributesNode(treeWalk.getObjectReader());
			}
			if (attributesNode == null && otherTree != null) {
				attributesNode = otherTree
						.getEntryAttributesNode(treeWalk.getObjectReader());
			}
			break;
		case CHECKOUT_OP:
			if (otherTree != null) {
				attributesNode = otherTree
						.getEntryAttributesNode(treeWalk.getObjectReader());
			}
			if (attributesNode == null && dirCacheIterator != null) {
				attributesNode = dirCacheIterator
						.getEntryAttributesNode(treeWalk.getObjectReader());
			}
			if (attributesNode == null && workingTreeIterator != null) {
				attributesNode = workingTreeIterator.getEntryAttributesNode();
			}
			break;
		default:
			throw new IllegalStateException(
					"The only supported operation types are:" //$NON-NLS-1$
							+ OperationType.CHECKIN_OP + "," //$NON-NLS-1$
							+ OperationType.CHECKOUT_OP);
		}

		return attributesNode;
	}

	private static <T extends AbstractTreeIterator> T parentOf(@Nullable T node) {
		if(node==null) return null;
		@SuppressWarnings("unchecked")
		Class<T> type = (Class<T>) node.getClass();
		AbstractTreeIterator parent = node.parent;
		if (type.isInstance(parent)) {
			return type.cast(parent);
		}
		return null;
	}

	private static <T extends AbstractTreeIterator> T rootOf(
			@Nullable T node) {
		if(node==null) return null;
		AbstractTreeIterator t=node;
		while (t!= null && t.parent != null) {
			t= t.parent;
		}
		@SuppressWarnings("unchecked")
		Class<T> type = (Class<T>) node.getClass();
		if (type.isInstance(t)) {
			return type.cast(t);
		}
		return null;
	}

}

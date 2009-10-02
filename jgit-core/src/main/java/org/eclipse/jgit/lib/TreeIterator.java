/*
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.lib;

import java.io.IOException;
import java.util.Iterator;

/**
 * A tree iterator iterates over a tree and all its members recursing into
 * subtrees according to order.
 *
 * Default is to only visit leafs. An {@link Order} value can be supplied to
 * make the iteration include Tree nodes as well either before or after the
 * child nodes have been visited.
 */
public class TreeIterator implements Iterator<TreeEntry> {

	private Tree tree;

	private int index;

	private TreeIterator sub;

	private Order order;

	private boolean visitTreeNodes;

	private boolean hasVisitedTree;

	/**
	 * Traversal order
	 */
	public enum Order {
		/**
		 * Visit node first, then leaves
		 */
		PREORDER,

		/**
		 * Visit leaves first, then node
		 */
		POSTORDER
	}

	/**
	 * Construct a {@link TreeIterator} for visiting all non-tree nodes.
	 *
	 * @param start
	 */
	public TreeIterator(Tree start) {
		this(start, Order.PREORDER, false);
	}

	/**
	 * Construct a {@link TreeIterator} visiting all nodes in a tree in a given
	 * order.
	 *
	 * @param start Root node
	 * @param order {@link Order}
	 */
	public TreeIterator(Tree start, Order order) {
		this(start, order, true);
	}

	/**
	 * Construct a {@link TreeIterator}
	 *
	 * @param start First node to visit
	 * @param order Visitation {@link Order}
	 * @param visitTreeNode True to include tree node
	 */
	private TreeIterator(Tree start, Order order, boolean visitTreeNode) {
		this.tree = start;
		this.visitTreeNodes = visitTreeNode;
		this.index = -1;
		this.order = order;
		if (!visitTreeNodes)
			this.hasVisitedTree = true;
		try {
			step();
		} catch (IOException e) {
			throw new Error(e);
		}
	}

	public TreeEntry next() {
		try {
			TreeEntry ret = nextTreeEntry();
			step();
			return ret;
		} catch (IOException e) {
			throw new Error(e);
		}
	}

	private TreeEntry nextTreeEntry() throws IOException {
		TreeEntry ret;
		if (sub != null)
			ret = sub.nextTreeEntry();
		else {
			if (index < 0 && order == Order.PREORDER) {
				return tree;
			}
			if (order == Order.POSTORDER && index == tree.memberCount()) {
				return tree;
			}
			ret = tree.members()[index];
		}
		return ret;
	}

	public boolean hasNext() {
		try {
			return hasNextTreeEntry();
		} catch (IOException e) {
			throw new Error(e);
		}
	}

	private boolean hasNextTreeEntry() throws IOException {
		if (tree == null)
			return false;
		return sub != null
			|| index < tree.memberCount()
			|| order == Order.POSTORDER && index == tree.memberCount();
	}

	private boolean step() throws IOException {
		if (tree == null)
			return false;

		if (sub != null) {
			if (sub.step())
				return true;
			sub = null;
		}

		if (index < 0 && !hasVisitedTree && order == Order.PREORDER) {
			hasVisitedTree = true;
			return true;
		}

		while (++index < tree.memberCount()) {
			TreeEntry e = tree.members()[index];
			if (e instanceof Tree) {
				sub = new TreeIterator((Tree) e, order, visitTreeNodes);
				if (sub.hasNextTreeEntry())
					return true;
				sub = null;
				continue;
			}
			return true;
		}

		if (index == tree.memberCount() && !hasVisitedTree
				&& order == Order.POSTORDER) {
			hasVisitedTree = true;
			return true;
		}
		return false;
	}

	public void remove() {
		throw new IllegalStateException(
				"TreeIterator does not support remove()");
	}
}

/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2007-2008, Robin Rosenberg <robin.rosenberg@dewire.com>
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

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.lib.GitIndex.Entry;

/**
 * Visitor interface for traversing the index and two trees in parallel.
 *
 * When merging we deal with up to two tree nodes and a base node. Then
 * we figure out what to do.
 *
 * A File argument is supplied to allow us to check for modifications in
 * a work tree or update the file.
 */
public interface IndexTreeVisitor {
	/**
	 * Visit a blob, and corresponding tree and index entries.
	 *
	 * @param treeEntry
	 * @param indexEntry
	 * @param file
	 * @throws IOException
	 */
	public void visitEntry(TreeEntry treeEntry, Entry indexEntry, File file) throws IOException;

	/**
	 * Visit a blob, and corresponding tree nodes and associated index entry.
	 *
	 * @param treeEntry
	 * @param auxEntry
	 * @param indexEntry
	 * @param file
	 * @throws IOException
	 */
	public void visitEntry(TreeEntry treeEntry, TreeEntry auxEntry, Entry indexEntry, File file) throws IOException;

	/**
	 * Invoked after handling all child nodes of a tree, during a three way merge
	 *
	 * @param tree
	 * @param auxTree
	 * @param curDir
	 * @throws IOException
	 */
	public void finishVisitTree(Tree tree, Tree auxTree, String curDir) throws IOException;

	/**
	 * Invoked after handling all child nodes of a tree, during two way merge.
	 *
	 * @param tree
	 * @param i
	 * @param curDir
	 * @throws IOException
	 */
	public void finishVisitTree(Tree tree, int i, String curDir) throws IOException;
}

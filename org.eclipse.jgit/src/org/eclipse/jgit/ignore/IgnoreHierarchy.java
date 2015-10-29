/*
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

import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.util.Debug;
import org.eclipse.jgit.util.DotFileTree;
import org.eclipse.jgit.util.StringUtils;

/**
 * Git ignore manager for a repo
 * <p>
 * This instance does lazy loading and caches the detected .gitignore
 *
 * @author imo
 *
 */
public class IgnoreHierarchy {

	private final DotFileTree dotFileTree;

	/**
	 * @param dotFileTree
	 */
	public IgnoreHierarchy(DotFileTree dotFileTree) {
		this.dotFileTree = dotFileTree;
	}


	/**
	 * Determine if the entry path is ignored by an ignore rule. Consider
	 * possible rule negation from child iterator.
	 *
	 * @param path
	 *            the path relative to the repository
	 * @param fileMode
	 * @return true if the entry is ignored by an ignore rule.
	 * @throws IOException
	 *             a relevant ignore rule file exists but cannot be read.
	 */
	public boolean isIgnored(String path, FileMode fileMode)
			throws IOException {
		final boolean isDirectory = FileMode.TREE.equals(fileMode);

		boolean b = isIgnored0(path, isDirectory);
		if (Debug.isDetail())
			Debug.println("  isEntryIgnored " + b + " '" + path + "'"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		return b;
	}

	private boolean isIgnored0(String path, boolean isDirectory)
			throws IOException {
		List<String> pathElements = StringUtils.splitPath(path);
		int folderElementCount = (isDirectory ? pathElements.size()
				: pathElements.size() - 1);

		boolean negateFirstMatch = false;

		if (folderElementCount > 0) {
			// subtree
			for (int i = folderElementCount; i > 0; i--) {
				String folderPath = StringUtils.join(pathElements.subList(0, i),
						"/"); //$NON-NLS-1$
				IgnoreNode node = dotFileTree.getWorkTreeIgnoreNode(folderPath);
				if (node != null) {
					String relativePath = i < pathElements.size()
							? StringUtils.join(pathElements.subList(i,
									pathElements.size()), "/") //$NON-NLS-1$
							: ""; //$NON-NLS-1$
					// The ignore code wants path to start with a '/' if
					// possible.
					// If we have the '/' in our path buffer because we are
					// inside
					// a subdirectory include it in the range we convert to
					// string.
					if (!relativePath.isEmpty()) {
						relativePath = "/" + relativePath; //$NON-NLS-1$
					}
					switch (node.isIgnored(relativePath, isDirectory,
							negateFirstMatch)) {
					case IGNORED:
						return true;
					case NOT_IGNORED:
						return false;
					case CHECK_PARENT:
						negateFirstMatch = false;
						break;
					case CHECK_PARENT_NEGATE_FIRST_MATCH:
						negateFirstMatch = true;
						break;
					}
				}
			}
		}

		// root, info, global
		IgnoreNode[] nodes = new IgnoreNode[] {
				dotFileTree.getWorkTreeIgnoreNode(null),
				dotFileTree.getInfoIgnoreNode(),
				dotFileTree.getGlobalIgnoreNode() };
		for (IgnoreNode node : nodes) {
			if (node != null) {
				switch (node.isIgnored(path, isDirectory, negateFirstMatch)) {
				case IGNORED:
					return true;
				case NOT_IGNORED:
					return false;
				case CHECK_PARENT:
					negateFirstMatch = false;
					break;
				case CHECK_PARENT_NEGATE_FIRST_MATCH:
					negateFirstMatch = true;
					break;
				}
			}
		}

		return false;
	}

}

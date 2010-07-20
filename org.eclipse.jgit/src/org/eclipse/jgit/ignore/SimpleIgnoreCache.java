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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.eclipse.jgit.util.FS;

/**
 * A simple ignore cache. Stores ignore information on .gitignore and exclude files.
 * <br><br>
 * The cache can be initialized by calling {@link #initialize()} on a
 * target file.
 *
 * Inspiration from: Ferry Huberts
 */
public class SimpleIgnoreCache {

	/**
	 * Map of ignore nodes, indexed by base directory. By convention, the
	 * base directory string should NOT start or end with a "/". Use
	 * {@link #relativize(File)} before appending nodes to the ignoreMap
	 * <br>
	 * e.g: path/to/directory is a valid String
	 */
	private HashMap<String, IgnoreNode> ignoreMap;

	//Repository associated with this cache
	private Repository repository;

	//Base directory of this cache
	private URI rootFileURI;

	/**
	 * Creates a base implementation of an ignore cache. This default implementation
	 * will search for all .gitignore files in all children of the base directory,
	 * and grab the exclude file from baseDir/.git/info/exclude.
	 * <br><br>
	 * Call {@link #initialize()} to fetch the ignore information relevant
	 * to a target file.
	 * @param repository
	 * 			  Repository to associate this cache with. The cache's base directory will
	 * 			  be set to this repository's GIT_DIR
	 *
	 */
	public SimpleIgnoreCache(Repository repository) {
		ignoreMap = new HashMap<String, IgnoreNode>();
		this.repository = repository;
		this.rootFileURI = repository.getWorkDir().toURI();
	}

	/**
	 * Initializes the ignore map for the target file and all parents.
	 * This will delete existing ignore information for all folders
	 * on the partial initialization path. Will only function for files
	 * that are children of the cache's basePath.
	 * <br><br>
	 * Note that this does not initialize the ignore rules. Ignore rules will
	 * be parsed when needed during a call to {@link #isIgnored(String)}
	 *
	 * @throws IOException
	 *            The tree could not be walked.
	 */
	public void initialize() throws IOException {
		TreeWalk tw = new TreeWalk(repository);
		tw.reset();
		tw.addTree(new FileTreeIterator(repository.getWorkDir(), FS.DETECTED));
		tw.setFilter(PathSuffixFilter.create("/" + Constants.DOT_GIT_IGNORE));
		tw.setRecursive(true);
		while (tw.next())
			addNodeFromTree(tw.getTree(0, FileTreeIterator.class));

		//The base is special
		//TODO: Test alternate locations for GIT_DIR
		readRulesAtBase();
	}

	/**
	 * Creates rules for .git/info/exclude and .gitignore to the base node.
	 * It will overwrite the existing base ignore node. There will always
	 * be a base ignore node, even if there is no .gitignore file
	 */
	private void readRulesAtBase() {
		//Add .gitignore rules
		File f = new File(repository.getWorkDir(), Constants.DOT_GIT_IGNORE);
		String path = f.getAbsolutePath();
		IgnoreNode n = new IgnoreNode(f.getParentFile());

		//Add exclude rules
		//TODO: Get /info directory without string concat
		path = new File(repository.getDirectory(), "info/exclude").getAbsolutePath();
		f = new File(path);
		if (f.canRead())
			n.addSecondarySource(f);

		ignoreMap.put("", n);
	}

	/**
	 * Adds a node located at the FileTreeIterator's current position.
	 *
	 * @param t
	 *            FileTreeIterator to check for ignore info. The name of the
	 *            entry should be ".gitignore".
	 */
	protected void addNodeFromTree(FileTreeIterator t) {
		IgnoreNode n = ignoreMap.get(relativize(t.getDirectory()));
		long time = t.getEntryLastModified();
		if (n != null) {
			if (n.getLastModified() == time)
				//TODO: Test and optimize
				return;
		}
		n = addIgnoreNode(t.getDirectory());
		n.setLastModified(time);
	}

	/**
	 * Maps the directory to an IgnoreNode, but does not initialize
	 * the IgnoreNode. If a node already exists it will be emptied. Empty nodes
	 * will be initialized when needed, see {@link #isIgnored(String)}
	 *
	 * @param dir
	 * 			  directory to load rules from
	 * @return
	 * 			  true if set successfully, false if directory does not exist
	 * 			  or if directory does not contain a .gitignore file.
	 */
	protected IgnoreNode addIgnoreNode(File dir) {
		String relativeDir = relativize(dir);
		IgnoreNode n = ignoreMap.get(relativeDir);
		if (n != null)
			n.clear();
		else {
			n = new IgnoreNode(dir);
			ignoreMap.put(relativeDir, n);
		}
		return n;
	}

	/**
	 * Returns the ignored status of the file based on the current state
	 * of the ignore nodes. Ignore nodes will not be updated and new ignore
	 * nodes will not be created.
	 * <br><br>
	 * Traverses from highest to lowest priority and quits as soon as a match
	 * is made. If no match is made anywhere, the file is assumed
	 * to be not ignored.
	 *
	 * @param file
	 * 			  Path string relative to Repository.getWorkDir();
	 * @return true
	 * 			  True if file is ignored, false if the file matches a negation statement
	 *            or if there are no rules pertaining to the file.
	 * @throws IOException
	 * 			  Failed to check ignore status
	 */
	public boolean isIgnored(String file) throws IOException{
		String currentPriority = file;

		boolean ignored = false;
		String target = rootFileURI.getPath() + file;
		while (currentPriority.length() > 1) {
			currentPriority = getParent(currentPriority);
			IgnoreNode n = ignoreMap.get(currentPriority);

			if (n != null) {
				ignored = n.isIgnored(target);

				if (n.wasMatched()) {
					if (ignored)
						return ignored;
					else
						target = getParent(target);
				}
			}
		}

		return false;
	}

	/**
	 * String manipulation to get the parent directory of the given path.
	 * It may be more efficient to make a file and call File.getParent().
	 * This function is only called in {@link #initialize}
	 *
	 * @param filePath
	 * 			  Will seek parent directory for this path. Returns empty string
	 * 			  if the filePath does not contain a File.separator
	 * @return
	 * 			  Parent of the filePath, or blank string if non-existent
	 */
	private String getParent(String filePath) {
		int lastSlash = filePath.lastIndexOf("/");
		if (filePath.length() > 0 && lastSlash != -1)
			return filePath.substring(0, lastSlash);
		else
			//This line should be unreachable with the current partiallyInitialize
			return "";
	}

	/**
	 * @param relativePath
	 * 			  Directory to find rules for, should be relative to the repository root
	 * @return
	 * 			  Ignore rules for given base directory, contained in an IgnoreNode
	 */
	public IgnoreNode getRules(String relativePath) {
		return ignoreMap.get(relativePath);
	}

	/**
	 * @return
	 * 			  True if there are no ignore rules in this cache
	 */
	public boolean isEmpty() {
		return ignoreMap.isEmpty();
	}

	/**
	 * Clears the cache
	 */
	public void clear() {
		ignoreMap.clear();
	}

	/**
	 * Returns the relative path versus the repository root.
	 *
	 * @param directory
	 * 			  Directory to find relative path for.
	 * @return
	 * 			  Relative path versus the repository root. This function will
	 * 			  strip the last trailing "/" from its return string
	 */
	private String relativize(File directory) {
		String retVal = rootFileURI.relativize(directory.toURI()).getPath();
		if (retVal.endsWith("/"))
			retVal = retVal.substring(0, retVal.length() - 1);
		return retVal;
	}

}

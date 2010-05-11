/*
 * Copyright (C) 2010, Red Hat Inc.
 * Copyright (C) 2010, Charley Wang <charley.wang@gmail.com>
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
import java.util.HashMap;

import org.eclipse.jgit.lib.Repository;

/**
 * A simple ignore cache. Stores ignore information on .gitignore and exclude files.
 * <br><br>
 * The cache can be initialized by calling {@link #partiallyInitialize(File)} on a
 * target file.
 *
 * Inspiration from: Ferry Huberts
 */
public class SimpleIgnoreCache {

	//Indexed by the base directory
	private HashMap<String, IgnoreNode> ignoreMap;

	//Repository associated with this cache
	private Repository repository;

	//Base directory of this cache
	private String basePath;

	/**
	 * Creates a base implementation of an ignore cache. This default implementation
	 * will search for all .gitignore files in all children of the base directory,
	 * and grab the exclude file from baseDir/.git/info/exclude.
	 * <br><br>
	 * Call {@link #partiallyInitialize(File)} to fetch the ignore information relevant
	 * to a target file.
	 * @param repository
	 * 			  Repository to associate this cache with. The cache's base directory will
	 * 			  be set to this repository's GIT_DIR
	 *
	 */
	public SimpleIgnoreCache(Repository repository) {
		ignoreMap = new HashMap<String, IgnoreNode>();
		this.repository = repository;
		this.basePath = repository.getDirectory().getAbsolutePath();
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
	 * @param targetFile
	 * 			  File to start initialization from. Will initialize all parents of this file.
	 */
	public void partiallyInitialize(File targetFile) {
		//TODO: Optimize to only parse files that have changed, or use listeners
		if (!targetFile.getAbsolutePath().startsWith(basePath))
			return;
		File temp = targetFile.getParentFile();

		while (temp != null && !temp.getAbsolutePath().equals(basePath)) {
			if (temp.isDirectory())
				addIgnoreNode(temp.getAbsolutePath());
			temp = temp.getParentFile();
		}

		if (temp != null && temp.getAbsolutePath().equals(basePath))
			readRulesAtBase();
	}

	/**
	 * Creates rules for .git/info/exclude and .gitignore to the base node.
	 * It will overwrite the existing base ignore node. There will always
	 * be a base ignore node, even if there is no .gitignore file
	 */
	private void readRulesAtBase() {
		//Add .gitignore rules
		String path =  new File(basePath, ".gitignore").getAbsolutePath();
		File f = new File(path);
		IgnoreNode n = new IgnoreNode(f.getParentFile());

		//Add exclude rules
		//TODO: Get /info directory without string concat
		path = new File(repository.getDirectory(), ".git/info/exclude".replaceAll("/", File.separator)).getAbsolutePath();
		f = new File(path);
		if (f.canRead())
			n.addSecondarySource(f);

		ignoreMap.put("", n);
	}

	/**
	 * Maps the directory to an IgnoreNode, but does not initialize
	 * the IgnoreNode. If a node already exists it will be emptied.
	 * The IgnoreNode will be initialized when needed, see {@link #isIgnored(String)}
	 *
	 *
	 * @param directory
	 * 			  directory to load rules from
	 * @return
	 * 			  true if set successfully, false if directory does not exist
	 * 			  or if directory does not contain a .gitignore file.
	 */
	protected boolean addIgnoreNode(String directory) {
		File dir = new File(directory);
		String relativeDir = directory.replaceFirst(basePath, "");

		File ignoreFile = new File(directory, ".gitignore");
		if (!ignoreFile.exists())
			return false;

		if (ignoreMap.get(relativeDir) != null) {
			ignoreMap.get(relativeDir).clear();
			return true;
		}

		IgnoreNode n = new IgnoreNode(dir);
		ignoreMap.put(relativeDir, n);
		return true;
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
	 * 			  Path string relative to Repository.getDirectory();
	 * @return true
	 * 			  True if file is ignored, false if the file matches a negation statement
	 *            or if there are no rules pertaining to the file.
	 * @throws IOException
	 * 			  Failed to check ignore status
	 */
	public boolean isIgnored(String file) throws IOException{
		String currentPriority = file;

		boolean ignored = false;
		while (currentPriority.length() > 1) {
			currentPriority = getParent(currentPriority);
			IgnoreNode n = ignoreMap.get(currentPriority);

			if (n != null) {
				ignored = n.isIgnored(basePath + file);
				if (n.wasMatched())
					return ignored;
			}
		}

		return false;
	}

	/**
	 * String manipulation to get the parent directory of the given path.
	 * It may be more efficient to make a file and call File.getParent().
	 * This function is only called in {@link #partiallyInitialize}
	 *
	 * @param filePath
	 * 			  Will seek parent directory for this path. Returns empty string
	 * 			  if the filePath does not contain a File.separator
	 * @return
	 * 			  Parent of the filePath, or blank string if non-existent
	 */
	private String getParent(String filePath) {
		int lastSlash = filePath.lastIndexOf(File.separator);
		if (filePath.length() > 0 && lastSlash != -1)
			return filePath.substring(0, lastSlash);
		else
			//This line should be unreachable with the current partiallyInitialize
			return "";
	}

	/**
	 * @param base
	 * 			  Directory to find rules for
	 * @return
	 * 			  Ignore rules for given base directory, contained in an IgnoreNode
	 */
	public IgnoreNode getRules(String base) {
		return ignoreMap.get(base);
	}

	/**
	 * @return
	 * 			  True if there are no ignore rules in this cache
	 */
	public boolean isEmpty() {
		return ignoreMap.isEmpty();
	}

	/**
	 * Clears the cache, sets the base path to blank.
	 */
	public void clear() {
		ignoreMap.clear();
		basePath = "";
	}
}

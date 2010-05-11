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

/**
 * A simple ignore cache. Stores ignore information on .gitignore and exclude files.
 *
 * Inspiration from: Ferry Huberts
 */
public class SimpleIgnoreCache {

	//Indexed by the base directory
	private HashMap<String, IgnoreNode> ignoreMap;

	//The base directory of the repository we are caching for.
	private String basePath;

	/**
	 * Creates a base implementation of an ignore cache. This default implementation
	 * will search for all .gitignore files in all children of the base directory,
	 * and grab the exclude file from baseDir/.git/info/exclude.
	 * <br>
	 * Call {@link #partiallyInitialize(File)} to fetch only the ignore information relevant
	 * to the target file.	 *
	 *
	 * @param baseDir
	 */
	public SimpleIgnoreCache(File baseDir) {
		ignoreMap = new HashMap<String, IgnoreNode>();
		this.basePath = baseDir.getAbsolutePath();
	}

	/**
	 * Initializes the ignore map for the target file and all parents.
	 * This will delete existing ignore information for all folders
	 * on the partial initialization path.
	 * <br>
	 * Note that this does not intialize the ignore rules. Ignore rules will
	 * automatically be parsed during a call to {@link #isIgnored(File)}
	 *
	 * @param targetFile
	 * @throws IOException
	 */
	public void partiallyInitialize(File targetFile) throws IOException {
		//TODO: Optimize to only parse files that have changed, or use listeners
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
	 *
	 * @return True if successful, false otherwise
	 * @throws IOException
	 *
	 */
	private boolean readRulesAtBase() throws IOException {
		//Add .gitignore rules
		String path = basePath + "/.gitignore";
		File f = new File(path);
		IgnoreNode n = new IgnoreNode(f.getParentFile());

		//Add exclude rules
		path = basePath + "/.git/info/exclude";
		f = new File(path);
		if (f.canRead())
			n.addSecondarySource(f);

		ignoreMap.put(basePath, n);
		return true;
	}

	/**
	 * Maps the directory to an IgnoreNode, but does not initialize
	 * the IgnoreNode. If a node already exists it will be emptied.
	 * <br>
	 * To initialize the IgnoreNode, see {@link #isIgnored(File)}
	 *
	 *
	 * @param directory -- directory to load rules from
	 * @return true if set successfully, false if directory does not exist
	 * or if directory does not contain a .gitignore file.
	 */
	protected boolean addIgnoreNode(String directory) {
		File dir = new File(directory);

		File ignoreFile = new File(directory + "/.gitignore");
		if (!ignoreFile.exists())
			return false;

		if (ignoreMap.get(directory) != null) {
			ignoreMap.get(directory).clear();
			return true;
		}

		IgnoreNode n = new IgnoreNode(dir);
		ignoreMap.put(directory, n);
		return true;
	}

	/**
	 * Returns the ignored status of the file based on the current state
	 * of the ignore nodes. Empty ignore nodes will be filled, but otherwise
	 * ignore nodes will not be updated and new ignore nodes will not be
	 * created.
	 * <br>
	 * Traverses from highest to lowest priority and quits as soon as a match
	 * is made.
	 * <br>
	 * If no information is available, the file is assumed to be not ignored.
	 *
	 * @param file -- the file to check
	 * @return true if the file is ignored
	 */
	public boolean isIgnored(File file) {
		String currentPriority = file.getAbsolutePath();

		//File is not in repository
		if (!currentPriority.startsWith(basePath))
			return false;

		boolean ignored = false;
		while (currentPriority.length() > 1 && !currentPriority.equals(basePath)) {
			currentPriority = getParent(currentPriority);
			IgnoreNode n = ignoreMap.get(currentPriority);
			if (n != null) {
				try {
					ignored = n.isIgnored(file);
				} catch (IOException e) {
					//TODO: Suggested error action?
					e.printStackTrace();
				}
				if (n.wasMatched())
					return ignored;
			}
		}

		return false;
	}

	/**
	 * String manipulation to get the parent directory of the given path.
	 * It may be more efficient to make a file and call <code>File.getParent()</code>
	 *
	 * @param filePath
	 * @return Parent of the filePath.
	 */
	private String getParent(String filePath) {
		int lastSlash = filePath.lastIndexOf("/");
		if (filePath.length() > 0 && lastSlash != -1)
			return filePath.substring(0, lastSlash);
		else
			return "";
	}

	/**
	 * @param base
	 * @return Ignore rules for given base directory
	 */
	public IgnoreNode getRules(String base) {
		return ignoreMap.get(base);
	}

	/**
	 * @return True if there are no Ignore Rules in this cache
	 */
	public boolean isEmpty() {
		return ignoreMap.isEmpty();
	}

	/**
	 * Clears the cache
	 */
	public void clear() {
		ignoreMap.clear();
		basePath = "";
	}
}

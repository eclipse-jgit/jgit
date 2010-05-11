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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Represents a bundle of ignore rules inherited from a base directory.
 * Each IgnoreNode corresponds to one directory. Most IgnoreNodes will have
 * at most one source of ignore information -- its .gitignore file.
 * <br><br>
 * At the root of the repository, there may be an additional source of
 * ignore information (the exclude file)
 * <br><br>
 * It is recommended that implementers call the {@link #isIgnored(String)} method
 * rather than try to use the rules manually. The method will handle rule priority
 * automatically.
 *
 */
public class IgnoreNode {
	//The base directory will be used to find the .gitignore file
	private File baseDir;
	//Only used for root node.
	private File secondaryFile;
	private ArrayList<IgnoreRule> rules;
	//Indicates whether a match was made. Necessary to terminate early when a negation is encountered
	private boolean matched;

	/**
	 * Create a new ignore node based on the given directory. The node's
	 * ignore file will be the .gitignore file in the directory (if any)
	 * Rules contained within this node will only be applied to files
	 * which are descendants of this directory.
	 *
	 * @param baseDir
	 * 			  base directory of this ignore node
	 */
	public IgnoreNode(File baseDir) {
		this.baseDir = baseDir;
		rules = new ArrayList<IgnoreRule>();
		secondaryFile = null;
	}

	/**
	 * Parse files according to gitignore standards.
	 *
	 * @throws IOException
	 * 			  Error thrown when reading an ignore file.
	 */
	private void parse() throws IOException {
		if (secondaryFile != null && secondaryFile.exists())
			parse(secondaryFile);

		parse(new File(baseDir.getAbsolutePath(), ".gitignore"));
	}

	private void parse(File targetFile) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(targetFile));
		String txt;
		try {
			while ((txt = br.readLine()) != null) {
				txt = txt.trim();
				if (txt.length() > 0 && !txt.startsWith("#"))
					rules.add(new IgnoreRule(txt));
			}
		} catch (IOException e) {
			throw e;
		} finally {
			br.close();
		}
	}

	/**
	 * @return
	 * 			  Base directory to which these rules apply, absolute path
	 */
	public String getBaseDir() {
		return baseDir.getAbsolutePath();
	}


	/**
	 *
	 * @return
	 * 			  List of all ignore rules held by this node
	 */
	public ArrayList<IgnoreRule> getRules() {
		return rules;
	}


	/**
	 *
	 * Returns whether or not a target is matched as being ignored by
	 * any patterns in this directory.
	 * <br>
	 * Will return false if the file is not a descendant of this directory.
	 * <br>
	 *
	 * @param target
	 *			  Absolute path to the file. This makes stripping common path elements easier.
	 * @return
	 * 			  true if target is ignored, false if the target is explicitly not
	 * 			  ignored or if no rules exist for the target.
	 * @throws IOException
	 * 			  Failed to parse rules
	 *
	 */
	public boolean isIgnored(String target) throws IOException {
		matched = false;
		File targetFile = new File(target);
		String tar = target.replaceFirst(getBaseDir(), "");

		if (tar.length() == target.length())
			//target is not a derivative of baseDir, this node has no jurisdiction
			return false;

		if (rules.isEmpty()) {
			//Either we haven't parsed yet, or the file is empty.
			//Empty file should be very fast to parse
			parse();
		}
		if (rules.isEmpty())
			return false;

		/*
		 * Boolean matched is necessary because we may have encountered
		 * a negation ("!/test.c").
		 */

		int i;
		//Parse rules in the reverse order that they were read
		for (i = rules.size() -1; i > -1; i--) {
			matched = rules.get(i).isMatch(tar, targetFile.isDirectory());
			if (matched)
				break;
		}

		if (i > -1 && rules.get(i) != null)
			return rules.get(i).getResult();

		return false;
	}

	/**
	 * @return
	 * 			  True if the previous call to isIgnored resulted in a match,
	 * 			  false otherwise.
	 */
	public boolean wasMatched() {
		return matched;
	}

	/**
	 * Adds another file as a source of ignore rules for this file. The
	 * secondary file will have a lower priority than the first file, and
	 * the parent directory of this node will be regarded as firstFile.getParent()
	 *
	 * @param f
	 * 			Secondary source of gitignore information for this node
	 */
	public void addSecondarySource(File f) {
		secondaryFile = f;
	}

	/**
	 * Clear all rules in this node.
	 */
	public void clear() {
		rules.clear();
	}
}

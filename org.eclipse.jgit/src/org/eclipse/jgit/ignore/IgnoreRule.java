/*******************************************************************************
 * Copyright (c) 2010 Red Hat Inc. and others.
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
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/

package org.eclipse.jgit.ignore;

import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.fnmatch.FileNameMatcher;


/**
 * @author Charley Wang
 *
 * Inspiration from: Ferry Huberts
 *
 */
public class IgnoreRule {
	private String pattern;

	private boolean negation;
	private boolean nameOnly;
	private boolean dirOnly;
	private FileNameMatcher matcher;

	/**
	 * Create a new ignore rule with the given pattern. Assumes that
	 * the pattern comes already trimmed.
	 *
	 * @param pattern
	 */
	public IgnoreRule (String pattern) {
		this.pattern = pattern;
		negation = false;
		nameOnly = false;
		dirOnly = false;
		matcher = null;
		setup();
	}

	/**
	 * Removes leading/trailing characters accordingly.
	 */
	private void setup() {
		int startIndex = 0;
		int endIndex = pattern.length();
		if (pattern.startsWith("!")) {
			startIndex++;
			negation = true;
		}

		if (pattern.endsWith("/")) {
			endIndex --;
			dirOnly = true;
		}

		pattern = pattern.substring(startIndex, endIndex);

		if (!pattern.contains("/")) {
			nameOnly = true;
		} else if (!pattern.startsWith("/")) {
			//Contains "/" but does not start with one
			//Adding / to the start should not interfere with matching
			pattern = "/" + pattern;
		}


		if (pattern.contains("*") || pattern.contains("?") || pattern.contains("[")) {
			try {
				matcher = new FileNameMatcher(pattern, new Character('/'));
			} catch (InvalidPatternException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * @return Whether there was a "!" in front of the pattern
	 */
	public boolean getNegation() {
		return negation;
	}

	/**
	 * A pattern matches name if it does not contain a "/"
	 *
	 * @return Whether to only match names
	 */
	public boolean getNameOnly() {
		return nameOnly;
	}

	/**
	 * @return Whether to only match directories
	 */
	public boolean getDirOnly() {
		return dirOnly;
	}

	/**
	 * @return The pattern, after modifications
	 */
	public String getPattern() {
		return pattern;
	}

	/**
	 * Returns if a match was made.
	 * <br>
	 * This function does NOT return the actual ignore status of the
	 * target! Please consult getResult for the ignore status. The actual ignore
	 * status may be true or false depending on whether this rule is
	 * an ignore rule or a negation rule.
	 *
	 * @param target -- target should be relative to whichever base directory
	 * was used to compile the patterns
	 * @param isDirectory
	 * @return true if a match was made. This does not necessarily mean that
	 * the target is ignored. Consult rule.getResult for the results.
	 */
	public boolean isMatch(String target, boolean isDirectory) {
		if (matcher == null) {
			//Exact match
			if (target.equals(pattern)) {
				if (isDirectory == dirOnly)
					//Directory expectations met
					return true;
				else
					//Directory expectations not met
					return false;
			}

			/*
			 * Add slashes for startsWith check. This avoids matching e.g.
			 * "/src/new" to /src/newfile" but allows "/src/new" to match
			 * "/src/new/newfile", as is the git standard
			 */
			if ((target).startsWith(pattern + "/"))
				return true;

			if (nameOnly) {
				//Iterate through each sub-name
				for (String folderName : target.split("/")) {
					if (folderName.equals(pattern))
						return true;
				}
			}

		} else {
			matcher.append(target);
			if (matcher.isMatch())
				return true;

			if (nameOnly) {
				for (String folderName : target.split("/")) {
					//Iterate through each sub-directory
					matcher.reset();
					matcher.append(folderName);
					if (matcher.isMatch())
						return true;
				}
			} else {
				//TODO: This is the slowest operation
				//This matches e.g. "/src/ne?" to "/src/new/file.c"
				matcher.reset();
				for (String folderName : target.split("/")) {
					if (folderName.length() > 0)
						matcher.append("/" + folderName);

					if (matcher.isMatch())
						return true;
				}
			}


		}

		return false;
	}


	/**
	 * If a call to <code>apply(String targetName)</code> was previously
	 * made, this will return whether or not the target was ignored. Otherwise
	 * this result is meaningless.
	 *
	 * @return True if the target is to be ignored, false otherwise.
	 */
	public boolean getResult() {
		return !negation;
	}

}
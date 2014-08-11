/*******************************************************************************
 * Copyright (c) 2014 Andrey Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrey Loskutov - initial API and implementation
 *******************************************************************************/
package org.eclipse.jgit.fnmatch2;

import static org.eclipse.jgit.fnmatch2.Strings.getPathSeparator;

/**
 * Matcher built from patterns for file names (single path segments). This class
 * is immutable and thread safe.
 *
 * @since 3.5
 */
public class NameMatcher extends AbstractMatcher {

	final boolean beginning;
	final char slash;
	final String subPattern;

	NameMatcher(String pattern, Character pathSeparator, boolean dirOnly){
		super(pattern, dirOnly);
		slash = getPathSeparator(pathSeparator);
		beginning = pattern.length() == 0 ? false : pattern.charAt(0) == slash;
		if(!beginning) {
			this.subPattern = pattern;
		} else {
			this.subPattern = pattern.substring(1, pattern.length());
		}
	}

	public boolean matches(String path, boolean assumeDirectory) {
		int end = 0;
		int firstChar = 0;
		do {
			firstChar = getFirstNotSlash(path, end);
			end = getFirstSlash(path, firstChar);
			boolean match = matches(path, firstChar, end, assumeDirectory);
			if(match){
				boolean isDirMatch = (end > 0 && end != path.length())
						|| assumeDirectory;
				return dirOnly ? isDirMatch : true;
			}
		}
		while(!beginning && end != path.length());
		return false;
	}

	public boolean matches(String segment, int startIncl, int endExcl,
			boolean assumeDirectory) {
		if(subPattern.length() != (endExcl - startIncl)){
			return false;
		}
		int i = 0;
		for (; i < subPattern.length(); i++) {
			char c1 = subPattern.charAt(i);
			char c2 = segment.charAt(i + startIncl);
			if(c1 != c2){
				return false;
			}
		}
		return true;
	}

	private int getFirstNotSlash(String s, int start) {
		int slashIdx = s.indexOf(slash, start);
		return slashIdx == start? start + 1 : start;
	}

	private int getFirstSlash(String s, int start) {
		int slashIdx = s.indexOf(slash, start);
		return slashIdx == -1? s.length() : slashIdx;
	}

}

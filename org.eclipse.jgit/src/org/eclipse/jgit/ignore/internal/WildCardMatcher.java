/*******************************************************************************
 * Copyright (c) 2014 Andrey Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrey Loskutov - initial API and implementation
 *******************************************************************************/
package org.eclipse.jgit.ignore.internal;

import static org.eclipse.jgit.ignore.internal.Strings.convertGlob;

import java.util.regex.Pattern;

import org.eclipse.jgit.errors.InvalidPatternException;

/**
 * Matcher build from path segments containing wildcards. This matcher converts
 * glob wildcards to Java {@link Pattern}'s.
 * <p>
 * This class is immutable and thread safe.
 *
 * @since 3.5
 */
public class WildCardMatcher extends NameMatcher {

	final Pattern p;

	WildCardMatcher(String pattern, Character pathSeparator, boolean dirOnly) throws InvalidPatternException {
		super(pattern, pathSeparator, dirOnly);
		p = convertGlob(subPattern);
	}

	@Override
	public boolean matches(String segment, int startIncl, int endExcl,
			boolean assumeDirectory) {
		return p.matcher(new SubString(segment, startIncl, endExcl)).matches();
	}

	/**
	 * Substring objects share common string to avoid generation of multiple smaller {@link String}
	 * instances during matching.
	 */
	final static class SubString implements CharSequence {
		private final String parent;
		private final int startIncl;
		private final int length;

		public SubString(String parent, int startIncl, int endExcl) {
			this.parent = parent;
			this.startIncl = startIncl;
			this.length = endExcl - startIncl;
			if (startIncl < 0 || endExcl < 0 || length < 0 || startIncl + length > parent.length()) {
				throw new IndexOutOfBoundsException(
						"Trying to create substring on: " + parent //$NON-NLS-1$
								+ " with invalid indices: " + startIncl + " / " + endExcl); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}

		public final int length() {
			return length;
		}

		public final char charAt(int index) {
			return parent.charAt(startIncl + index);
		}

		public final CharSequence subSequence(int start, int end) {
			return new SubString(parent, startIncl + start, startIncl + end);
		}

		@Override
		public final String toString() {
			return parent.substring(startIncl, startIncl + length);
		}
	}
}

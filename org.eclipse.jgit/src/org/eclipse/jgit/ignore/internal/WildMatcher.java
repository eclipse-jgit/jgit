/*
 * Copyright (C) 2014, Andrey Loskutov <loskutov@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.ignore.internal;

/**
 * Wildmatch matcher for "double star" (<code>**</code>) pattern only. This
 * matcher matches any path.
 * <p>
 * This class is immutable and thread safe.
 */
public final class WildMatcher extends AbstractMatcher {

	static final String WILDMATCH = "**"; //$NON-NLS-1$

	// double star for the beginning of pattern
	static final String WILDMATCH2 = "/**"; //$NON-NLS-1$

	WildMatcher(boolean dirOnly) {
		super(WILDMATCH, dirOnly);
	}

	/** {@inheritDoc} */
	@Override
	public final boolean matches(String path, boolean assumeDirectory,
			boolean pathMatch) {
		return !dirOnly || assumeDirectory
				|| (!pathMatch && isSubdirectory(path));
	}

	/** {@inheritDoc} */
	@Override
	public final boolean matches(String segment, int startIncl, int endExcl) {
		return true;
	}

	private static boolean isSubdirectory(String path) {
		final int slashIndex = path.indexOf('/');
		return slashIndex >= 0 && slashIndex < path.length() - 1;
	}
}

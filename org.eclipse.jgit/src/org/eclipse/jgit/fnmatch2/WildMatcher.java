/*******************************************************************************
 * Copyright (c) 2014 Andrey Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrey Loskutov - initial API and implementation
 *******************************************************************************/
package org.eclipse.jgit.fnmatch2;

/**
 * Wildmatch matcher for "double star" (<code>**</code>) pattern only. This
 * matcher matches any path.
 * <p>
 * This class is immutable and thread safe.
 *
 * @since 3.5
 */
public final class WildMatcher extends AbstractMatcher {

	static final String WILDMATCH = "**";
	static final WildMatcher INSTANCE = new WildMatcher();

	private WildMatcher() {
		super(WILDMATCH, false);
	}

	public final boolean matches(String path, boolean assumeDirectory) {
		return true;
	}

	public final boolean matches(String segment, int startIncl, int endExcl,
			boolean assumeDirectory) {
		return true;
	}

}

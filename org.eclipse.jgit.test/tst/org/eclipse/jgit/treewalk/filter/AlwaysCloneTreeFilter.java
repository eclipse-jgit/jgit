/*
 * Copyright (C) 2008, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.treewalk.filter;

import org.eclipse.jgit.treewalk.TreeWalk;

class AlwaysCloneTreeFilter extends TreeFilter {
	@Override
	public TreeFilter clone() {
		return new AlwaysCloneTreeFilter();
	}

	@Override
	public boolean include(TreeWalk walker) {
		return false;
	}

	@Override
	public boolean shouldBeRecursive() {
		return false;
	}
}

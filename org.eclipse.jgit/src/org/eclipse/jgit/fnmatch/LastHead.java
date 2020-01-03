/*
 * Copyright (C) 2008, Florian Koeberle <florianskarten@web.de>
 * Copyright (C) 2008, Florian Köberle <florianskarten@web.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.fnmatch;

import java.util.List;

final class LastHead implements Head {
	static final Head INSTANCE = new LastHead();

	/**
	 * Don't call this constructor, use {@link #INSTANCE}
	 */
	private LastHead() {
		// defined because of javadoc and visibility modifier.
	}

	/** {@inheritDoc} */
	@Override
	public List<Head> getNextHeads(char c) {
		return FileNameMatcher.EMPTY_HEAD_LIST;
	}

}

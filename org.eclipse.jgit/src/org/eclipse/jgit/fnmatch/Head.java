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

interface Head {
	/**
	 * Get the character which decides which heads are returned
	 *
	 * @param c
	 *            the character which decides which heads are returned.
	 * @return a list of heads based on the input.
	 */
	List<Head> getNextHeads(char c);
}

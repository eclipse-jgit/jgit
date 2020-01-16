/*
 * Copyright (C) 2011, 2013 Robin Rosenberg
 * Copyright (C) 2013 Robin Stocker and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util.io;

class Strings {
	static String repeat(String input, int size) {
		StringBuilder sb = new StringBuilder(input.length() * size);
		for (int i = 0; i < size; i++)
			sb.append(input);
		String s = sb.toString();
		return s;
	}
}

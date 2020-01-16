/*
 * Copyright (C) 2011, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Sets {
	@SafeVarargs
	public static <T> Set<T> of(T... elements) {
		Set<T> ret = new HashSet<>();
		ret.addAll(Arrays.asList(elements));
		return ret;
	}
}

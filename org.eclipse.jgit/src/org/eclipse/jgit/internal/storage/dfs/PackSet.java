/*
 * Copyright (C) 2025, Google LLC.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.dfs;

import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/** A set for packs, that uses the packname for uniqueness */
public class PackSet<P extends DfsPackFile> extends TreeSet<P> {
	static final Comparator<DfsPackFile> PACK_NAME_COMPARATOR = Comparator
			.comparing(pack -> pack.getPackDescription().getPackName());

	// Default instance for empty
	public static final PackSet EMPTY = new PackSet(List.of());

	// Create a pack set from a lsit of packs
	public PackSet(List<P> packs) {
		super(PACK_NAME_COMPARATOR);
		addAll(packs);
	}
}

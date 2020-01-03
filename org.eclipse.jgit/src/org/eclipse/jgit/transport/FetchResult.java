/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Final status after a successful fetch from a remote repository.
 *
 * @see Transport#fetch(org.eclipse.jgit.lib.ProgressMonitor, Collection)
 */
public class FetchResult extends OperationResult {
	private final List<FetchHeadRecord> forMerge;

	private final Map<String, FetchResult> submodules;

	FetchResult() {
		forMerge = new ArrayList<>();
		submodules = new HashMap<>();
	}

	void add(FetchHeadRecord r) {
		if (!r.notForMerge)
			forMerge.add(r);
	}

	/**
	 * Add fetch results for a submodule.
	 *
	 * @param path
	 *            the submodule path
	 * @param result
	 *            the fetch result
	 * @since 4.7
	 */
	public void addSubmodule(String path, FetchResult result) {
		submodules.put(path, result);
	}

	/**
	 * Get fetch results for submodules.
	 *
	 * @return Fetch results for submodules as a map of submodule paths to fetch
	 *         results.
	 * @since 4.7
	 */
	public Map<String, FetchResult> submoduleResults() {
		return Collections.unmodifiableMap(submodules);
	}
}

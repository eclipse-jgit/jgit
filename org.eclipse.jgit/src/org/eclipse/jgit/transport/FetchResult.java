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

import org.eclipse.jgit.lib.Ref;

/**
 * Final status after a successful fetch from a remote repository.
 *
 * @see Transport#fetch(org.eclipse.jgit.lib.ProgressMonitor, Collection)
 */
public class FetchResult extends OperationResult {
	private final List<FetchHeadRecord> forMerge;

	private final Map<String, FetchResult> submodules;

	private final Map<String, Ref> fetchedRefs;

	FetchResult() {
		forMerge = new ArrayList<>();
		submodules = new HashMap<>();
		fetchedRefs = new HashMap<>();
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

	/**
	 * Add info about a fetched reference
	 *
	 * @param source
	 *            The source of the refspec that triggered this fetch
	 * @param ref
	 *            The resulting ref fetched from the provided refspec
	 *
	 * @since 5.13
	 */
	public void addFetchedRef(String source, Ref ref) {
		fetchedRefs.put(source, ref);
	}

	/**
	 * Get ref that was requested and successfully fetched.
	 *
	 * @param source
	 *            The source of the refspec
	 * @return The Ref that was fetched as part of the fetch operation,
	 *         <code>null</code> if <code>source</code> does not match a ref
	 *         that was successfully fetched.
	 *
	 * @since 5.13
	 */
	public Ref getFetchedRef(String source) {
		return fetchedRefs.get(source);
	}
}

/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.ketch;

import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

/**
 * A fetch request to obtain objects from a replica, and its result.
 */
public class ReplicaFetchRequest {
	private final Set<String> wantRefs;
	private final Set<ObjectId> wantObjects;
	private Map<String, Ref> refs;

	/**
	 * Construct a new fetch request for a replica.
	 *
	 * @param wantRefs
	 *            named references to be fetched.
	 * @param wantObjects
	 *            specific objects to be fetched.
	 */
	public ReplicaFetchRequest(Set<String> wantRefs,
			Set<ObjectId> wantObjects) {
		this.wantRefs = wantRefs;
		this.wantObjects = wantObjects;
	}

	/**
	 * Get references to be fetched.
	 *
	 * @return references to be fetched.
	 */
	public Set<String> getWantRefs() {
		return wantRefs;
	}

	/**
	 * Get objects to be fetched.
	 *
	 * @return objects to be fetched.
	 */
	public Set<ObjectId> getWantObjects() {
		return wantObjects;
	}

	/**
	 * Get remote references, usually from the advertisement.
	 *
	 * @return remote references, usually from the advertisement.
	 */
	@Nullable
	public Map<String, Ref> getRefs() {
		return refs;
	}

	/**
	 * Set references observed from the replica.
	 *
	 * @param refs
	 *            references observed from the replica.
	 */
	public void setRefs(Map<String, Ref> refs) {
		this.refs = refs;
	}
}

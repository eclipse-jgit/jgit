/*
 * Copyright (C) 2021, Tencent.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.commitgraph;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.CommitGraph;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;

/**
 * Per-object state used by
 * {@link org.eclipse.jgit.internal.storage.commitgraph.CommitGraphWriter}
 */
class GraphCommitData extends ObjectIdOwnerMap.Entry {

	private int generation = CommitGraph.GENERATION_NOT_COMPUTED;

	private int oidPosition = -1;

	/**
	 * Initialize this entry with a specific ObjectId.
	 *
	 * @param id
	 *            the id the entry represents.
	 */
	GraphCommitData(AnyObjectId id) {
		super(id);
	}

	int getGeneration() {
		return generation;
	}

	void setGeneration(int generation) {
		this.generation = generation;
	}

	int getOidPosition() {
		return oidPosition;
	}

	void setOidPosition(int oidPosition) {
		this.oidPosition = oidPosition;
	}
}

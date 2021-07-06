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

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_COMMIT_GRAPH_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_COMPUTE_GENERATION;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

/**
 * Configuration used by a commit-graph writer when constructing the stream.
 */
public class CommitGraphConfig {

	/**
	 * Default value of compute generation option: {@value}
	 *
	 * @see #setComputeGeneration(boolean)
	 */
	public static final boolean DEFAULT_COMPUTE_GENERATION = true;

	private boolean computeGeneration = DEFAULT_COMPUTE_GENERATION;

	/**
	 * Create a default configuration.
	 */
	public CommitGraphConfig() {
	}

	/**
	 * Create a configuration honoring the repository's settings.
	 *
	 * @param db
	 *            the repository to read settings from. The repository is not
	 *            retained by the new configuration, instead its settings are
	 *            copied during the constructor.
	 */
	public CommitGraphConfig(Repository db) {
		fromConfig(db.getConfig());
	}

	/**
	 * Create a configuration honoring settings in a
	 * {@link org.eclipse.jgit.lib.Config}.
	 *
	 * @param cfg
	 *            the source to read settings from. The source is not retained
	 *            by the new configuration, instead its settings are copied
	 *            during the constructor.
	 */
	public CommitGraphConfig(Config cfg) {
		fromConfig(cfg);
	}

	/**
	 * Checks whether to compute generation numbers.
	 *
	 * @return {@code true} if the writer should compute generation numbers.
	 */
	public boolean isComputeGeneration() {
		return computeGeneration;
	}

	/**
	 * Whether the writer should compute generation numbers.
	 *
	 * Default setting: {@value #DEFAULT_COMPUTE_GENERATION}
	 *
	 * @param computeGeneration
	 *            if {@code true} the commit-graph will include the computed
	 *            generation numbers.
	 */
	public void setComputeGeneration(boolean computeGeneration) {
		this.computeGeneration = computeGeneration;
	}

	/**
	 * Update properties by setting fields from the configuration.
	 *
	 * If a property's corresponding variable is not defined in the supplied
	 * configuration, then it is left unmodified.
	 *
	 * @param rc
	 *            configuration to read properties from.
	 */
	public void fromConfig(Config rc) {
		computeGeneration = rc.getBoolean(CONFIG_COMMIT_GRAPH_SECTION,
				CONFIG_KEY_COMPUTE_GENERATION, computeGeneration);
	}
}

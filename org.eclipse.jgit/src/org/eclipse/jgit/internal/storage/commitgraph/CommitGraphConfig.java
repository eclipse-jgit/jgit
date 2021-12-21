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
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_COMPUTE_CHANGED_PATHS;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_COMPUTE_GENERATION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_MAX_NEW_FILTERS;

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

	/**
	 * Default if we compute changed paths when write commit-graph: {@value}
	 *
	 * @see #setComputeChangedPaths(boolean)
	 */
	public static final boolean DEFAULT_COMPUTE_CHANGED_PATHS = false;

	/**
	 * Default value of maximum number of new bloom filters: @{value}
	 *
	 * @see #setMaxNewFilters(int)
	 */
	public static final int DEFAULT_MAX_NEW_FILTERS = -1;

	private boolean computeGeneration = DEFAULT_COMPUTE_GENERATION;

	private boolean computeChangedPaths = DEFAULT_COMPUTE_CHANGED_PATHS;

	private int maxNewFilters = DEFAULT_MAX_NEW_FILTERS;

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
	 * True is writer is allowed to compute and write information about the
	 * paths changed between a commit and its first parent.
	 *
	 * Default setting: {@value #DEFAULT_COMPUTE_CHANGED_PATHS}
	 *
	 * @return whether to compute changed paths
	 */
	public boolean isComputeChangedPaths() {
		return computeChangedPaths;
	}

	/**
	 * Enable computing and writing information about the paths changed between
	 * a commit and its first parent.
	 *
	 * This operation can take a while on large repositories. It provides
	 * significant performance gains for getting history of a directory or a
	 * file with {@code git log -- <path>}.
	 *
	 * Default setting: {@value #DEFAULT_COMPUTE_CHANGED_PATHS}
	 *
	 * @param computeChangedPaths
	 *            true to compute and write changed paths
	 */
	public void setComputeChangedPaths(boolean computeChangedPaths) {
		this.computeChangedPaths = computeChangedPaths;
	}

	/**
	 * Get the maximum number of new bloom filters. Only commits present in the
	 * new layer count against this limit.
	 *
	 * Default setting: {@value #DEFAULT_MAX_NEW_FILTERS}
	 *
	 * @return the maximum number of new bloom filters.
	 */
	public int getMaxNewFilters() {
		return maxNewFilters;
	}

	/**
	 * Set the maximum number of new bloom filters.
	 *
	 * With tht value n, generate at most n new Bloom Filters.(if
	 * {@link #isComputeChangedPaths()} is true) If n is -1, no limit is
	 * enforced. Only commits present in the new layer count against this limit.
	 *
	 * Default setting: {@value #DEFAULT_MAX_NEW_FILTERS}
	 *
	 * @param n
	 *            maximum number of new bloom filters
	 */
	public void setMaxNewFilters(int n) {
		this.maxNewFilters = n;
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
		computeChangedPaths = rc.getBoolean(CONFIG_COMMIT_GRAPH_SECTION,
				CONFIG_KEY_COMPUTE_CHANGED_PATHS, computeChangedPaths);
		maxNewFilters = rc.getInt(CONFIG_COMMIT_GRAPH_SECTION,
				CONFIG_KEY_MAX_NEW_FILTERS, maxNewFilters);
	}
}

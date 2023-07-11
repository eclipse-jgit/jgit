/*
 * Copyright (C) 2023, Google LLC
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.commitgraph;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;

/**
 * Configuration used by a commit graph writer when constructing the stream.
 */
public class CommitGraphConfig {
	private int generationVersion;

	private int hashsz;

	/**
	 * Create a default configuration.
	 */
	public CommitGraphConfig() {
		this.generationVersion = 1;
		this.hashsz = OBJECT_ID_LENGTH;
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
	 * Get the generation version used by Commit Graph.
	 *
	 * @return {@code value} 1 = topological order, 2 = topological order +
	 *         corrected time date.
	 */
	public int getGenerationVersion() {
		return generationVersion;
	}

	/**
	 * Set the generation version used by Commit Graph.
	 *
	 * @param generationVersion
	 *            1 = topological order, 2 = topological order + corrected time
	 *            date.
	 */
	public void setGenerationVersion(int generationVersion) {
		this.generationVersion = generationVersion;
	}

	/**
	 * Get the hash length used by Commit Graph.
	 *
	 * @return {@code value}
	 */
	public int getHashsz() {
		return hashsz;
	}

	/**
	 * Set the hash length used by Commit Graph.
	 *
	 * @param hashsz
	 *            the hash length being used
	 */
	public void setHashsz(int hashsz) {
		this.hashsz = hashsz;
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
		generationVersion = rc.getInt("commitGraph", "generationVersion", //$NON-NLS-1$//$NON-NLS-2$
				generationVersion);
		generationVersion = rc.getInt("commitGraph", "hashLength", hashsz); //$NON-NLS-1$ //$NON-NLS-2$
	}
}

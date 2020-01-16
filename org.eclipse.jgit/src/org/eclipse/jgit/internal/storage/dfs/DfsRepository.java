/*
 * Copyright (C) 2011, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.dfs;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Collections;

import org.eclipse.jgit.attributes.AttributesNode;
import org.eclipse.jgit.attributes.AttributesNodeProvider;
import org.eclipse.jgit.attributes.AttributesRule;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;

/**
 * A Git repository on a DFS.
 */
public abstract class DfsRepository extends Repository {
	private final DfsConfig config;

	private final DfsRepositoryDescription description;

	/**
	 * Initialize a DFS repository.
	 *
	 * @param builder
	 *            description of the repository.
	 */
	protected DfsRepository(DfsRepositoryBuilder builder) {
		super(builder);
		this.config = new DfsConfig();
		this.description = builder.getRepositoryDescription();
	}

	/** {@inheritDoc} */
	@Override
	public abstract DfsObjDatabase getObjectDatabase();

	/**
	 * Get the description of this repository.
	 *
	 * @return the description of this repository.
	 */
	public DfsRepositoryDescription getDescription() {
		return description;
	}

	/**
	 * Check if the repository already exists.
	 *
	 * @return true if the repository exists; false if it is new.
	 * @throws java.io.IOException
	 *             the repository cannot be checked.
	 */
	public boolean exists() throws IOException {
		if (getRefDatabase() instanceof DfsRefDatabase) {
			return ((DfsRefDatabase) getRefDatabase()).exists();
		}
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public void create(boolean bare) throws IOException {
		if (exists())
			throw new IOException(MessageFormat.format(
					JGitText.get().repositoryAlreadyExists, "")); //$NON-NLS-1$

		String master = Constants.R_HEADS + Constants.MASTER;
		RefUpdate.Result result = updateRef(Constants.HEAD, true).link(master);
		if (result != RefUpdate.Result.NEW)
			throw new IOException(result.name());
	}

	/** {@inheritDoc} */
	@Override
	public StoredConfig getConfig() {
		return config;
	}

	/** {@inheritDoc} */
	@Override
	public String getIdentifier() {
		return getDescription().getRepositoryName();
	}

	/** {@inheritDoc} */
	@Override
	public void scanForRepoChanges() throws IOException {
		getRefDatabase().refresh();
		getObjectDatabase().clearCache();
	}

	/** {@inheritDoc} */
	@Override
	public void notifyIndexChanged(boolean internal) {
		// Do not send notifications.
		// There is no index, as there is no working tree.
	}

	/** {@inheritDoc} */
	@Override
	public ReflogReader getReflogReader(String refName) throws IOException {
		throw new UnsupportedOperationException();
	}

	/** {@inheritDoc} */
	@Override
	public AttributesNodeProvider createAttributesNodeProvider() {
		// TODO Check if the implementation used in FileRepository can be used
		// for this kind of repository
		return new EmptyAttributesNodeProvider();
	}

	private static class EmptyAttributesNodeProvider implements
			AttributesNodeProvider {
		private EmptyAttributesNode emptyAttributesNode = new EmptyAttributesNode();

		@Override
		public AttributesNode getInfoAttributesNode() throws IOException {
			return emptyAttributesNode;
		}

		@Override
		public AttributesNode getGlobalAttributesNode() throws IOException {
			return emptyAttributesNode;
		}

		private static class EmptyAttributesNode extends AttributesNode {

			public EmptyAttributesNode() {
				super(Collections.<AttributesRule> emptyList());
			}

			@Override
			public void parse(InputStream in) throws IOException {
				// Do nothing
			}
		}
	}
}

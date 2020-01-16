/*
 * Copyright (C) 2011, GitHub Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.submodule;

import org.eclipse.jgit.lib.ObjectId;

/**
 * Status class containing the type, path, and commit id of the submodule.
 */
public class SubmoduleStatus {

	private final SubmoduleStatusType type;

	private final String path;

	private final ObjectId indexId;

	private final ObjectId headId;

	/**
	 * Create submodule status
	 *
	 * @param type
	 *            a {@link org.eclipse.jgit.submodule.SubmoduleStatusType}
	 *            object.
	 * @param path
	 *            submodule path
	 * @param indexId
	 *            an {@link org.eclipse.jgit.lib.ObjectId} object.
	 */
	public SubmoduleStatus(final SubmoduleStatusType type, final String path,
			final ObjectId indexId) {
		this(type, path, indexId, null);
	}

	/**
	 * Create submodule status
	 *
	 * @param type
	 *            a {@link org.eclipse.jgit.submodule.SubmoduleStatusType}
	 *            object.
	 * @param path
	 *            submodule path
	 * @param indexId
	 *            index id
	 * @param headId
	 *            head id
	 */
	public SubmoduleStatus(final SubmoduleStatusType type, final String path,
			final ObjectId indexId, final ObjectId headId) {
		this.type = type;
		this.path = path;
		this.indexId = indexId;
		this.headId = headId;
	}

	/**
	 * Get type
	 *
	 * @return type
	 */
	public SubmoduleStatusType getType() {
		return type;
	}

	/**
	 * Get submodule path
	 *
	 * @return path submodule path
	 */
	public String getPath() {
		return path;
	}

	/**
	 * Get index object id
	 *
	 * @return index object id
	 */
	public ObjectId getIndexId() {
		return indexId;
	}

	/**
	 * Get HEAD object id
	 *
	 * @return HEAD object id
	 */
	public ObjectId getHeadId() {
		return headId;
	}
}

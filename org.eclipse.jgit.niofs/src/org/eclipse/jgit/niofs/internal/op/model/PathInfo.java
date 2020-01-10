/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op.model;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;

import static org.eclipse.jgit.lib.FileMode.TYPE_FILE;

public class PathInfo {

	private final long size;
	private final ObjectId objectId;
	private final String path;
	private final PathType pathType;

	public PathInfo(final ObjectId objectId, final String path, final FileMode fileMode) {
		this(objectId, path, convert(fileMode), -1);
	}

	public PathInfo(final ObjectId objectId, final String path, final FileMode fileMode, final long size) {
		this(objectId, path, convert(fileMode));
	}

	public PathInfo(final ObjectId objectId, final String path, final PathType pathType) {
		this(objectId, path, pathType, -1);
	}

	public PathInfo(final ObjectId objectId, final String path, final PathType pathType, final long size) {
		this.objectId = objectId;
		this.path = path;
		this.pathType = pathType;
		this.size = size;
	}

	private static PathType convert(final FileMode fileMode) {
		if (fileMode.equals(FileMode.TYPE_TREE)) {
			return PathType.DIRECTORY;
		} else if (fileMode.equals(TYPE_FILE)) {
			return PathType.FILE;
		}
		return null;
	}

	public ObjectId getObjectId() {
		return objectId;
	}

	public String getPath() {
		return path;
	}

	public PathType getPathType() {
		return pathType;
	}

	public long getSize() {
		return size;
	}
}

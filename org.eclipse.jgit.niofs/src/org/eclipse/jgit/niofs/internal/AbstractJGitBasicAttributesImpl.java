/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

public class AbstractJGitBasicAttributesImpl implements BasicFileAttributes {

	private BasicFileAttributes attributes;

	public AbstractJGitBasicAttributesImpl(final BasicFileAttributes attributes) {
		this.attributes = attributes;
	}

	@Override
	public FileTime lastModifiedTime() {
		return attributes.lastModifiedTime();
	}

	@Override
	public FileTime lastAccessTime() {
		return attributes.lastAccessTime();
	}

	@Override
	public FileTime creationTime() {
		return attributes.creationTime();
	}

	@Override
	public boolean isRegularFile() {
		return attributes.isRegularFile();
	}

	@Override
	public boolean isDirectory() {
		return attributes.isDirectory();
	}

	@Override
	public boolean isSymbolicLink() {
		return attributes.isSymbolicLink();
	}

	@Override
	public boolean isOther() {
		return attributes.isOther();
	}

	@Override
	public long size() {
		return attributes.size();
	}

	@Override
	public Object fileKey() {
		return attributes.fileKey();
	}
}

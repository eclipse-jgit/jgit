/*
 * Copyright (C) 2026, NVIDIA Corporation and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import java.io.File;
import java.net.URI;

import org.eclipse.jgit.lib.Config;

/**
 * Large object alternates stored in S3, listed as
 * {@code s3://<bucket>/<prefix>} links in {@code objects/info/s3-alternates}.
 */
class S3Alternates extends LargeObjectAlternates {
	static final String INFO_S3_ALTERNATES = "info/s3-alternates"; //$NON-NLS-1$

	private static final String S3_SCHEME = "s3"; //$NON-NLS-1$

	/**
	 * Default opener. JGit has no S3 object database yet, so links are
	 * reported as unavailable.
	 */
	static final Opener DEFAULT_OPENER = (cfg, dir, link) -> null;

	S3Alternates(Config config, File objectsDir) {
		super(config, objectsDir, INFO_S3_ALTERNATES, DEFAULT_OPENER);
	}

	@Override
	boolean accepts(URI link) {
		return S3_SCHEME.equalsIgnoreCase(link.getScheme())
				&& link.getHost() != null;
	}

	@Override
	String linkExample() {
		return "s3://<bucket>/<prefix>"; //$NON-NLS-1$
	}
}

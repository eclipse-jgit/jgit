/*
 * Copyright (C) 2013 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.archive;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.eclipse.jgit.api.ArchiveCommand;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;

/**
 * Xz-compressed tar (tar.xz) format.
 */
public final class TxzFormat extends BaseFormat implements
		ArchiveCommand.Format<ArchiveOutputStream> {
	private static final List<String> SUFFIXES = Collections
			.unmodifiableList(Arrays.asList(".tar.xz", ".txz")); //$NON-NLS-1$ //$NON-NLS-2$

	private final ArchiveCommand.Format<ArchiveOutputStream> tarFormat = new TarFormat();

	/** {@inheritDoc} */
	@Override
	public ArchiveOutputStream createArchiveOutputStream(OutputStream s)
			throws IOException {
		return createArchiveOutputStream(s,
				Collections.<String, Object> emptyMap());
	}

	/** {@inheritDoc} */
	@Override
	public ArchiveOutputStream createArchiveOutputStream(OutputStream s,
			Map<String, Object> o) throws IOException {
		XZCompressorOutputStream out = new XZCompressorOutputStream(s);
		return tarFormat.createArchiveOutputStream(out, o);
	}

	/** {@inheritDoc} */
	@Override
	public void putEntry(ArchiveOutputStream out,
			ObjectId tree, String path, FileMode mode, ObjectLoader loader)
			throws IOException {
		tarFormat.putEntry(out, tree, path, mode, loader);
	}

	/** {@inheritDoc} */
	@Override
	public Iterable<String> suffixes() {
		return SUFFIXES;
	}

	/** {@inheritDoc} */
	@Override
	public boolean equals(Object other) {
		return (other instanceof TxzFormat);
	}

	/** {@inheritDoc} */
	@Override
	public int hashCode() {
		return getClass().hashCode();
	}
}

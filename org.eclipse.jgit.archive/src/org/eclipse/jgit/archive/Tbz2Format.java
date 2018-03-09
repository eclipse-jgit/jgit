/*
 * Copyright (C) 2013 Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.archive;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.eclipse.jgit.api.ArchiveCommand;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;

/**
 * bzip2-compressed tarball (tar.bz2) format.
 */
public final class Tbz2Format extends BaseFormat implements
		ArchiveCommand.Format<ArchiveOutputStream> {
	private static final List<String> SUFFIXES = Collections
			.unmodifiableList(Arrays.asList(".tar.bz2", ".tbz", ".tbz2")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

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
		BZip2CompressorOutputStream out = new BZip2CompressorOutputStream(s);
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
		return (other instanceof Tbz2Format);
	}

	/** {@inheritDoc} */
	@Override
	public int hashCode() {
		return getClass().hashCode();
	}
}

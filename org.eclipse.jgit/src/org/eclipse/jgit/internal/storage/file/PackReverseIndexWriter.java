/*
 * Copyright (C) 2022, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static org.eclipse.jgit.internal.storage.file.PackReverseIndex.VERSION_1;

import java.io.BufferedOutputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.text.MessageFormat;
import java.util.List;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.PackedObjectInfo;

/**
 * Writes reverse index files conforming to the requested version.
 * <p>
 * The file format is specified at https://git-scm.com/docs/pack-format#_pack_rev_files_have_the_format.
 */
public abstract class PackReverseIndexWriter {
	protected final DigestOutputStream out;
	protected final DataOutput dataOutput;

	private static final int DEFAULT_VERSION = VERSION_1;

	protected PackReverseIndexWriter(OutputStream dst) {
		out = new DigestOutputStream(dst instanceof BufferedOutputStream ? dst : new BufferedOutputStream(dst),
				Constants.newMessageDigest());
		dataOutput = new SimpleDataOutput(out);
	}

	public static PackReverseIndexWriter createWriter(final OutputStream dst) {
		return createWriter(dst, DEFAULT_VERSION);
	}

	public static PackReverseIndexWriter createWriter(final OutputStream dst, final int version) {
		if (version == VERSION_1) {
			return new PackReverseIndexWriterV1(dst);
		}
		throw new IllegalArgumentException(
				MessageFormat.format(JGitText.get().unsupportedPackReverseIndexVersion, Integer.toString(version)));
	}

	public void write(
			List<? extends PackedObjectInfo> objectsSortedByIndexPosition,
			byte[] packChecksum) throws IOException {
		writeHeader();
		writeBody(objectsSortedByIndexPosition);
		writeFooter(packChecksum);
		out.flush();
	}

	protected abstract void writeHeader() throws IOException;

	protected abstract void writeBody(
			List<? extends PackedObjectInfo> objectsSortedByIndexPosition)
			throws IOException;

	private void writeFooter(byte[] packChecksum) throws IOException {
		out.write(packChecksum);
		byte[] selfChecksum = out.getMessageDigest().digest();
		out.write(selfChecksum);
	}
}

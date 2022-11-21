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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import org.eclipse.jgit.transport.PackedObjectInfo;

/**
 * Writes reverse index files following the version 1 format.
 * <p>
 * The file format is specified at
 * https://git-scm.com/docs/pack-format#_pack_rev_files_have_the_format.
 */
final class PackReverseIndexWriterV1 extends PackReverseIndexWriter {
	private static final int OID_VERSION_SHA1 = 1;
	private static final int DEFAULT_OID_VERSION = OID_VERSION_SHA1;

	PackReverseIndexWriterV1(final OutputStream dst) {
		super(dst);
	}

	@Override
	protected void writeHeader() throws IOException {
		out.write(MAGIC);
		dataOutput.writeInt(VERSION_1);
		dataOutput.writeInt(DEFAULT_OID_VERSION);
	}

	@Override
	protected void writeBody(
			List<? extends PackedObjectInfo> objectsSortedByIndexPosition)
			throws IOException {
		int[] indexPositionsSortedByOffset = IntStream.range(0,
						objectsSortedByIndexPosition.size()).boxed()
				.sorted(Comparator.comparingLong(
						indexPosition -> objectsSortedByIndexPosition.get(
								indexPosition.intValue()).getOffset()))
				.mapToInt(Integer::intValue).toArray();

		for (int indexPosition : indexPositionsSortedByOffset) {
			dataOutput.writeInt(indexPosition);
		}
	}
}

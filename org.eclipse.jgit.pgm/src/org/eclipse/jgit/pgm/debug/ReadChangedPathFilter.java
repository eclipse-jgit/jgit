/*
 * Copyright (C) 2023, Google LLC
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm.debug;

import java.io.FileInputStream;
import java.util.HashSet;

import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.eclipse.jgit.util.NB;
import org.kohsuke.args4j.Argument;

/**
 * Prints the contents of the BDAT chunk from commit-graph file.
 * <p>
 * This is a debugging tool for changed path filter development.
 */
@Command
class ReadChangedPathFilter extends TextBuiltin {

	static final int CHUNK_ID_OID_FANOUT = 0x4f494446; /* "OIDF" */

	static final int CHUNK_ID_BLOOM_FILTER_INDEX = 0x42494458; /* "BIDX" */

	static final int CHUNK_ID_BLOOM_FILTER_DATA = 0x42444154; /* "BDAT" */

	@Argument(index = 0)
	private String input;

	static HashSet<String> changedPathStrings(byte[] data) {
		int oidf_offset = -1;
		int bidx_offset = -1;
		int bdat_offset = -1;
		for (int i = 8; i < data.length - 4; i += 12) {
			switch (NB.decodeInt32(data, i)) {
			case CHUNK_ID_OID_FANOUT:
				oidf_offset = (int) NB.decodeInt64(data, i + 4);
				break;
			case CHUNK_ID_BLOOM_FILTER_INDEX:
				bidx_offset = (int) NB.decodeInt64(data, i + 4);
				break;
			case CHUNK_ID_BLOOM_FILTER_DATA:
				bdat_offset = (int) NB.decodeInt64(data, i + 4);
				break;
			}
		}
		bdat_offset += 12; // skip version, hash count, bits per entry
		int commit_count = NB.decodeInt32(data, oidf_offset + 255 * 4);
		int[] changed_path_length_cumuls = new int[commit_count];
		for (int i = 0; i < commit_count; i++) {
			changed_path_length_cumuls[i] = NB.decodeInt32(data,
					bidx_offset + i * 4);
		}
		HashSet<String> changed_paths = new HashSet<>();
		for (int i = 0; i < commit_count; i++) {
			int prior_cumul = i == 0 ? 0 : changed_path_length_cumuls[i - 1];
			String changed_path = "";
			for (int j = prior_cumul; j < changed_path_length_cumuls[i]; j++) {
				changed_path += data[bdat_offset + j] + ",";
			}
			changed_paths.add(changed_path);
		}
		return changed_paths;
	}

	/** {@inheritDoc} */
	@Override
	protected void run() throws Exception {
		try (FileInputStream in = new FileInputStream(input)
				) {
				byte[] data = in.readAllBytes();
				outw.println(changedPathStrings(data).toString());
		}
	}
}

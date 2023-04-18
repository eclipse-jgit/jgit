/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm.debug;

import java.io.FileInputStream;

import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.kohsuke.args4j.Argument;
import java.util.HashSet;
import org.eclipse.jgit.util.NB;

@Command
class ReadChangedPathFilter extends TextBuiltin {
	@Argument(index = 0)
	private String input;

	static HashSet<String> changedPathStrings(byte[] data) {
		int oidf_offset = -1;
		int bidx_offset = -1;
		int bdat_offset = -1;
		for (int i = 8; i < data.length - 4; i += 12) {
			switch (NB.decodeInt32(data, i)) {
			case 0x4f494446:
				oidf_offset = (int) NB.decodeInt64(data, i + 4);
				break;
				case 0x42494458:
				bidx_offset = (int) NB.decodeInt64(data, i + 4);
				break;
				case 0x42444154:
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

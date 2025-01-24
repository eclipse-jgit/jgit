/*
 * Copyright (C) 2012, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.internal.storage.file.ObjectDirectory;
import org.eclipse.jgit.internal.storage.file.Pack;
import org.eclipse.jgit.internal.storage.file.PackFile;
import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndexPrettyPrinter;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndexWriter;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@SuppressWarnings("nls")
@Command(common = true, usage = "usage_MultiPackIndex")
class MultiPackIndex extends TextBuiltin {
	@Argument(index = 0, required = true, usage = "write, print")
	private String command;

	@Option(name = "--midx")
	private String midxFilename;

	/** {@inheritDoc} */
	@Override
	protected void run() throws IOException {
		switch (command) {
		case "print":
			printMultiPackIndex(midxFilename);
			break;
		case "write":
			writeMultiPackIndex(midxFilename);
			break;
		default:
			outw.println("Unknown command " + command);
		}
	}

	private void printMultiPackIndex(String midxFileName) {
		if (midxFileName == null || midxFileName.isEmpty()) {
			throw die(
					"'print' requires the path of a multipack index file with --midx option.");
		}

		try (FileInputStream is = new FileInputStream(midxFileName)) {
			PrintWriter pw = new PrintWriter(outw, true);
			MultiPackIndexPrettyPrinter.prettyPrint(is.readAllBytes(), pw);
		} catch (FileNotFoundException e) {
			throw die(true, e);
		} catch (IOException e) {
			throw die(true, e);
		}
	}

	private void writeMultiPackIndex(String midxFileName) throws IOException {
		if (!(db.getObjectDatabase() instanceof ObjectDirectory)) {
			throw die("This repository object db doesn't have packs");
		}

		File midx;
		if (midxFileName == null || midxFileName.isEmpty()) {
			midx = new File(((ObjectDirectory) db.getObjectDatabase())
					.getPackDirectory(), "multi-pack-index");
		} else {
			midx = new File(midxFileName);
		}

		errw.println("Writing " + midx.getAbsolutePath());

		ObjectDirectory odb = (ObjectDirectory) db.getObjectDatabase();

		Map<String, PackIndex> indexes = new HashMap<>();
		for (Pack pack : odb.getPacks()) {
			PackFile packFile = pack.getPackFile().create(PackExt.INDEX);
			try {
				indexes.put(packFile.getName(), pack.getIndex());
			} catch (IOException e) {
				throw die("Cannot open index in pack", e);
			}
		}

		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		try (FileOutputStream out = new FileOutputStream(midxFileName)) {
			writer.write(NullProgressMonitor.INSTANCE, out, indexes);
		} catch (IOException e) {
			throw die("Cannot write midx " + midxFileName, e);
		}
	}
}

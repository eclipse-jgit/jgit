/*
 * Copyright (C) 2025, Google LLC.
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
import java.io.IOException;
import java.io.PrintWriter;
import java.util.regex.Pattern;

import org.eclipse.jgit.internal.storage.file.MidxWriter;
import org.eclipse.jgit.internal.storage.file.ObjectDirectory;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndexPrettyPrinter;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_MultiPackIndex")
@SuppressWarnings("nls")
class MultiPackIndex extends TextBuiltin {
	@Argument(index = 0, required = true, usage = "write, print")
	private String command;

	@Option(name = "--midx")
	private String midxPath;

	@Option(name = "--bitmaps")
	private boolean writeBitmaps;

	/** {@inheritDoc} */
	@Override
	protected void run() throws IOException {
		switch (command) {
		case "print":
			printMultiPackIndex();
			break;
		case "write":
			writeMultiPackIndex();
			break;
		default:
			outw.println("Unknown command " + command);
		}
	}

	private void printMultiPackIndex() {
		if (midxPath == null || midxPath.isEmpty()) {
			throw die("'print' requires the path of a multipack "
					+ "index file with --midx option.");
		}

		try (FileInputStream is = new FileInputStream(midxPath)) {
			PrintWriter pw = new PrintWriter(outw, true);
			MultiPackIndexPrettyPrinter.prettyPrint(is.readAllBytes(), pw);
		} catch (FileNotFoundException e) {
			throw die(true, e);
		} catch (IOException e) {
			throw die(true, e);
		}
	}

	private void writeMultiPackIndex() throws IOException {
		if (!(db.getObjectDatabase() instanceof ObjectDirectory)) {
			throw die("This repository object db doesn't have packs");
		}

		ObjectDirectory odb = (ObjectDirectory) db.getObjectDatabase();
		if (hasLooseObjects(odb)) {
			throw die("Cannot write multi-pack-index with loose objects");
		}

		File midx;
		if (midxPath == null || midxPath.isEmpty()) {
			midx = new File(((ObjectDirectory) db.getObjectDatabase())
					.getPackDirectory(), "multi-pack-index");
		} else {
			midx = new File(midxPath);
		}

		errw.println("Writing " + midx.getAbsolutePath());
		TextProgressMonitor pm = new TextProgressMonitor(errw);
		MidxWriter.writeMidx(pm, odb.getPacks(), midx);
		if (writeBitmaps) {
			PackConfig packConfig = new PackConfig();
			packConfig.setBitmapRecentCommitSpan(1);
			packConfig.setBitmapRecentCommitSpan(100);
			MidxWriter.createAndAttachBitmaps(pm, db, midx, packConfig);
		}
	}

	private static final Pattern FANOUT_DIR_PATTERN = Pattern
			.compile("^[0-9a-f]{2}$");

	/**
	 * Checks if the repository's object directory contains any loose objects.
	 *
	 * @param objDir
	 *            the ObjectDirectory to check.
	 * @return {@code true} if any loose objects are found, {@code false}
	 *         otherwise.
	 */
	public static boolean hasLooseObjects(ObjectDirectory objDir) {
		File objectsDir = objDir.getDirectory();
		if (objectsDir == null || !objectsDir.isDirectory()) {
			return false;
		}

		String[] fanoutDirNames = objectsDir.list();
		if (fanoutDirNames == null) {
			return false;
		}

		for (String dirName : fanoutDirNames) {
			if (FANOUT_DIR_PATTERN.matcher(dirName).matches()) {
				File fanoutDir = new File(objectsDir, dirName);
				if (fanoutDir.isDirectory()) {
					String[] objects = fanoutDir.list();
					// If the fan-out directory is not empty, we have loose
					// objects.
					if (objects != null && objects.length > 0) {
						return true;
					}
				}
			}
		}

		return false;
	}
}

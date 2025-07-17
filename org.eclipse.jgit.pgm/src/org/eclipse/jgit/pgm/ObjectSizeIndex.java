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

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_MIN_BYTES_OBJ_SIZE_INDEX;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_USE_OBJECT_SIZE_INDEX;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_PACK_SECTION;

import java.io.IOException;

import org.eclipse.jgit.internal.storage.file.ObjectDirectory;
import org.eclipse.jgit.internal.storage.file.Pack;
import org.eclipse.jgit.internal.storage.file.PackObjectSizeIndexHelper;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.kohsuke.args4j.Argument;

@Command(common = true, usage = "usage_ObjectSizeIndex")
@SuppressWarnings("nls")
class ObjectSizeIndex extends TextBuiltin {
	@Argument(index = 0, required = true, usage = "write, check")
	private String command;

	/** {@inheritDoc} */
	@Override
	protected void run() throws IOException {
		switch (command) {
		case "write":
			writeObjectSizeIndex();
			break;
		case "check":
			checkObjectSizeIndex();
			break;
		default:
			outw.println("Unknown command " + command);
		}
	}

	private void writeObjectSizeIndex() throws IOException {
		if (!(db.getObjectDatabase() instanceof ObjectDirectory)) {
			throw die("This repository object db doesn't have packs");
		}
		PackObjectSizeIndexHelper.forAllPacks(
				(ObjectDirectory) db.getObjectDatabase(),
				new TextProgressMonitor());
	}

	private void checkObjectSizeIndex() throws IOException {
		if (!(db.getObjectDatabase() instanceof ObjectDirectory)) {
			throw die("This repository object db doesn't have packs");
		}

		System.out.println("Object size index configuration for this repo:");
		System.out.println("\n* Writing:");
		printInt(CONFIG_PACK_SECTION, CONFIG_KEY_MIN_BYTES_OBJ_SIZE_INDEX);

		System.out.println("\n* Reading:");
		printBoolean(CONFIG_PACK_SECTION, CONFIG_KEY_USE_OBJECT_SIZE_INDEX);

		System.out.println("\n* Packs - have object size index:");
		for (Pack pack : ((ObjectDirectory) db.getObjectDatabase())
				.getPacks()) {
			String name = pack.getPackName();
			boolean hasObjectSizeIndex = pack.hasObjectSizeIndex();
			System.out.println(
					String.format("  - %s - %b", name, hasObjectSizeIndex));
		}
	}

	private void printInt(String section, String name) {
		Integer value = db.getConfig().getInt(section, name);
		String key = String.join(".", section, name);
		System.out.println("  - " + key + " = " + value);
	}

	private void printBoolean(String section, String name) {
		Boolean value = db.getConfig().getBoolean(section, name);
		String key = String.join(".", section, name);
		System.out.println("  - " + key + " = " + value);
	}
}

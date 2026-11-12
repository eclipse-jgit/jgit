/*
 * Copyright (C) 2026, Digital.ai and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm.debug;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.MessageFormat;

import org.eclipse.jgit.internal.storage.external.ExternalObjectDatabase;
import org.eclipse.jgit.internal.storage.external.ExternalObjectInserter;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.kohsuke.args4j.Argument;

/**
 * Store a file as a blob in one of the repository's external object stores
 * and print the blob's id.
 * <p>
 * The blob is not referenced by anything; build a tree and commit around it,
 * for example with {@code git mktree --missing} and {@code git commit-tree},
 * to make it reachable.
 */
@Command(usage = "usage_ExternalInsert")
class ExternalInsert extends TextBuiltin {

	@Argument(index = 0, required = true, metaVar = "metaVar_name", usage = "usage_ExternalStoreName")
	private String storeName;

	@Argument(index = 1, required = true, metaVar = "metaVar_file", usage = "usage_ExternalFile")
	private File file;

	@Override
	protected void run() throws Exception {
		if (!(db instanceof FileRepository)) {
			throw die(MessageFormat.format(
					CLIText.get().externalObjectStoreNotFound, storeName));
		}
		ExternalObjectDatabase store = ((FileRepository) db)
				.getObjectDatabase().getExternalObjectDatabases().stream()
				.filter(s -> s.getName().equals(storeName)).findFirst()
				.orElseThrow(() -> die(MessageFormat.format(
						CLIText.get().externalObjectStoreNotFound,
						storeName)));

		long size = Files.size(file.toPath());
		ObjectId id;
		try (InputStream in = Files.newInputStream(file.toPath())) {
			id = new ObjectInserter.Formatter().idFor(OBJ_BLOB, size, in);
		}
		try (ExternalObjectInserter inserter = store.newInserter();
				InputStream in = Files.newInputStream(file.toPath())) {
			inserter.insert(id, OBJ_BLOB, size, in);
		}
		outw.println(id.name());
	}
}

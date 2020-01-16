/*
 * Copyright (C) 2012 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;

import org.eclipse.jgit.api.ArchiveCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.archive.ArchiveFormats;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_archive")
class Archive extends TextBuiltin {
	static {
		ArchiveFormats.registerAll();
	}

	@Argument(index = 0, metaVar = "metaVar_treeish")
	private ObjectId tree;

	@Option(name = "--format", metaVar = "metaVar_archiveFormat", usage = "usage_archiveFormat")
	private String format;

	@Option(name = "--prefix", metaVar = "metaVar_archivePrefix", usage = "usage_archivePrefix")
	private String prefix;

	@Option(name = "--output", aliases = { "-o" }, metaVar = "metaVar_file", usage = "usage_archiveOutput")
	private String output;

	/** {@inheritDoc} */
	@Override
	protected void run() throws Exception {
		if (tree == null)
			throw die(CLIText.get().treeIsRequired);

		OutputStream stream = null;
		try {
			if (output != null)
				stream = new FileOutputStream(output);
			else
				stream = outs;

			try (Git git = new Git(db)) {
				ArchiveCommand cmd = git.archive()
					.setTree(tree)
					.setFormat(format)
					.setPrefix(prefix)
					.setOutputStream(stream);
				if (output != null)
					cmd.setFilename(output);
				cmd.call();
			} catch (GitAPIException e) {
				throw die(e.getMessage(), e);
			}
		} catch (FileNotFoundException e) {
			throw die(e.getMessage(), e);
		} finally {
			if (output != null && stream != null)
				stream.close();
		}
	}
}

/*
 * Copyright (C) 2014, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.gitrepo.RepoCommand;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_parseRepoManifest")
class Repo extends TextBuiltin {

	@Option(name = "--base-uri", aliases = { "-u" }, usage = "usage_baseUri")
	private String uri;

	@Option(name = "--groups", aliases = { "-g" }, usage = "usage_groups")
	private String groups = "default"; //$NON-NLS-1$

	@Argument(required = true, metaVar = "metaVar_path", usage = "usage_pathToXml")
	private String path;

	/** {@inheritDoc} */
	@Override
	protected void run() {
		try {
			new RepoCommand(db)
				.setURI(uri)
				.setPath(path)
				.setGroups(groups)
				.call();
		} catch (GitAPIException e) {
			throw die(e.getMessage(), e);
		}
	}
}

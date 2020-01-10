/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op.commands;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

public class UpdateRemoteConfig {

	private final Git git;
	private final Map.Entry<String, String> remote;
	private final Collection<RefSpec> refSpecs;

	public UpdateRemoteConfig(final Git git, final Map.Entry<String, String> remote,
			final Collection<RefSpec> refSpecs) {
		this.git = git;
		this.remote = remote;
		this.refSpecs = refSpecs;
	}

	public List<RefSpec> execute() throws IOException, URISyntaxException {
		final List<RefSpec> specs = new ArrayList<>();
		if (refSpecs == null || refSpecs.isEmpty()) {
			specs.add(new RefSpec("+refs/heads/*:refs/remotes/" + remote.getKey() + "/*"));
			specs.add(new RefSpec("+refs/tags/*:refs/tags/*"));
			specs.add(new RefSpec("+refs/notes/*:refs/notes/*"));
		} else {
			specs.addAll(refSpecs);
		}

		final StoredConfig config = git.getRepository().getConfig();
		final String url = config.getString("remote", remote.getKey(), "url");
		if (url == null) {
			final RemoteConfig remoteConfig = new RemoteConfig(git.getRepository().getConfig(), remote.getKey());
			remoteConfig.addURI(new URIish(remote.getValue()));
			specs.forEach(remoteConfig::addFetchRefSpec);
			remoteConfig.update(git.getRepository().getConfig());
			git.getRepository().getConfig().save();
		}
		return specs;
	}
}

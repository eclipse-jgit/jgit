/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.io.IOException;
import java.sql.Ref;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.Condition;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.commands.Clone;
import org.eclipse.jgit.niofs.internal.op.commands.ListRefs;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JGitMirrorTest extends BaseTest {

	public static final String TARGET_GIT = "test/target.git";
	public static final String ORIGIN = "https://github.com/uberfire/uberfire-website";
	private static Logger logger = LoggerFactory.getLogger(JGitMirrorTest.class);

	@Test
	public void testToHTTPMirrorSuccess() throws IOException, GitAPIException {
		final File parentFolder = util.createTempDirectory();
		final File directory = new File(parentFolder, TARGET_GIT);
		new Clone(directory, ORIGIN, true, null, CredentialsProvider.getDefault(), null, null).execute();

		final Git cloned = Git.open(directory);

		assertThat(cloned).isNotNull();

		assertThat(cloned.getRepository().getRefDatabase().getRefs(RefDatabase.ALL))
				.is(new Condition<Map<String, Ref>>() {
					@Override
					public boolean matches(final Map<String, Ref> refs) {
						final boolean hasMasterRef = refs.get("refs/heads/master") != null;
						final boolean hasNewWebsiteRef = refs.get("refs/heads/new-website") != null;
						final boolean hasRemoteOriginMaster = refs.get("refs/remotes/origin/master") != null;
						final boolean hasRemoteOriginNewWebSite = refs.get("refs/remotes/origin/master") != null;

						return hasMasterRef && hasNewWebsiteRef && hasRemoteOriginMaster & hasRemoteOriginNewWebSite;
					}
				});

		URIish remoteUri = cloned.remoteList().call().get(0).getURIs().get(0);
		String remoteUrl = remoteUri.getScheme() + "://" + remoteUri.getHost() + remoteUri.getPath();
		assertThat(remoteUrl).isEqualTo(ORIGIN);
	}

	@Test
	public void testToHTTPUnmirrorSuccess() throws IOException, GitAPIException {
		final File parentFolder = util.createTempDirectory();
		final File directory = new File(parentFolder, TARGET_GIT);
		new Clone(directory, ORIGIN, false, null, CredentialsProvider.getDefault(), null, null).execute();

		final Git cloned = Git.open(directory);

		assertThat(cloned).isNotNull();

		assertThat(cloned.getRepository().getAllRefs()).is(new Condition<Map<String, Ref>>() {
			@Override
			public boolean matches(final Map<String, Ref> refs) {
				final boolean hasMasterRef = refs.get("refs/heads/master") != null;
				final boolean hasNewWebsiteRef = refs.get("refs/heads/new-website") != null;
				final boolean hasRemoteOriginMaster = refs.get("refs/remotes/origin/master") != null;
				final boolean hasRemoteOriginNewWebSite = refs.get("refs/remotes/origin/master") != null;

				return hasMasterRef && hasNewWebsiteRef && hasRemoteOriginMaster & hasRemoteOriginNewWebSite;
			}
		});

		final boolean isMirror = cloned.getRepository().getConfig().getBoolean("remote", "origin", "mirror", false);
		assertFalse(isMirror);

		assertThat(new ListRefs(cloned.getRepository()).execute().get(0).getName()).isEqualTo("refs/heads/master");

		URIish remoteUri = cloned.remoteList().call().get(0).getURIs().get(0);
		String remoteUrl = remoteUri.getScheme() + "://" + remoteUri.getHost() + remoteUri.getPath();
		assertThat(remoteUrl).isEqualTo(ORIGIN);
	}

	@Test
	public void testEmptyCredentials() throws IOException, GitAPIException {
		final File parentFolder = util.createTempDirectory();
		final File directory = new File(parentFolder, TARGET_GIT);
		new Clone(directory, ORIGIN, false, null, null, null, null).execute();

		final Git cloned = Git.open(directory);

		assertThat(cloned).isNotNull();

		assertThat(new ListRefs(cloned.getRepository()).execute()).is(new Condition<List<? extends Ref>>() {
			@Override
			public boolean matches(final List<? extends Ref> refs) {
				return refs.size() > 0;
			}
		});

		assertThat(new ListRefs(cloned.getRepository()).execute().get(0).getName()).isEqualTo("refs/heads/master");

		URIish remoteUri = cloned.remoteList().call().get(0).getURIs().get(0);
		String remoteUrl = remoteUri.getScheme() + "://" + remoteUri.getHost() + remoteUri.getPath();
		assertThat(remoteUrl).isEqualTo(ORIGIN);
	}

	@Test
	public void testBadUrl() throws IOException, GitAPIException {
		final File parentFolder = util.createTempDirectory();
		final File directory = new File(parentFolder, TARGET_GIT);
		try {
			new Clone(directory, ORIGIN + "sssss", false, null, CredentialsProvider.getDefault(), null, null).execute();
			fail("If got here the test is wrong because the ORIGIN does no exist");
		} catch (Clone.CloneException ex) {
			assertThat(ex).isNotNull();
			logger.info(ex.getMessage(), ex);
		}
	}
}

/*
 * Copyright (C) 2026, Stuart Lang <stuart.b.lang@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.FilterSpec;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.Transport;

/**
 * Fetches missing objects on demand from the promisor remote configured in
 * {@code extensions.partialClone}.
 * <p>
 * Each missing object is requested from the promisor remote as an explicit
 * {@code want}, which the server honors even under the configured partial-clone
 * filter. The downloaded pack is stored in the repository's object database and
 * marked as a promisor pack.
 */
class PromisorRemoteFetcher implements PromisorObjectFetcher {

	private final Repository repo;

	PromisorRemoteFetcher(Repository repo) {
		this.repo = repo;
	}

	@Override
	public boolean fetch(Collection<ObjectId> ids) throws IOException {
		if (ids.isEmpty()) {
			return false;
		}
		StoredConfig cfg = repo.getConfig();
		String remote = cfg.getString(
				ConfigConstants.CONFIG_EXTENSIONS_SECTION, null,
				ConfigConstants.CONFIG_KEY_PARTIAL_CLONE);
		if (remote == null || remote.isEmpty()) {
			// Not a partial clone; nothing can be fetched lazily.
			return false;
		}

		List<RefSpec> want = ids.stream().map(id -> new RefSpec(id.name()))
				.collect(toList());
		try (Transport tn = Transport.open(repo, remote)) {
			String filter = cfg.getString(
					ConfigConstants.CONFIG_REMOTE_SECTION, remote,
					ConfigConstants.CONFIG_KEY_PARTIAL_CLONE_FILTER);
			if (filter != null) {
				tn.setFilterSpec(FilterSpec.fromFilterLine(filter));
			}
			tn.fetch(NullProgressMonitor.INSTANCE, want);
			return true;
		} catch (URISyntaxException | NotSupportedException
				| TransportException e) {
			throw new IOException(MessageFormat.format(
					JGitText.get().cannotFetchPromisorObjects, remote), e);
		}
	}
}

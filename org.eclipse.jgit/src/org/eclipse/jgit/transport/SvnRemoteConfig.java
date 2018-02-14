/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.transport;

import org.eclipse.jgit.lib.Config;

import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.*;

/**
 * A remembered remote repository, including URLs and RefSpecs.
 * <p>
 * A remote configuration remembers one or more URLs for a frequently accessed
 * remote repository as well as zero or more fetch and push specifications
 * describing how refs should be transferred between this repository and the
 * remote repository.
 *
 * @since 4.11
 */
public class SvnRemoteConfig implements Serializable {
	private static final long serialVersionUID = 1L;

	private static final String SECTION = "svn-remote"; //$NON-NLS-1$

	private static final String KEY_URL = "url"; //$NON-NLS-1$

	private static final String KEY_FETCH = "fetch"; //$NON-NLS-1$

	private static final String KEY_BRANCHES = "branches"; //$NON-NLS-1$

	private static final String KEY_TAGS = "tags"; //$NON-NLS-1$

	/**
	 * Parse all svn bridged repo blocks in an existing configuration file, looking for
	 * configuration.
	 *
	 * @param rc
	 *            the existing configuration to get the remote settings from.
	 *            The configuration must already be loaded into memory.
	 * @return all svn bridged repo configurations existing in provided repository
	 *         configuration. Returned configurations are ordered
	 *         lexicographically by names.
	 * @throws URISyntaxException
	 *             one of the URIs within the remote's configuration is invalid.
	 */
	public static List<SvnRemoteConfig> getAllRemoteConfigs(final Config rc)
			throws URISyntaxException {
		final List<String> names = new ArrayList<>(rc
				.getSubsections(SECTION));
		Collections.sort(names);

		final List<SvnRemoteConfig> result = new ArrayList<>(names
				.size());
		for (final String name : names)
			result.add(new SvnRemoteConfig(rc, name));
		return result;
	}

	private String name;

	private List<URIish> uris;

	private List<RefSpec> fetch;

	private List<String> branches;

	private List<String> tags;

	/**
	 * Parse a remote block from an existing configuration file.
	 * <p>
	 * This constructor succeeds even if the requested remote is not defined
	 * within the supplied configuration file. If that occurs then there will be
	 * no URIs and no ref specifications known to the new instance.
	 *
	 * @param rc
	 *            the existing configuration to get the remote settings from.
	 *            The configuration must already be loaded into memory.
	 * @param remoteName
	 *            subsection key indicating the name of this remote.
	 * @throws URISyntaxException
	 *             one of the URIs within the remote's configuration is invalid.
	 */
	public SvnRemoteConfig(final Config rc, final String remoteName)
			throws URISyntaxException {
		name = remoteName;

		String[] vlst;

		vlst = rc.getStringList(SECTION, name, KEY_URL);
		uris = new ArrayList<>(vlst.length);
		for (final String s : vlst) {
			uris.add(new URIish(s));
		}

		fetch = rc.getRefSpecs(SECTION, name, KEY_FETCH);

		String[] blst = rc.getStringList(SECTION, name, KEY_BRANCHES);
		branches = Arrays.asList(blst);

		String[] tlst = rc.getStringList(SECTION, name, KEY_TAGS);
		tags = Arrays.asList(tlst);
	}

	/**
	 * Update this remote's definition within the configuration.
	 *
	 * @param rc
	 *            the configuration file to store ourselves into.
	 */
	public void update(final Config rc) {
		final List<String> vlst = new ArrayList<>();

		vlst.clear();
		for (final URIish u : getURIs())
			vlst.add(u.toPrivateString());
		rc.setStringList(SECTION, getName(), KEY_URL, vlst);

		vlst.clear();
		for (final RefSpec u : getFetchRefSpecs())
			vlst.add(u.toString());
		rc.setStringList(SECTION, getName(), KEY_FETCH, vlst);

		rc.setStringList(SECTION, getName(), KEY_BRANCHES, getBranches());

		rc.setStringList(SECTION, getName(), KEY_TAGS, getTags());
	}

	/**
	 * @return branches
	 */
	public List<String> getBranches() {
		return branches;
	}

	/**
	 * @return tags
	 */
	public List<String> getTags() {
		return tags;
	}

	/**
	 * Get the local name this remote configuration is recognized as.
	 *
	 * @return name assigned by the user to this configuration block.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get all configured URIs under this remote.
	 *
	 * @return the set of URIs known to this remote.
	 */
	public List<URIish> getURIs() {
		return Collections.unmodifiableList(uris);
	}

	/**
	 * Add a new URI to the end of the list of URIs.
	 *
	 * @param toAdd
	 *            the new URI to add to this remote.
	 * @return true if the URI was added; false if it already exists.
	 */
	public boolean addURI(final URIish toAdd) {
		if (uris.contains(toAdd))
			return false;
		return uris.add(toAdd);
	}

	/**
	 * Remove a URI from the list of URIs.
	 *
	 * @param toRemove
	 *            the URI to remove from this remote.
	 * @return true if the URI was added; false if it already exists.
	 */
	public boolean removeURI(final URIish toRemove) {
		return uris.remove(toRemove);
	}

	/**
	 * Remembered specifications for fetching from a repository.
	 *
	 * @return set of specs used by default when fetching.
	 */
	public List<RefSpec> getFetchRefSpecs() {
		return Collections.unmodifiableList(fetch);
	}

	/**
	 * Add a new fetch RefSpec to this remote.
	 *
	 * @param s
	 *            the new specification to add.
	 * @return true if the specification was added; false if it already exists.
	 */
	public boolean addFetchRefSpec(final RefSpec s) {
		if (fetch.contains(s))
			return false;
		return fetch.add(s);
	}

	/**
	 * Override existing fetch specifications with new ones.
	 *
	 * @param specs
	 *            list of fetch specifications to set. List is copied, it can be
	 *            modified after this call.
	 */
	public void setFetchRefSpecs(final List<RefSpec> specs) {
		fetch.clear();
		fetch.addAll(specs);
	}

	/**
	 * Remove a fetch RefSpec from this remote.
	 *
	 * @param s
	 *            the specification to remove.
	 * @return true if the specification existed and was removed.
	 */
	public boolean removeFetchRefSpec(final RefSpec s) {
		return fetch.remove(s);
	}
}

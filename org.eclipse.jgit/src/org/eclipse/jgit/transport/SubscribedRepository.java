/*
 * Copyright (C) 2012, Google Inc.
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

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;

/**
 * A subscription to a single repository using one or more SubscriptionSpecs.
 * Updates are multiplexed over a single Subscriber connection.
 */
public class SubscribedRepository {
	private final Repository repository;

	private final String remote;

	/** The name unique for this repository on this host, usually the path. */
	private final String name;

	private List<RefSpec> specs;

	/**
	 * Get the PubSub ref location from a remote ref. This prefixes the remote
	 * ref with /heads/ so heads and tags can be stored under the pubsub ref.
	 *
	 * @param remote
	 *            e.g "origin"
	 * @param ref
	 *            e.g "refs/remotes/origin/master"
	 * @return pubsub ref location, e.g "refs/pubsub/origin/heads/master"
	 */
	public static String getPubSubRefFromRemote(String remote, String ref) {
		return translateRef(Constants.R_REMOTES + remote + "/",
				Constants.R_PUBSUB + remote + "/heads/", ref);
	}

	/**
	 * Get the PubSub ref location from a local ref.
	 *
	 * @param remote
	 *            e.g "origin"
	 * @param ref
	 *            e.g "refs/heads/master"
	 * @return pubsub ref location, e.g "refs/pubsub/origin/heads/master"
	 */
	public static String getPubSubRefFromLocal(String remote, String ref) {
		return translateRef(
				Constants.R_REFS, Constants.R_PUBSUB + remote + "/", ref);
	}

	/**
	 * Get the remote ref location from a pubsub ref. This strips off the pubsub
	 * prefix /heads/.
	 *
	 * @param remote
	 *            e.g "origin"
	 * @param ref
	 *            e.g "refs/pubsub/origin/heads/master"
	 * @return remote ref location, e.g "refs/remotes/origin/master"
	 */
	public static String getRemoteRefFromPubSub(String remote, String ref) {
		return translateRef(Constants.R_PUBSUB + remote + "/heads/",
				Constants.R_REMOTES + remote + "/", ref);
	}

	/**
	 * Get the local ref location from a pubsub ref.
	 *
	 * @param remote
	 *            e.g "origin"
	 * @param ref
	 *            e.g "refs/remotes/origin/master"
	 * @return local ref location, e.g "refs/heads/master"
	 */
	public static String getLocalRefFromRemote(String remote, String ref) {
		return translateRef(Constants.R_REMOTES + remote + "/",
				Constants.R_REFS + "heads/", ref);
	}

	/**
	 * Get the local ref location from a remote ref.
	 *
	 * @param remote
	 *            e.g "origin"
	 * @param ref
	 *            e.g "refs/heads/master"
	 * @return local ref location, e.g "refs/remotes/origin/master"
	 */
	public static String getRemoteRefFromLocal(String remote, String ref) {
		return translateRef(
				Constants.R_REFS + "heads/", Constants.R_REMOTES + remote + "/",
				ref);
	}

	/**
	 * Get the local ref location from a pubsub ref.
	 *
	 * @param remote
	 *            e.g "origin"
	 * @param ref
	 *            e.g "refs/pubsub/origin/heads/master"
	 * @return local ref location, e.g "refs/heads/master"
	 */
	public static String getLocalRefFromPubSub(String remote, String ref) {
		return translateRef(
				Constants.R_PUBSUB + remote + "/", Constants.R_REFS, ref);
	}

	private static String translateRef(String oldPrefix, String newPrefix,
			String ref) {
		return newPrefix + ref.substring(oldPrefix.length());
	}

	/**
	 * Create a new SubscribedRepo and set up local space for storing ref
	 * updates in refs/pubsub.
	 *
	 * @param s
	 * @throws IOException
	 */
	public SubscribedRepository(PubSubConfig.Subscriber s) throws IOException {
		remote = s.getRemote();
		repository = new FileRepository(s.getDirectory());
		specs = s.getSubscribeSpecs();
		name = s.getName();
	}

	/**
	 * Set up the refs/pubsub/* space and copy in all matching refs.
	 *
	 * @throws IOException
	 */
	public void setUpRefs() throws IOException {
		// Set up space in refs/pubsub/* by copying all locally matching refs
		Map<String, Ref> refs = getRemoteRefs();
		for (Map.Entry<String, Ref> entry : refs.entrySet()) {
			String pubsubRef = getPubSubRefFromLocal(remote,
					entry.getKey());
			if (repository.getRef(pubsubRef) != null)
				continue;
			RefUpdate ru = repository.updateRef(pubsubRef);
			// Create refs/pubsub/<remote name>/<ref>
			ru.setExpectedOldObjectId(ObjectId.zeroId());
			ru.setNewObjectId(entry.getValue().getObjectId());
			ru.update();
		}
	}

	/** @return repository. */
	public Repository getRepository() {
		return repository;
	}

	/** @return the remote name. */
	public String getRemote() {
		return remote;
	}

	/** @return the set of subscribe specs for this repository. */
	public List<RefSpec> getSubscribeSpecs() {
		return Collections.unmodifiableList(specs);
	}

	/**
	 * @param s
	 */
	public void setSubscribeSpecs(List<RefSpec> s) {
		specs = s;
	}

	/**
	 * @return all matching remote ref heads in refs/remotes/remote/* and tag
	 *         values in refs/tags/*, with keys corresponding to refs/*.
	 * @throws IOException
	 */
	public Map<String, Ref> getRemoteRefs() throws IOException {
		Map<String, Ref> matches = new HashMap<String, Ref>();
		RefDatabase rdb = repository.getRefDatabase();
		for (RefSpec spec : getSubscribeSpecs()) {
			String remoteRef;
			boolean isTag = spec.getSource().startsWith(Constants.R_TAGS);
			if (isTag)
				remoteRef = spec.getSource();
			else
				remoteRef = getRemoteRefFromLocal(remote, spec.getSource());
			Collection<Ref> c;
			if (spec.isWildcard()) {
				remoteRef = remoteRef.substring(0, remoteRef.length() - 1);
				c = rdb.getRefs(remoteRef).values();
			} else {
				Ref r = rdb.getRef(remoteRef);
				if (r == null)
					continue;
				c = Collections.nCopies(1, r);
			}
			for (Ref r : c) {
				if (isTag)
					matches.put(r.getName(), r);
				else
					matches.put(getLocalRefFromRemote(getRemote(), r.getName()),
							r);
			}
		}
		return matches;
	}

	/**
	 * @return all pubsub refs in refs/pubsub/remote/*, with keys corresponding
	 *         to refs/*.
	 * @throws IOException
	 */
	public Map<String, Ref> getPubSubRefs() throws IOException {
		Map<String, Ref> matches = new HashMap<String, Ref>();
		RefDatabase rdb = repository.getRefDatabase();
		for (RefSpec spec : getSubscribeSpecs()) {
			String pubsubRef = getPubSubRefFromLocal(remote, spec.getSource());
			if (spec.isWildcard()) {
				pubsubRef = pubsubRef.substring(0, pubsubRef.length() - 1);
				for (Ref r : rdb.getRefs(pubsubRef).values()) {
					matches.put(getLocalRefFromPubSub(remote, r.getName()), r);
				}
			} else {
				Ref r = rdb.getRef(pubsubRef);
				if (r != null)
					matches.put(getLocalRefFromPubSub(remote, r.getName()), r);
			}
		}
		return matches;
	}

	/** @return the name (usually the uri path) of this repository. */
	public String getName() {
		return name;
	}

	/** Close the open repository. */
	public void close() {
		repository.close();
	}
}

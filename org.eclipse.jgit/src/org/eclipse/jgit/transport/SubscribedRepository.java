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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;

/**
 * A subscription to a single repository using one or more SubscriptionSpecs.
 * Updates are multiplexed over a single Subscriber connection.
 */
public class SubscribedRepository {
	/** Location to store updates to subscribed refs. */
	public static final String PUBSUB_REF_PREFIX = "refs/pubsub/";

	private final Repository repository;

	private final String remote;

	/** The name unique for this repository on this host, usually the path. */
	private final String name;

	private List<RefSpec> specs;

	/**
	 * Get the PubSub ref location for a given remote and ref.
	 *
	 * @param remote
	 *            e.g "origin"
	 * @param ref
	 *            e.g "heads/master"
	 * @return pubsub ref location, e.g "refs/pubsub/origin/heads/master"
	 */
	public static String getSubscribedRef(String remote, String ref) {
		return PUBSUB_REF_PREFIX + remote + ref.substring(4);
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

		// Set up space in refs/pubsub/* by copying all locally matching refs
		Map<String, Ref> refs = getMatchingRefs();
		for (Map.Entry<String, Ref> entry : refs.entrySet()) {
			String pubsubRef = getSubscribedRef(s.getRemote(), entry.getKey());
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

	/** @return all local refs that match the subscribe specs. */
	public Map<String, Ref> getMatchingRefs() {
		return getRefFilter().filter(repository.getAllRefs());
	}

	/** @return a ref filter that matches all subscribe specs. */
	public RefFilter getRefFilter() {
		return new RefFilter() {
			public Map<String, Ref> filter(Map<String, Ref> refs) {
				Map<String, Ref> ret = new HashMap<String, Ref>();
				for (Map.Entry<String, Ref> e : refs.entrySet()) {
					for (RefSpec spec : getSubscribeSpecs()) {
						if (spec.matchSource(e.getValue()))
							ret.put(e.getKey(), e.getValue());
					}
				}
				return ret;
			}
		};
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

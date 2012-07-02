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

package org.eclipse.jgit.util;

import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;

/**
 * Utility functions for translating between local (refs/{heads,tags}/),
 * tracking (refs/remotes/origin/) and pubsub (refs/pubsub/origin/{heads,tags}/)
 * ref names.
 */
public class RefTranslator {
	/**
	 * Get the PubSub ref location from a remote tracking ref. The /pubsub/ ref
	 * tree is different from the /remotes/ tree in that it can store branches
	 * and tags. Branches are under /heads/ and tags are under /tags/.
	 *
	 * @param rc
	 * @param trackingRef
	 *            e.g "refs/remotes/origin/master"
	 * @return pubsub ref location, e.g "refs/pubsub/origin/heads/master"
	 */
	public static String getPubSubRefFromTracking(
			RemoteConfig rc, String trackingRef) {
		String ref = getRemoteRefFromTracking(rc, trackingRef);
		return getPubSubRefFromRemote(rc.getName(), ref);
	}

	/**
	 * Get the PubSub ref location from a remote ref.
	 *
	 * @param remote
	 *            e.g "origin"
	 * @param remoteRef
	 *            e.g "refs/heads/master"
	 * @return pubsub ref location, e.g "refs/pubsub/origin/heads/master"
	 */
	public static String getPubSubRefFromRemote(
			String remote, String remoteRef) {
		return translateRef(
				Constants.R_REFS, Constants.R_PUBSUB + remote + "/", remoteRef);
	}

	/**
	 * Get the tracking ref location from a pubsub ref. This strips off the
	 * pubsub prefix /heads/. Translation is only allowed from a pubsub branch
	 * ref to a remote ref, because the /remotes/ tree only stores branches.
	 *
	 * @param rc
	 * @param pubsubRef
	 *            e.g "refs/pubsub/origin/heads/master"
	 * @return tracking ref location, e.g "refs/remotes/origin/master"
	 */
	public static String getTrackingRefFromPubSub(
			RemoteConfig rc, String pubsubRef) {
		String remote = getRemoteRefFromPubSub(rc.getName(), pubsubRef);
		return getTrackingRefFromRemote(rc, remote);
	}

	/**
	 * Get the remote ref location from a tracking ref.
	 *
	 * @param rc
	 * @param trackingRef
	 *            e.g "refs/remotes/origin/master"
	 * @return remote ref location, e.g "refs/heads/master"
	 */
	public static String getRemoteRefFromTracking(
			RemoteConfig rc, String trackingRef) {
		// Match ref against the tracking side of a fetch spec
		String local = null;
		for (RefSpec r : rc.getFetchRefSpecs()) {
			if (r.matchDestination(trackingRef)) {
				if (r.isWildcard())
					local = r.getSource()
							.substring(0, r.getSource().length() - 1)
							+ trackingRef.substring(
									r.getDestination().length() - 1);
				else
					local = r.getSource();
			}
		}
		if (local == null)
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().noMatchingFetchSpec, trackingRef,
					rc.getName()));
		return local;
	}

	/**
	 * Get the tracking ref location from a remote ref.
	 *
	 * @param rc
	 * @param remoteRef
	 *            e.g "refs/heads/master"
	 * @return tracking ref location, e.g "refs/remotes/origin/master"
	 */
	public static String getTrackingRefFromRemote(
			RemoteConfig rc, String remoteRef) {
		// Match ref against the remote side of a fetch spec
		String local = null;
		for (RefSpec r : rc.getFetchRefSpecs()) {
			if (r.matchSource(remoteRef)) {
				if (r.isWildcard())
					local = r.getDestination()
							.substring(0, r.getDestination().length() - 1)
							+ remoteRef.substring(r.getSource().length() - 1);
				else
					local = r.getDestination();
			}
		}
		if (local == null)
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().noMatchingFetchSpec, remoteRef,
					rc.getName()));
		return local;
	}

	/**
	 * Get the remote ref location from a pubsub ref.
	 *
	 * @param remote
	 *            e.g "origin"
	 * @param ref
	 *            e.g "refs/pubsub/origin/heads/master"
	 * @return remote ref location, e.g "refs/heads/master"
	 */
	public static String getRemoteRefFromPubSub(String remote, String ref) {
		return translateRef(
				Constants.R_PUBSUB + remote + "/", Constants.R_REFS, ref);
	}

	private static String translateRef(String oldPrefix, String newPrefix,
			String ref) {
		if (!ref.startsWith(oldPrefix))
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().invalidRefName, ref));
		return newPrefix + ref.substring(oldPrefix.length());
	}

	private RefTranslator() {
		// Static methods only
	}
}

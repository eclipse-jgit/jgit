// Copyright 2012 Google Inc. All Rights Reserved.

package org.eclipse.jgit.transport;

import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Implementation of {@link AdvertiseRefsHook} that advertises the same refs for
 * upload-pack and receive-pack.
 */
public abstract class AbstractAdvertiseRefsHook implements AdvertiseRefsHook {
	public void advertiseRefs(UploadSession uploadSession)
			throws ServiceMayNotContinueException {
		uploadSession.setAdvertisedRefs(getAdvertisedRefs(
				uploadSession.getRepository(), uploadSession.getRevWalk()));
	}

	public void advertiseRefs(ReceiveSession receiveSession)
			throws ServiceMayNotContinueException {
		Map<String, Ref> refs = getAdvertisedRefs(receiveSession.getRepository(),
				receiveSession.getRevWalk());
		Set<ObjectId> haves = getAdvertisedHaves(receiveSession.getRepository(),
				receiveSession.getRevWalk());
		receiveSession.setAdvertisedRefs(refs, haves);
	}

	/**
	 * Get the refs to advertise.
	 *
	 * @param repository
	 *            repository instance.
	 * @param revWalk
	 *            open rev walk on the repository.
	 * @return set of refs to advertise.
	 * @throws ServiceMayNotContinueException
	 *             abort; the message will be sent to the user.
	 */
	protected abstract Map<String, Ref> getAdvertisedRefs(
			Repository repository, RevWalk revWalk)
			throws ServiceMayNotContinueException;

	/**
	 * Get the additional haves to advertise.
	 *
	 * @param repository
	 *            repository instance.
	 * @param revWalk
	 *            open rev walk on the repository.
	 * @return set of additional haves; see
	 *         {@link ReceiveSession#getAdvertisedObjects()}.
	 * @throws ServiceMayNotContinueException
	 *             abort; the message will be sent to the user.
	 */
	protected Set<ObjectId> getAdvertisedHaves(
			Repository repository, RevWalk revWalk)
			throws ServiceMayNotContinueException {
		return null;
	}
}

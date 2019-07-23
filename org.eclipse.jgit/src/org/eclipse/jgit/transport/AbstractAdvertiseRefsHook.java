/*
 * Copyright (C) 2012, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 * * This program and the accompanying materials are made available
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

import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Implementation of {@link org.eclipse.jgit.transport.AdvertiseRefsHook} that advertises the same refs for
 * upload-pack and receive-pack.
 *
 * @since 2.0
 */
public abstract class AbstractAdvertiseRefsHook implements AdvertiseRefsHook {
	/** {@inheritDoc} */
	@Override
	public void advertiseRefs(UploadPack uploadPack)
			throws ServiceMayNotContinueException {
		uploadPack.setAdvertisedRefs(getAdvertisedRefs(
				uploadPack.getRepository(), uploadPack.getRevWalk()));
	}

	/** {@inheritDoc} */
	@Override
	public void advertiseRefs(ReceivePack receivePack)
			throws ServiceMayNotContinueException {
		Map<String, Ref> refs = getAdvertisedRefs(receivePack.getRepository(),
				receivePack.getRevWalk());
		Set<ObjectId> haves = getAdvertisedHaves(receivePack.getRepository(),
				receivePack.getRevWalk());
		receivePack.setAdvertisedRefs(refs, haves);
	}

	/**
	 * Get the refs to advertise.
	 *
	 * @param repository
	 *            repository instance.
	 * @param revWalk
	 *            open rev walk on the repository.
	 * @return set of refs to advertise.
	 * @throws org.eclipse.jgit.transport.ServiceMayNotContinueException
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
	 *         {@link org.eclipse.jgit.transport.ReceivePack#getAdvertisedObjects()}.
	 * @throws org.eclipse.jgit.transport.ServiceMayNotContinueException
	 *             abort; the message will be sent to the user.
	 */
	protected Set<ObjectId> getAdvertisedHaves(
			Repository repository, RevWalk revWalk)
			throws ServiceMayNotContinueException {
		return null;
	}
}

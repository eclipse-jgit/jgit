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

/**
 * Hook to allow callers to take over advertising refs to the client.
 *
 * @since 2.0
 */
public interface AdvertiseRefsHook {
	/**
	 * A simple hook that advertises the default refs.
	 * <p>
	 * The method implementations do nothing to preserve the default behavior; see
	 * {@link UploadPack#setAdvertisedRefs(java.util.Map)} and
	 * {@link ReceivePack#setAdvertisedRefs(java.util.Map,java.util.Set)}.
	 */
	AdvertiseRefsHook DEFAULT = new AdvertiseRefsHook() {
		@Override
		public void advertiseRefs(UploadPack uploadPack) {
			// Do nothing.
		}

		@Override
		public void advertiseRefs(BaseReceivePack receivePack) {
			// Do nothing.
		}
	};

	/**
	 * Advertise refs for upload-pack.
	 *
	 * @param uploadPack
	 *            instance on which to call
	 *            {@link org.eclipse.jgit.transport.UploadPack#setAdvertisedRefs(java.util.Map)}
	 *            if necessary.
	 * @throws org.eclipse.jgit.transport.ServiceMayNotContinueException
	 *             abort; the message will be sent to the user.
	 */
	void advertiseRefs(UploadPack uploadPack)
			throws ServiceMayNotContinueException;

	/**
	 * Advertise refs for receive-pack.
	 *
	 * @param receivePack
	 *            instance on which to call
	 *            {@link org.eclipse.jgit.transport.ReceivePack#setAdvertisedRefs(java.util.Map,java.util.Set)}
	 *            if necessary.
	 * @throws org.eclipse.jgit.transport.ServiceMayNotContinueException
	 *             abort; the message will be sent to the user.
	 */
	void advertiseRefs(BaseReceivePack receivePack)
			throws ServiceMayNotContinueException;
}

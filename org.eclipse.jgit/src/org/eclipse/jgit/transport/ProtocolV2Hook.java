/*
 * Copyright (C) 2018, Google LLC.
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

/**
 * Hook to allow callers to be notified on Git protocol v2 requests.
 *
 * @see UploadPack#setProtocolV2Hook(ProtocolV2Hook)
 * @since 5.1
 */
public interface ProtocolV2Hook {
	/**
	 * The default hook implementation that does nothing.
	 */
	static ProtocolV2Hook DEFAULT = new ProtocolV2Hook() {
		// No override.
	};

	/**
	 * @param req
	 *            the capabilities request
	 * @throws ServiceMayNotContinueException
	 *             abort; the message will be sent to the user
	 * @since 5.1
	 */
	default void onCapabilities(CapabilitiesV2Request req)
			throws ServiceMayNotContinueException {
		// Do nothing by default.
	}

	/**
	 * @param req
	 *            the ls-refs request
	 * @throws ServiceMayNotContinueException
	 *             abort; the message will be sent to the user
	 * @since 5.1
	 */
	default void onLsRefs(LsRefsV2Request req)
			throws ServiceMayNotContinueException {
		// Do nothing by default.
	}

	/**
	 * @param req the fetch request
	 * @throws ServiceMayNotContinueException abort; the message will be sent to the user
	 */
	default void onFetch(FetchV2Request req)
			throws ServiceMayNotContinueException {
		// Do nothing by default
	}
}

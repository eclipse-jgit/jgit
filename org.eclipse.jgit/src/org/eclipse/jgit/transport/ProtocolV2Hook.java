/*
 * Copyright (C) 2018, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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

package org.eclipse.jgit.transport;

/**
 * Hook to allow callers to be notified on Git protocol v2 requests.
 *
 * @since 5.0
 */
public interface ProtocolV2Hook {
	/**
	 * @throws ServiceMayNotContinueException
	 * @since 5.0
	 */
	default void onCapabilities() throws ServiceMayNotContinueException {
		// Do nothing by default.
	}

	/**
	 * @param req
	 *            the ls-refs request
	 * @throws ServiceMayNotContinueException
	 *             abort; the message will be sent to the user
	 * @since 5.0
	 */
	default void onLsRefs(LsRefsV2Request req)
			throws ServiceMayNotContinueException {
		// Do nothing by default.
	}
}

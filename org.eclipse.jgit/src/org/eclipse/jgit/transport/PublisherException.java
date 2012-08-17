package org.eclipse.jgit.transport;

import java.io.IOException;

/**
 * A fatal exception for this publisher; it must stop accepting new clients and
 * disconnect existing clients.
 */
public class PublisherException extends IOException {
	private static final long serialVersionUID = 1L;

	/**
	 * @param message
	 */
	public PublisherException(String message) {
		super(message);
	}

	/**
	 * @param message
	 * @param cause
	 */
	public PublisherException(String message, Throwable cause) {
		super(message);
		initCause(cause);
	}
}

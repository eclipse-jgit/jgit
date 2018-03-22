package org.eclipse.jgit.lib;

import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * Signals an invalid Git config entry.
 *
 * @since 5.0
 */
public class ConfigIllegalValueException extends ConfigInvalidException {

	/**
	 * @param message
	 */
	public ConfigIllegalValueException(String message) {
		super(message);
	}

	/**
	 * @param message
	 * @param cause
	 */
	public ConfigIllegalValueException(String message, Throwable cause) {
		super(message, cause);
	}
}

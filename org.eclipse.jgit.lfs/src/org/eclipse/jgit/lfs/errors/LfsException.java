package org.eclipse.jgit.lfs.errors;

/**
 * Thrown when an error occurs during LFS operation.
 *
 * @since 4.5
 */
public class LfsException extends Exception {
	private static final long serialVersionUID = 1L;

	/**
	 * @param message
	 */
	public LfsException(String message) {
		super(message);
	}
}

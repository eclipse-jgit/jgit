package org.eclipse.jgit.lfs.errors;

/**
 * Thrown when there is a validation error with one or more of the objects in
 * the request.
 *
 * @since 4.5
 */
public class LfsValidationError extends LfsException {
	private static final long serialVersionUID = 1L;

	/**
	 * @param message
	 */
	public LfsValidationError(String message) {
		super(message);
	}
}

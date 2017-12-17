package org.eclipse.jgit.api.errors;

/**
 * Thrown from StashApplyCommand when stash apply fails
 */
public class StashApplyFailureException extends GitAPIException {

	private static final long serialVersionUID = 1L;

	/**
	 * Constructor for StashApplyFailureException
	 *
	 * @param message
	 *            error message
	 * @param cause
	 *            a {@link java.lang.Throwable}
	 * @since 4.1
	 */
	public StashApplyFailureException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Create a StashApplyFailedException
	 *
	 * @param message
	 *            error message
	 */
	public StashApplyFailureException(final String message) {
		super(message);
	}

}

package org.eclipse.jgit.api.errors;

/**
 * Thrown when trying to delete a branch which is currently checked out
 */
public class CannotDeleteCurrentBranchException extends GitAPIException {
	private static final long serialVersionUID = 1L;

	/**
	 * @param message
	 *            the message
	 */
	public CannotDeleteCurrentBranchException(String message) {
		super(message);
	}
}

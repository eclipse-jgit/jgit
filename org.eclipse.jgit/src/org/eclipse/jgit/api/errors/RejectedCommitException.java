package org.eclipse.jgit.api.errors;

/**
 * Exception thrown when a commit is rejected by a hook (either
 * {@link org.eclipse.jgit.util.Hook#PRE_COMMIT pre-commit} or
 * {@link org.eclipse.jgit.util.Hook#COMMIT_MSG commit-msg}).
 *
 * @since 3.6
 */
public class RejectedCommitException extends GitAPIException {
	private static final long serialVersionUID = 1L;

	/**
	 * @param message
	 */
	public RejectedCommitException(String message) {
		super(message);
	}
}

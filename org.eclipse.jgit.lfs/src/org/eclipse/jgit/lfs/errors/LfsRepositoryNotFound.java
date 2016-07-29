package org.eclipse.jgit.lfs.errors;

/**
 * Thrown when the repository does not exist for the user.
 *
 * @since 4.5
 */
public class LfsRepositoryNotFound extends LfsException {
	private static final long serialVersionUID = 1L;

	/**
	 * @param name
	 *
	 */
	public LfsRepositoryNotFound(String name) {
		super("repository " + name + " not found"); //$NON-NLS-1$ //$NON-NLS-2$
	}
}

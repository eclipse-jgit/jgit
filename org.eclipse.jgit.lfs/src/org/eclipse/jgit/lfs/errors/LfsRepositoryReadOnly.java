package org.eclipse.jgit.lfs.errors;

/**
 * Thrown when the user has read, but not write access. Only applicable when the
 * operation in the request is "upload".
 *
 * @since 4.5
 */
public class LfsRepositoryReadOnly extends LfsException {
	private static final long serialVersionUID = 1L;

	/**
	 * @param name
	 */
	public LfsRepositoryReadOnly(String name) {
		super("repository " + name + "is read-only"); //$NON-NLS-1$ //$NON-NLS-2$
	}
}

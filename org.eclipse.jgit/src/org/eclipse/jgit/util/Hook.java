package org.eclipse.jgit.util;

/**
 * An enum describing the different hooks a user can implement to customize his
 * repositories.
 *
 * @since 3.6
 */
public enum Hook {
	/**
	 * Literal for the "pre-commit" git hook.
	 * <p>
	 * This hook is invoked by git commit, and can be bypassed with the
	 * "no-verify" option. It takes no parameter, and is invoked before
	 * obtaining the proposed commit log message and making a commit.
	 * </p>
	 * <p>
	 * A non-zero exit code from the called hook means that the commit should be
	 * aborted.
	 * </p>
	 */
	PRE_COMMIT("pre-commit"); //$NON-NLS-1$

	private final String name;

	private Hook(String name) {
		this.name = name;
	}

	/**
	 * @return The name of this hook.
	 */
	public String getName() {
		return name;
	}
}

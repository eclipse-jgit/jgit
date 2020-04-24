package org.eclipse.jgit.internal.transport.jsch;

import org.eclipse.jgit.nls.NLS;
import org.eclipse.jgit.nls.TranslationBundle;

/**
 * Externalized text messages for localization.
 */
public final class JSchText extends TranslationBundle {

	/**
	 * Get an instance of this translation bundle.
	 *
	 * @return an instance of this translation bundle
	 */
	public static JSchText get() {
		return NLS.getBundleFor(JSchText.class);
	}

	// @formatter:off
	/***/ public String connectionFailed;
	/***/ public String sshUserNameError;
	/***/ public String transportSSHRetryInterrupt;
	/***/ public String unknownHost;

}

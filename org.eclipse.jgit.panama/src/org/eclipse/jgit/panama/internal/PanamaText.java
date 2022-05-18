package org.eclipse.jgit.panama.internal;

import org.eclipse.jgit.nls.NLS;
import org.eclipse.jgit.nls.TranslationBundle;

/**
 * Externalized text messages for localization.
 */
public class PanamaText extends TranslationBundle {

	/**
	 * Get an instance of this translation bundle.
	 *
	 * @return an instance of this translation bundle
	 */
	public static PanamaText get() {
		return NLS.getBundleFor(PanamaText.class);
	}

	// @formatter:off
	/***/ //public String key;
}

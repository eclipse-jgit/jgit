package org.eclipse.jgit.transport;

import java.util.List;

/**
 * Holds a list of option strings.
 */
public abstract class OptionStringWrapper {
	/**
	 * A list of option strings
	 */
	protected List<String> optionStrings;

	/**
	 * Gets the list of option strings.
	 *
	 * @return optionStrings
	 */
	public List<String> getOptionStrings() {
		return optionStrings;
	}
}

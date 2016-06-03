package org.eclipse.jgit.transport;

import java.util.List;

/**
 * A mock option string wrapper for testing purposes.
 */
public class MockOptionStringWrapper extends OptionStringWrapper {
	/**
	 * Sets the list of option strings.
	 *
	 * @param optionStrings
	 * @return {@code this}
	 */
	public MockOptionStringWrapper setOptionStrings(
			List<String> optionStrings) {
		this.optionStrings = optionStrings;
		return this;
	}
}

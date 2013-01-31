package org.eclipse.jgit.attributes;

import java.util.Arrays;

import org.eclipse.jgit.errors.InvalidPatternException;

/**
 * @author Marc Strapetz
 */
class AttributesEntry {
	final String pattern;
	final Attribute[] attributes;
	final String line;
	final AttributesFileNameMatcher matcher;
	int priority;

	public AttributesEntry(String pattern, Attribute[] attributes, String line)
			throws InvalidPatternException {
		this.pattern = pattern;
		this.attributes = attributes;
		this.line = line;
		this.matcher = AttributesFileNameMatcher.createInstance(pattern);
	}

	@Override
	public String toString() {
		return pattern + "=" + Arrays.toString(attributes);
	}
}
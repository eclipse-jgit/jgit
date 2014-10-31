package org.eclipse.jgit.attributes;

import java.util.Set;

/**
 * @since 4.2
 */
public interface AttributesProvider {
	/**
	 * @return the currently active attributes
	 */
	public Set<Attribute> getAttributes();
}

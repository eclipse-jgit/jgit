package org.eclipse.jgit.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ls-refs protocol v2 request.
 *
 * @since 5.0
 */
public class LsRefsV2Request {
	final List<String> refPrefixes = new ArrayList<>();

	boolean symrefs;

	boolean peel;

	/** @return unmodifiable ref-prefixes. */
	public List<String> getRefPrefixes() {
		return Collections.unmodifiableList(refPrefixes);
	}

	/** @return true if symref is requested. */
	public boolean getSymrefs() {
		return symrefs;
	}

	/** @return true if peeling tags are requested. */
	public boolean getPeel() {
		return peel;
	}
}
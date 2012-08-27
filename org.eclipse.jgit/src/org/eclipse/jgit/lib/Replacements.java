package org.eclipse.jgit.lib;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Information about how to replace the contents of objects. This information is
 * typically stored in .git/info/grafts and the refs/replace/* refs.
 */
public interface Replacements {

	/**
	 * @return the mapping of grafts for this repository FIXME: How about grafts
	 *         in alternative repositories
	 * @throws IOException
	 */
	Map<AnyObjectId, List<ObjectId>> getGrafts() throws IOException;

	/**
	 * @return replacement mappings
	 * @throws IOException
	 */
	Map<AnyObjectId, ObjectId> getReplacements() throws IOException;

}
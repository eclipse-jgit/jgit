package org.eclipse.jgit.internal.storage.dfs;

import java.io.PrintWriter;

/**
 * Configuration writer for DFS structures.
 */
public interface DebugConfigurationWriter {
	/**
	 * Print the current cache configuration to the given {@link PrintWriter}.
	 *
	 * @param linePrefix
	 *            prefix to prepend all writen lines with. Ex a string of 0 or
	 *            more " " entries.
	 * @param pad
	 *            filler used to extend linePrefix. Ex a multiple of " ".
	 * @param writer
	 *            {@link PrintWriter} to write the cache's configuration to.
	 */
	void writeConfigurationDebug(String linePrefix, String pad,
			PrintWriter writer);
}

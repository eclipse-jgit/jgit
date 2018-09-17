package org.eclipse.jgit.internal.transport.parser;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * In the pack negotiation phase (protocol v1), the client sends a list of
 * wants. The first "want" line is special, as it (can) have a list of options
 * appended.
 *
 * This class parses the input want line and holds the results: the real want
 * line and the options.
 */
public class FirstWantLine {
	private final String line;

	private final Set<String> options;

	/**
	 * Parse the first want line in the protocol v1 pack negotiation.
	 *
	 * @param line
	 *            line from the client.
	 * @return an instance of FetchLineWithOptions
	 */
	public static FirstWantLine fromLine(String line) {
		String realLine;
		Set<String> options;

		if (line.length() > 45) {
			final HashSet<String> opts = new HashSet<>();
			String opt = line.substring(45);
			if (opt.startsWith(" ")) //$NON-NLS-1$
				opt = opt.substring(1);
			for (String c : opt.split(" ")) //$NON-NLS-1$
				opts.add(c);
			realLine = line.substring(0, 45);
			options = Collections.unmodifiableSet(opts);
		} else {
			realLine = line;
			options = Collections.emptySet();
		}

		return new FirstWantLine(realLine, options);
	}

	private FirstWantLine(String line, Set<String> options) {
		this.line = line;
		this.options = options;
	}

	/**
	 * Parse the first line of a receive-pack request.
	 *
	 * @param line
	 *            line from the client.
	 *
	 * @deprecated Use factory method {@link #fromLine(String)} instead
	 */
	@Deprecated
	public FirstWantLine(String line) {
		if (line.length() > 45) {
			final HashSet<String> opts = new HashSet<>();
			String opt = line.substring(45);
			if (opt.startsWith(" ")) //$NON-NLS-1$
				opt = opt.substring(1);
			for (String c : opt.split(" ")) //$NON-NLS-1$
				opts.add(c);
			this.line = line.substring(0, 45);
			this.options = Collections.unmodifiableSet(opts);
		} else {
			this.line = line;
			this.options = Collections.emptySet();
		}
	}

	/** @return non-capabilities part of the line. */
	public String getLine() {
		return line;
	}

	/** @return options parsed from the line. */
	public Set<String> getOptions() {
		return options;
	}
}

/*
 * Copyright (C) 2014, Andrey Loskutov <loskutov@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.ignore.internal;

import static org.eclipse.jgit.ignore.internal.Strings.checkWildCards;
import static org.eclipse.jgit.ignore.internal.Strings.count;
import static org.eclipse.jgit.ignore.internal.Strings.getPathSeparator;
import static org.eclipse.jgit.ignore.internal.Strings.isWildCard;
import static org.eclipse.jgit.ignore.internal.Strings.split;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.ignore.internal.Strings.PatternState;

/**
 * Matcher built by patterns consists of multiple path segments.
 * <p>
 * This class is immutable and thread safe.
 */
public class PathMatcher extends AbstractMatcher {

	private static final WildMatcher WILD_NO_DIRECTORY = new WildMatcher(false);

	private static final WildMatcher WILD_ONLY_DIRECTORY = new WildMatcher(
			true);

	private final List<IMatcher> matchers;

	private final char slash;

	private final boolean beginning;

	private PathMatcher(String pattern, Character pathSeparator,
			boolean dirOnly)
			throws InvalidPatternException {
		super(pattern, dirOnly);
		slash = getPathSeparator(pathSeparator);
		beginning = pattern.indexOf(slash) == 0;
		if (isSimplePathWithSegments(pattern))
			matchers = null;
		else
			matchers = createMatchers(split(pattern, slash), pathSeparator,
					dirOnly);
	}

	private boolean isSimplePathWithSegments(String path) {
		return !isWildCard(path) && path.indexOf('\\') < 0
				&& count(path, slash, true) > 0;
	}

	private static List<IMatcher> createMatchers(List<String> segments,
			Character pathSeparator, boolean dirOnly)
			throws InvalidPatternException {
		List<IMatcher> matchers = new ArrayList<>(segments.size());
		for (int i = 0; i < segments.size(); i++) {
			String segment = segments.get(i);
			IMatcher matcher = createNameMatcher0(segment, pathSeparator,
					dirOnly, i == segments.size() - 1);
			if (i > 0) {
				final IMatcher last = matchers.get(matchers.size() - 1);
				if (isWild(matcher) && isWild(last))
					// collapse wildmatchers **/** is same as **, but preserve
					// dirOnly flag (i.e. always use the last wildmatcher)
					matchers.remove(matchers.size() - 1);
			}

			matchers.add(matcher);
		}
		return matchers;
	}

	/**
	 * Create path matcher
	 *
	 * @param pattern
	 *            a pattern
	 * @param pathSeparator
	 *            if this parameter isn't null then this character will not
	 *            match at wildcards(* and ? are wildcards).
	 * @param dirOnly
	 *            a boolean.
	 * @return never null
	 * @throws org.eclipse.jgit.errors.InvalidPatternException
	 */
	public static IMatcher createPathMatcher(String pattern,
			Character pathSeparator, boolean dirOnly)
			throws InvalidPatternException {
		pattern = trim(pattern);
		char slash = Strings.getPathSeparator(pathSeparator);
		// ignore possible leading and trailing slash
		int slashIdx = pattern.indexOf(slash, 1);
		if (slashIdx > 0 && slashIdx < pattern.length() - 1)
			return new PathMatcher(pattern, pathSeparator, dirOnly);
		return createNameMatcher0(pattern, pathSeparator, dirOnly, true);
	}

	/**
	 * Trim trailing spaces, unless they are escaped with backslash, see
	 * https://www.kernel.org/pub/software/scm/git/docs/gitignore.html
	 *
	 * @param pattern
	 *            non null
	 * @return trimmed pattern
	 */
	private static String trim(String pattern) {
		while (pattern.length() > 0
				&& pattern.charAt(pattern.length() - 1) == ' ') {
			if (pattern.length() > 1
					&& pattern.charAt(pattern.length() - 2) == '\\') {
				// last space was escaped by backslash: remove backslash and
				// keep space
				pattern = pattern.substring(0, pattern.length() - 2) + " "; //$NON-NLS-1$
				return pattern;
			}
			pattern = pattern.substring(0, pattern.length() - 1);
		}
		return pattern;
	}

	private static IMatcher createNameMatcher0(String segment,
			Character pathSeparator, boolean dirOnly, boolean lastSegment)
			throws InvalidPatternException {
		// check if we see /** or ** segments => double star pattern
		if (WildMatcher.WILDMATCH.equals(segment)
				|| WildMatcher.WILDMATCH2.equals(segment))
			return dirOnly && lastSegment ? WILD_ONLY_DIRECTORY
					: WILD_NO_DIRECTORY;

		PatternState state = checkWildCards(segment);
		switch (state) {
		case LEADING_ASTERISK_ONLY:
			return new LeadingAsteriskMatcher(segment, pathSeparator, dirOnly);
		case TRAILING_ASTERISK_ONLY:
			return new TrailingAsteriskMatcher(segment, pathSeparator, dirOnly);
		case COMPLEX:
			return new WildCardMatcher(segment, pathSeparator, dirOnly);
		default:
			return new NameMatcher(segment, pathSeparator, dirOnly, true);
		}
	}

	/** {@inheritDoc} */
	@Override
	public boolean matches(String path, boolean assumeDirectory,
			boolean pathMatch) {
		if (matchers == null) {
			return simpleMatch(path, assumeDirectory, pathMatch);
		}
		return iterate(path, 0, path.length(), assumeDirectory, pathMatch);
	}

	/*
	 * Stupid but fast string comparison: the case where we don't have to match
	 * wildcards or single segments (mean: this is multi-segment path which must
	 * be at the beginning of the another string)
	 */
	private boolean simpleMatch(String path, boolean assumeDirectory,
			boolean pathMatch) {
		boolean hasSlash = path.indexOf(slash) == 0;
		if (beginning && !hasSlash) {
			path = slash + path;
		}
		if (!beginning && hasSlash) {
			path = path.substring(1);
		}
		if (path.equals(pattern)) {
			// Exact match: must meet directory expectations
			return !dirOnly || assumeDirectory;
		}
		/*
		 * Add slashes for startsWith check. This avoids matching e.g.
		 * "/src/new" to /src/newfile" but allows "/src/new" to match
		 * "/src/new/newfile", as is the git standard
		 */
		String prefix = pattern + slash;
		if (pathMatch) {
			return path.equals(prefix) && (!dirOnly || assumeDirectory);
		}
		if (path.startsWith(prefix)) {
			return true;
		}
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public boolean matches(String segment, int startIncl, int endExcl) {
		throw new UnsupportedOperationException(
				"Path matcher works only on entire paths"); //$NON-NLS-1$
	}

	private boolean iterate(final String path, final int startIncl,
			final int endExcl, boolean assumeDirectory, boolean pathMatch) {
		int matcher = 0;
		int right = startIncl;
		boolean match = false;
		int lastWildmatch = -1;
		// ** matches may get extended if a later match fails. When that
		// happens, we must extend the ** by exactly one segment.
		// wildmatchBacktrackPos records the end of the segment after a **
		// match, so that we can reset correctly.
		int wildmatchBacktrackPos = -1;
		while (true) {
			int left = right;
			right = path.indexOf(slash, right);
			if (right == -1) {
				if (left < endExcl) {
					match = matches(matcher, path, left, endExcl,
							assumeDirectory, pathMatch);
				} else {
					// a/** should not match a/ or a
					match = match && !isWild(matchers.get(matcher));
				}
				if (match) {
					if (matcher < matchers.size() - 1
							&& isWild(matchers.get(matcher))) {
						// ** can match *nothing*: a/**/b match also a/b
						matcher++;
						match = matches(matcher, path, left, endExcl,
								assumeDirectory, pathMatch);
					} else if (dirOnly && !assumeDirectory) {
						// Directory expectations not met
						return false;
					}
				}
				return match && matcher + 1 == matchers.size();
			}
			if (wildmatchBacktrackPos < 0) {
				wildmatchBacktrackPos = right;
			}
			if (right - left > 0) {
				match = matches(matcher, path, left, right, assumeDirectory,
						pathMatch);
			} else {
				// path starts with slash???
				right++;
				continue;
			}
			if (match) {
				boolean wasWild = isWild(matchers.get(matcher));
				if (wasWild) {
					lastWildmatch = matcher;
					wildmatchBacktrackPos = -1;
					// ** can match *nothing*: a/**/b match also a/b
					right = left - 1;
				}
				matcher++;
				if (matcher == matchers.size()) {
					// We had a prefix match here.
					if (!pathMatch) {
						return true;
					}
					if (right == endExcl - 1) {
						// Extra slash at the end: actually a full match.
						// Must meet directory expectations
						return !dirOnly || assumeDirectory;
					}
					// Prefix matches only if pattern ended with /**
					if (wasWild) {
						return true;
					}
					if (lastWildmatch >= 0) {
						// Consider pattern **/x and input x/x.
						// We've matched the prefix x/ so far: we
						// must try to extend the **!
						matcher = lastWildmatch + 1;
						right = wildmatchBacktrackPos;
						wildmatchBacktrackPos = -1;
					} else {
						return false;
					}
				}
			} else if (lastWildmatch != -1) {
				matcher = lastWildmatch + 1;
				right = wildmatchBacktrackPos;
				wildmatchBacktrackPos = -1;
			} else {
				return false;
			}
			right++;
		}
	}

	private boolean matches(int matcherIdx, String path, int startIncl,
			int endExcl, boolean assumeDirectory, boolean pathMatch) {
		IMatcher matcher = matchers.get(matcherIdx);

		final boolean matches = matcher.matches(path, startIncl, endExcl);
		if (!matches || !pathMatch || matcherIdx < matchers.size() - 1
				|| !(matcher instanceof AbstractMatcher)) {
			return matches;
		}

		return assumeDirectory || !((AbstractMatcher) matcher).dirOnly;
	}

	private static boolean isWild(IMatcher matcher) {
		return matcher == WILD_NO_DIRECTORY || matcher == WILD_ONLY_DIRECTORY;
	}
}

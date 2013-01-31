package org.eclipse.jgit.attributes;

import java.io.IOException;

import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.fnmatch.FileNameMatcher;

/**
 * @author Marc Strapetz
 */
public abstract class AttributesFileNameMatcher {

	// Abstract ===============================================================

	public abstract boolean isSimple();

	public abstract boolean matches(String match);

	// Static =================================================================

	public static AttributesFileNameMatcher createInstance(String pattern) {
		final String tempPattern = pattern.endsWith("/") ? pattern.substring(0, pattern.length() - 1) : pattern;
		final boolean absPattern = pattern.equals("/") || tempPattern.contains("/");
		if (pattern.startsWith("/")) {
			pattern = pattern.substring(1);
		}

		final String finalPattern = pattern;
		if (absPattern && isSimplePattern(pattern)) {
			final String finalDecodedPattern;
			try {
				finalDecodedPattern = decode(pattern);
			}
			catch (IOException e) {
				return createSimpleNothingMatcher();
			}

			return new AttributesFileNameMatcher() {
				@Override
				public boolean matches(String match) {
					return finalDecodedPattern.equals(match);
				}

				@Override
				public boolean isSimple() {
					return true;
				}
			};
		}

		if (isEverythingPattern(pattern)) {
			return new AttributesFileNameMatcher() {
				@Override
				public boolean isSimple() {
					return false;
				}

				@Override
				public boolean matches(String match) {
					return !match.contains("/");
				}
			};
		}

		return new GlobMatcher(finalPattern);
	}

	// Utils ================================================================

	private static AttributesFileNameMatcher createSimpleNothingMatcher() {
		return new AttributesFileNameMatcher() {
			@Override
			public boolean isSimple() {
				return true;
			}

			@Override
			public boolean matches(String match) {
				return false;
			}
		};
	}

	public static String decode(String path) throws IOException {
		final StringBuilder stringBuilder = new StringBuilder();

		boolean escaped = false;

		for (int i = 0; i < path.length(); i++) {
			final char c = path.charAt(i);
			if (c == '\\' && !escaped) {
				escaped = true;
			}
			else {
				stringBuilder.append(c);
				escaped = false;
			}
		}
		if (escaped) {
			throw new IOException("Path \"" + path + "\" ends with '\\'");
		}
		return stringBuilder.toString();
	}

	public static boolean isSimplePattern(String basePattern) {
		for (int i = 0; i < basePattern.length(); i++) {
			final char c = basePattern.charAt(i);
			final boolean isNotSimpleCardCharacter = c == '*' || c == '?' || c == '[' || c == ']';
			final boolean isNotEscaped = i == 0 || basePattern.charAt(i - 1) != '\\';
			if (isNotSimpleCardCharacter && isNotEscaped) {
				return false;
			}
		}

		return true;
	}

	private static boolean isEverythingPattern(String pattern) {
		return "*".equals(pattern);
	}

	private static boolean isExtensionPattern(String pattern) {
		return pattern.length() > 0 && (pattern.charAt(0) == '*') && !pattern.contains("/");
	}

	// Inner Classes ========================================================

	private static class GlobMatcher extends AttributesFileNameMatcher {
		private final String pattern;

		//lazy fields
		private FileNameMatcher matcher;
		private boolean patternIsInvalid;

		public GlobMatcher(String pattern) {
			this.pattern = pattern;
			this.matcher = null;
			this.patternIsInvalid = false;
		}

		@Override
		public boolean matches(String match) {
			if (patternIsInvalid) {
				return false;
			}

			final FileNameMatcher matcher = getOrCreateMatcher();
			return globMatches(matcher, match);
		}

		@Override
		public boolean isSimple() {
			return false;
		}

		private FileNameMatcher getOrCreateMatcher() {
			if (matcher == null) {
				matcher = createMatcher(pattern);
			}
			return matcher;
		}

		private boolean globMatches(FileNameMatcher matcher, String match) {
			if (matcher == null) {
				return false;
			}

			matcher.reset();
			matcher.append(match);
			return matcher.isMatch();
		}

		private FileNameMatcher createMatcher(String pattern) {
			if (patternIsInvalid) {
				return null;
			}

			final FileNameMatcher matcher;
			try {
				matcher = new FileNameMatcher(pattern, '/');
			}
			catch (InvalidPatternException e) {
				patternIsInvalid = true;
				return null;
			}
			return matcher;
		}
	}
}
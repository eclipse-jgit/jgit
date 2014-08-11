package org.eclipse.jgit.fnmatch2;

import static java.lang.Character.isLetter;

import java.util.*;
import java.util.regex.Pattern;

import org.eclipse.jgit.errors.InvalidPatternException;

/**
 * Various {@link String} related utility methods, written mostly to avoid
 * generation of new String objects (e.g. via splitting Strings etc).
 * 
 * @since 3.5
 */
public class Strings {

	static char getPathSeparator(Character pathSeparator) {
		return pathSeparator == null ? '/' : pathSeparator.charValue();
	}

	/**
	 * @param pattern
	 *            non null
	 * @param c
	 *            character to remove
	 * @return new string with all trailing characters removed
	 */
	public static String stripTrailing(String pattern, char c) {
		while(pattern.length() > 0 && pattern.charAt(pattern.length() - 1) == c){
			pattern = pattern.substring(0, pattern.length() - 1);
		}
		return pattern;
	}

	private static int count(String s, char c, boolean ignoreFirstLast){
		int start = 0;
		int count = 0;
		while (true) {
			start = s.indexOf(c, start);
			if(start == -1) {
				break;
			}
			if(!ignoreFirstLast || (start != 0 && start != s.length())) {
				count ++;
			}
			start ++;
		}
		return count;
	}

	/**
	 * Splits given string to substrings by given separator
	 *
	 * @param pattern
	 *            non null
	 * @param slash
	 *            separator char
	 * @return list of substrings
	 */
	public static List<String> split(String pattern, char slash){
		int count = count(pattern, slash, true);
		if(count < 1){
			throw new IllegalStateException("Pattern must have at least two segments: " + pattern);
		}
		List<String> segments = new ArrayList<String>(count);
		int right = 0;
		while (true) {
			int left = right;
			right = pattern.indexOf(slash, right);
			if(right == -1) {
				if(left < pattern.length()){
					segments.add(pattern.substring(left));
				}
				break;
			}
			if(right - left > 0) {
				if(left == 1){
					// leading slash should remain by the first pattern
					segments.add(pattern.substring(left - 1, right));
				} else if(right == pattern.length() - 1){
					// trailing slash should remain too
					segments.add(pattern.substring(left, right + 1));
				} else {
					segments.add(pattern.substring(left, right));
				}
			}
			right ++;
		}
		return segments;
	}

	static boolean isWildCard(String pattern) {
		return pattern.indexOf('*') != -1
				|| pattern.indexOf('?') != -1
				|| pattern.indexOf('[') != -1
				// required to match escaped backslashes '\\\\'
				|| pattern.indexOf('\\') != -1
				|| pattern.indexOf(']') != -1;
	}

	final static List<String> POSIX_CHAR_CLASSES = Arrays.asList(
			//[:alnum:] [:alpha:] [:blank:] [:cntrl:]
			"alnum",  "alpha",  "blank",  "cntrl",
			//[:digit:] [:graph:] [:lower:] [:print:]
			"digit",  "graph",  "lower",  "print",
			//[:punct:] [:space:] [:upper:] [:xdigit:]
			"punct",  "space",  "upper",  "xdigit",
			//[:word:] XXX I don't see it in http://man7.org/linux/man-pages/man7/glob.7.html
			// but this was in org.eclipse.jgit.fnmatch.GroupHead.java ???
			"word"
			);
	private static final String DL = "\\p{javaDigit}\\p{javaLetter}";
	final static List<String> JAVA_CHAR_CLASSES = Arrays.asList(
			//[:alnum:]      [:alpha:]         [:blank:]         [:cntrl:]
			"\\p{Alnum}",  "\\p{javaLetter}",  "\\p{Blank}",  "\\p{Cntrl}",
			//[:digit:]         [:graph:]       [:lower:]         [:print:]
			"\\p{javaDigit}",  "[\\p{Graph}" + DL + "]",  "\\p{Ll}",  "[\\p{Print}" + DL + "]",
			//[:punct:]      [:space:]        [:upper:]          [:xdigit:]
			"\\p{Punct}",  "\\p{Space}",  "\\p{Lu}",  "\\p{XDigit}",
			"[" + DL + "_]"
			);

	// XXX Collating symbols [.a.] or equivalence class expressions [=a=] are not supported (yet?)
	// Have no idea if this was simply not implemented in JGit or CLI git don't support this
	final static Pattern UNSUPPORTED = Pattern.compile("\\[[.=]\\w+[.=]\\]");

	/**
	 * Conversion from glob to Java regex following two sources: <li>
	 * http://man7.org/linux/man-pages/man7/glob.7.html <li>
	 * org.eclipse.jgit.fnmatch.FileNameMatcher.java Seems that there are
	 * various ways to define what "glob" can be.
	 *
	 * @param pattern
	 *            non null pattern
	 *
	 * @return Java regex pattern corresponding to given glob pattern
	 * @throws InvalidPatternException
	 */
	static Pattern convertGlob(String pattern) throws InvalidPatternException {
		if(UNSUPPORTED.matcher(pattern).find()){
			throw new InvalidPatternException(
					"Collating symbols [.a.] or equivalence class expressions [=a=] are not supported yet",
					pattern);
		}

		StringBuilder sb = new StringBuilder(pattern.length());

		int in_brackets = 0;
		boolean seenEscape = false;
		boolean ignoreLastBracket = false;
		boolean in_char_class = false;
		// 6 is the length of the longest posix char class "xdigit"
		char[] charClass = new char[6];

		for (int i = 0; i < pattern.length(); i++) {
			char c = pattern.charAt(i);
			switch (c) {

			case '*':
				if(seenEscape || in_brackets > 0) {
					sb.append(c);
				} else {
					sb.append('.').append(c);
				}
				break;

			case '.':
				if(seenEscape || in_brackets > 0) {
					sb.append(c);
				} else {
					sb.append('\\').append('.');
				}
				break;

			case '?':
				if(seenEscape || in_brackets > 0) {
					sb.append(c);
				} else {
					sb.append('.');
				}
				break;

			case ':':
				if(in_brackets > 0) {
					if(lookBehind(sb) == '[' && isLetter(lookAhead(pattern, i))) {
						in_char_class = true;
					}
				}
				sb.append(':');
				break;

			case '-':
				if(in_brackets > 0) {
					if(lookAhead(pattern, i) == ']') {
						sb.append('\\').append(c);
					} else {
						sb.append(c);
					}
				} else {
					sb.append('-');
				}
				break;

			case '\\':
				if(in_brackets > 0) {
					char lookAhead = lookAhead(pattern, i);
					if (lookAhead == ']' || lookAhead == '[') {
						ignoreLastBracket = true;
					}
				}
				sb.append(c);
				break;

			case '[':
				if(in_brackets > 0) {
					sb.append('\\').append('[');
					ignoreLastBracket = true;
				} else {
					if(!seenEscape){
						in_brackets ++;
						ignoreLastBracket = false;
					}
					sb.append('[');
				}
				break;

			case ']':
				if(seenEscape){
					sb.append(']');
					ignoreLastBracket = true;
					break;
				}
				if(in_brackets <= 0) {
					sb.append('\\').append(']');
					ignoreLastBracket = true;
					break;
				}
				char lookBehind = lookBehind(sb);
				if((lookBehind == '[' && !ignoreLastBracket)
						|| lookBehind == '^'
						|| (!in_char_class && lookAhead(pattern, i) == ']')) {
					sb.append('\\');
					sb.append(']');
					ignoreLastBracket = true;
				} else {
					ignoreLastBracket = false;
					if(!in_char_class) {
						in_brackets --;
						sb.append(']');
					} else {
						in_char_class = false;
						String charCl = checkPosixCharClass(charClass);
						// delete last \[:: chars and set the pattern
						if(charCl != null){
							sb.setLength(sb.length() - 4);
							sb.append(charCl);
						}
						reset(charClass);
					}
				}
				break;

			case '!':
				if(in_brackets > 0) {
					if(lookBehind(sb) == '[') {
						sb.append('^');
					} else {
						sb.append(c);
					}
				} else {
					sb.append(c);
				}
				break;

			default:
				if(in_char_class){
					setNext(charClass, c);
				} else{
					sb.append(c);
				}
				break;
			} // end switch

			seenEscape = c == '\\';

		} // end for

		if(in_brackets > 0){
			throw new InvalidPatternException("Not closed bracket?", pattern);
		}
		return Pattern.compile(sb.toString());
	}

	/**
	 * @param buffer
	 * @return zero of the buffer is empty, otherwise the last character from
	 *         buffer
	 */
	private static char lookBehind(StringBuilder buffer) {
		return buffer.length() > 0 ? buffer.charAt(buffer.length() - 1) : 0;
	}

	/**
	 * @param pattern
	 * @param i current pointer in the pattern
	 * @return zero of the index is out of range, otherwise the next character from given position
	 */
	private static char lookAhead(String pattern, int i) {
		int idx = i + 1;
		return idx >= pattern.length()? 0 : pattern.charAt(idx);
	}

	private static void setNext(char[] buffer, char c){
		for (int i = 0; i < buffer.length; i++) {
			if(buffer[i] == 0){
				buffer[i] = c;
				break;
			}
		}
	}

	private static void reset(char[] buffer){
		for (int i = 0; i < buffer.length; i++) {
			buffer[i] = 0;
		}
	}

	private static String checkPosixCharClass(char[] buffer){
		for (int i = 0; i < POSIX_CHAR_CLASSES.size(); i++) {
			String clazz = POSIX_CHAR_CLASSES.get(i);
			boolean match = true;
			for (int j = 0; j < clazz.length(); j++) {
				if(buffer[j] != clazz.charAt(j)){
					match = false;
					break;
				}
			}
			if(match) {
				return JAVA_CHAR_CLASSES.get(i);
			}
		}
		return null;
	}

}

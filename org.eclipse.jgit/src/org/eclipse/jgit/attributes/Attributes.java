/*
 * Copyright (C) 2010, Marc Strapetz <marc.strapetz@syntevo.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.attributes;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.fnmatch.FileNameMatcher;
import org.eclipse.jgit.util.RawParseUtils;

/**
 *
 */
public class Attributes {

	private final List<Object> items = new ArrayList<Object>();

	/**
	 * @return ...
	 */
	public final List<String> getPatterns() {
		final List<String> patterns = new ArrayList<String>();
		for (Object item : items) {
			if (!(item instanceof Entry))
				continue;

			final Entry entry = (Entry) item;
			patterns.add(entry.pattern);
		}

		return patterns;
	}

	/**
	 * @return ...
	 */
	public boolean isEmpty() {
		return items.size() == 0;
	}

	/**
	 * @param relativePath
	 * @param collector
	 * @param keysToIgnore
	 * @return ...
	 */
	public boolean collect(String relativePath, AttributesCollector collector,
			Set<String> keysToIgnore) {
		relativePath = RawParseUtils.pathTrimLeadingSlash(relativePath);

		if (keysToIgnore == null)
			keysToIgnore = new HashSet<String>();

		for (int index = items.size() - 1; index >= 0; index--) {
			final Entry entry = getAsMatchingEntry(relativePath,
					items.get(index));
			if (entry == null)
				continue;

			for (Attribute attribute : entry.attributes) {
				final String key = attribute.getKey();
				if (!keysToIgnore.add(key))
					continue;

				if (!collector.collect(attribute))
					return false;
			}
		}

		return true;
	}

	/**
	 * @param reader
	 * @throws IOException
	 */
	public void parse(Reader reader) throws IOException {
		clear();

		StringBuilder builder = new StringBuilder();
		for (;;) {
			final int ch = reader.read();
			if (ch == -1) {
				parseLine(builder.toString());
				break;
			}

			builder.append((char) ch);
			if (ch == '\n') {
				parseLine(builder.toString());
				builder = new StringBuilder();
			}
		}
	}

	/**
	 * @return ...
	 */
	public String toText() {
		final StringBuilder builder = new StringBuilder();
		for (Object item : items) {
			if (item instanceof Entry)
				builder.append(((Entry) item).line);
			else
				builder.append(item.toString());
		}

		return builder.toString();
	}

	/**
	 *
	 */
	public void clear() {
		items.clear();
	}

	private void parseLine(String line) throws IOException {
		final Entry entry = parseEntry(line);
		if (entry != null)
			items.add(entry);
		else
			items.add(line);
	}

	private static Entry parseEntry(String line) throws IOException {
		line = line.trim();
		if (line.startsWith("#") || line.startsWith("<<<<<<<")
				|| line.startsWith("========") || line.startsWith(">>>>>>>>"))
			return null;
		String pattern = null;
		final List<Attribute> attributes = new ArrayList<Attribute>();
		final String[] tokens = line.replace('\t', ' ').split(" ");
		for (int index = 0; index < tokens.length; index++) {
			// Get rid of trailing \r and \n
			String token = tokens[index].trim();
			if (index == tokens.length - 1)
				token = token.trim();

			if (token.length() == 0)
				continue;

			if (pattern == null) {
				pattern = token;
				continue;
			}

			final Attribute attribute = parseAttribute(token);
			if (attribute == null)
				return null;

			attributes.add(attribute);
		}

		if (pattern == null)
			return null;

		try {
			return new Entry(pattern,
					attributes.toArray(new Attribute[attributes.size()]), line);
		} catch (InvalidPatternException ex) {
			throw new IOException(ex.getMessage());
		}
	}

	private static Attribute parseAttribute(String token) {
		if (token.startsWith("!"))
			return createValidAttribute(token.substring(1), null);

		if (token.startsWith("-"))
			return createValidAttribute(token.substring(1),
					AttributeValue.UNSET);

		final int equalsIndex = token.indexOf("=");
		if (equalsIndex == -1)
			return createValidAttribute(token, AttributeValue.SET);

		return createValidAttribute(token.substring(0, equalsIndex),
				AttributeValue.createValue(token.substring(equalsIndex + 1)));
	}

	private static Attribute createValidAttribute(String name,
			AttributeValue value) {
		if (!isValidAttributeName(name))
			return null;

		return new Attribute(name, value);
	}

	private static boolean isValidAttributeName(String name) {
		if (name.length() == 0)
			return false;

		if (name.charAt(0) == '-')
			return false;

		for (int index = 0; index < name.length(); index++) {
			final char ch = name.charAt(index);
			if ('0' <= ch && ch <= '9')
				continue;
			if ('a' <= ch && ch <= 'z')
				continue;
			if ('A' <= ch && ch <= 'Z')
				continue;
			if (ch == '-' || ch == '.' || ch == '_')
				continue;
			return false;
		}

		return true;
	}

	private static Entry getAsMatchingEntry(String path, Object item) {
		if (!(item instanceof Entry))
			return null;

		final Entry entry = (Entry) item;
		final FileNameMatcher matcher = entry.fileNameMatcher;
		matcher.reset();

		final String match = entry.pattern.contains("/") ? path : RawParseUtils
				.pathTail(path);
		matcher.append(match);
		return matcher.isMatch() ? entry : null;
	}

	private static class Entry {
		private final String pattern;

		private final FileNameMatcher fileNameMatcher;

		private final Attribute[] attributes;

		private final String line;

		public Entry(String pattern, Attribute[] attributes, String line)
				throws InvalidPatternException {
			this.pattern = pattern;
			this.fileNameMatcher = new FileNameMatcher(pattern, new Character(
					'/'));
			this.attributes = attributes;
			this.line = line;
		}

		@Override
		public String toString() {
			return pattern + "=" + Arrays.toString(attributes);
		}
	}
}
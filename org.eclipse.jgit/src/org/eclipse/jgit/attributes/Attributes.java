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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.util.RawParseUtils;

/**
 *
 */
public class Attributes {

	private final AttributesEntryStorage storage = new AttributesEntryStorage();

	/**
	 * @return ...
	 */
	public final List<String> getPatterns() {
		return storage.getPatterns();
	}

	/**
	 * @return ...
	 */
	public boolean isEmpty() {
		return storage.isEmpty();
	}

	/**
	 * @return ...
	 */
	public boolean collect(String relativePath, AttributesCollector collector,
	                       Set<String> keysToIgnore) {
		relativePath = RawParseUtils.pathTrimLeadingSlash(relativePath);

		if (keysToIgnore == null)
			keysToIgnore = new HashSet<String>();

		final List<AttributesEntry> patterns = storage.getPatterns(relativePath);
		for (int index = patterns.size() - 1; index >= 0; index--) {
			final AttributesEntry entry = getAsMatchingEntry(relativePath, patterns.get(index));
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
		for (Object item : storage.getItems()) {
			if (item instanceof AttributesEntry) 
				builder.append(((AttributesEntry) item).line);
			else 
				builder.append(item.toString());
		}

		return builder.toString();
	}

	/**
	 *
	 */
	public void clear() {
		storage.clear();
	}

	private void parseLine(String line) throws IOException {
		final AttributesEntry entry = parseEntry(line);
		if (entry != null)
			storage.addEntry(entry);
		else
			storage.addLine(line);
	}

	private static AttributesEntry parseEntry(String line) throws IOException {
		final List<Attribute> attributes = new ArrayList<Attribute>();
		final String pattern = parse(line, attributes);

		if (pattern == null) {
			return null;
		}

		try {
			return new AttributesEntry(pattern,
			                           attributes.toArray(new Attribute[attributes.size()]), line);
		}
		catch (InvalidPatternException ex) {
			throw new IOException(ex.getMessage());
		}
	}

	public static String parse(String line, List<Attribute> attributes) {
		String pattern = null;
		final String[] tokens = line.replace('\t', ' ').split(" ");
		for (int index = 0; index < tokens.length; index++) {
			// Get rid of trailing \r and \n
			String token = tokens[index].trim();
			if (index == tokens.length - 1)
				token = token.trim();

			if (token.length() == 0)
				continue;

			if (pattern == null) {
				if (!containsPattern(token))
					return null;

				pattern = token;
				continue;
			}

			final Attribute attribute = parseAttribute(token);
			if (attribute == null)
				return null;

			attributes.add(attribute);
		}

		return pattern;
	}

	private static boolean containsPattern(String token) {
		return !token.startsWith("#") && !token.startsWith("<<<<<<<")
				&& !token.startsWith("=======")
				&& !token.startsWith(">>>>>>>");
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

	private static AttributesEntry getAsMatchingEntry(String path, Object item) {
		if (path.startsWith("/")) {
			path = path.substring(1);
		}

		if (!(item instanceof AttributesEntry)) {
			return null;
		}

		final AttributesEntry entry = (AttributesEntry)item;
		final String match = entry.pattern.contains("/") ? path : RawParseUtils.pathTail(path);
		return entry.matcher.matches(match) ? entry : null;
	}
}
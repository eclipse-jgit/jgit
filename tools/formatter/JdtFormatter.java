/*
 * Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.format;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** JDT-backed formatter using the checked-in Eclipse profile. */
final class JdtFormatter {
	private static final String COMPILER_SOURCE = "org.eclipse.jdt.core.compiler.source";

	private static final String COMPILER_COMPLIANCE = "org.eclipse.jdt.core.compiler.compliance";

	private static final String COMPILER_TARGET = "org.eclipse.jdt.core.compiler.codegen.targetPlatform";

	private final CodeFormatter formatter;

	JdtFormatter(Path profile) throws IOException {
		Map<String, String> settings = readSettings(profile);
		settings.put(COMPILER_SOURCE, "25");
		settings.put(COMPILER_COMPLIANCE, "25");
		settings.put(COMPILER_TARGET, "25");
		formatter = ToolFactory.createCodeFormatter(settings);
	}

	String format(String source, List<GitDiff.LineRange> ranges)
			throws IOException {
		String formatted = source;
		String lineSeparator = lineSeparator(source);
		List<GitDiff.LineRange> descending = new ArrayList<>(ranges);
		descending.sort((left, right) -> Integer.compare(right.firstLine(),
				left.firstLine()));
		for (GitDiff.LineRange range : descending) {
			int offset = lineStartOffset(formatted, range.firstLine());
			if (offset < 0) {
				continue;
			}
			int end = lineStartOffset(formatted,
					range.firstLine() + range.lineCount());
			if (end < 0) {
				end = formatted.length();
			}
			if (end <= offset) {
				continue;
			}
			TextEdit edit = formatter.format(
					CodeFormatter.K_COMPILATION_UNIT
							| CodeFormatter.F_INCLUDE_COMMENTS,
					formatted, offset, end - offset, 0, lineSeparator);
			if (edit == null) {
				continue;
			}
			Document document = new Document(formatted);
			try {
				edit.apply(document);
			} catch (BadLocationException | MalformedTreeException e) {
				throw new IOException("Cannot apply JDT formatter edits", e);
			}
			formatted = document.get();
		}
		return formatted;
	}

	private static Map<String, String> readSettings(Path profile)
			throws IOException {
		if (!Files.isRegularFile(profile)) {
			throw new IOException("Formatter profile not found: " + profile);
		}
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		try {
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature(
					"http://apache.org/xml/features/disallow-doctype-decl",
					true);
			factory.setFeature(
					"http://xml.org/sax/features/external-general-entities",
					false);
			factory.setFeature(
					"http://xml.org/sax/features/external-parameter-entities",
					false);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			Element profileElement = selectProfile(factory.newDocumentBuilder()
					.parse(profile.toFile()).getElementsByTagName("profile"));
			NodeList settings = profileElement.getElementsByTagName("setting");
			Map<String, String> result = new HashMap<>();
			for (int i = 0; i < settings.getLength(); i++) {
				Element setting = (Element) settings.item(i);
				result.put(setting.getAttribute("id"),
						setting.getAttribute("value"));
			}
			if (result.isEmpty()) {
				throw new IOException("No formatter settings in " + profile);
			}
			return result;
		} catch (ParserConfigurationException | SAXException
				| IllegalArgumentException e) {
			throw new IOException("Cannot read formatter profile " + profile,
					e);
		}
	}

	private static Element selectProfile(NodeList profiles) throws IOException {
		Element firstFormatterProfile = null;
		for (int i = 0; i < profiles.getLength(); i++) {
			Element candidate = (Element) profiles.item(i);
			if (!"CodeFormatterProfile"
					.equals(candidate.getAttribute("kind"))) {
				continue;
			}
			if (firstFormatterProfile == null) {
				firstFormatterProfile = candidate;
			}
			if ("JGit Format".equals(candidate.getAttribute("name"))) {
				return candidate;
			}
		}
		if (firstFormatterProfile != null) {
			return firstFormatterProfile;
		}
		throw new IOException("No CodeFormatterProfile element found");
	}

	private static int lineStartOffset(String source, int line) {
		if (line < 1) {
			return -1;
		}
		if (line == 1) {
			return 0;
		}
		int currentLine = 1;
		for (int offset = 0; offset < source.length(); offset++) {
			char character = source.charAt(offset);
			if (character == '\r') {
				if (offset + 1 < source.length()
						&& source.charAt(offset + 1) == '\n') {
					offset++;
				}
				currentLine++;
				if (currentLine == line) {
					return offset + 1;
				}
			} else if (character == '\n') {
				currentLine++;
				if (currentLine == line) {
					return offset + 1;
				}
			}
		}
		return -1;
	}

	private static String lineSeparator(String source) {
		for (int i = 0; i < source.length(); i++) {
			char character = source.charAt(i);
			if (character == '\r') {
				return i + 1 < source.length() && source.charAt(i + 1) == '\n'
						? "\r\n"
						: "\r";
			}
			if (character == '\n') {
				return "\n";
			}
		}
		return "\n";
	}
}

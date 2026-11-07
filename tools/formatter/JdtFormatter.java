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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

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
	private static final Pattern TRAILING_WS = Pattern
			.compile("[ \\t]+(?=\\R|\\z)");

	private static final String COMPILER_SOURCE = "org.eclipse.jdt.core.compiler.source";

	private static final String COMPILER_COMPLIANCE = "org.eclipse.jdt.core.compiler.compliance";

	private static final String COMPILER_TARGET = "org.eclipse.jdt.core.compiler.codegen.targetPlatform";

	private final CodeFormatter formatter;

	JdtFormatter(Path profile) throws IOException {
		Map<String, String> settings = readSettings(profile);
		settings.put(COMPILER_SOURCE, FormatterConfig.JAVA_VERSION);
		settings.put(COMPILER_COMPLIANCE, FormatterConfig.JAVA_VERSION);
		settings.put(COMPILER_TARGET, FormatterConfig.JAVA_VERSION);
		formatter = ToolFactory.createCodeFormatter(settings);
	}

	String format(String source) throws IOException {
		Document document = new Document(source);
		TextEdit edit = formatter.format(
				CodeFormatter.K_COMPILATION_UNIT
						| CodeFormatter.F_INCLUDE_COMMENTS,
				source, 0, source.length(), 0, lineSeparator(document));
		if (edit != null) {
			try {
				edit.apply(document);
			} catch (BadLocationException | MalformedTreeException e) {
				throw new IOException("Cannot apply JDT formatter edits", e);
			}
		}
		return TRAILING_WS.matcher(document.get()).replaceAll("");
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

	private static String lineSeparator(Document document) throws IOException {
		if (document.getLength() == 0) {
			return "\n";
		}
		try {
			String delimiter = document.getLineDelimiter(0);
			return delimiter == null ? "\n" : delimiter;
		} catch (BadLocationException e) {
			throw new IOException("Cannot determine source line delimiter", e);
		}
	}

}

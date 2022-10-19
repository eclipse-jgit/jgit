/*
 * Copyright (C) 2022, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import org.eclipse.jgit.api.errors.InvalidConfigurationException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.jupiter.api.Test;

public class AbbrevConfigTest extends RepositoryTestCase {

	@Test
	void testDefault() throws Exception {
		assertEquals(7, testCoreAbbrev(null));
	}

	@Test
	void testAuto() throws Exception {
		assertEquals(7, testCoreAbbrev("auto"));
	}

	@Test
	void testNo() throws Exception {
		assertEquals(40, testCoreAbbrev("no"));
	}

	@Test
	void testValidMin() throws Exception {
		assertEquals(4, testCoreAbbrev("4"));
	}

	@Test
	void testValid() throws Exception {
		assertEquals(22, testCoreAbbrev("22"));
	}

	@Test
	void testValidMax() throws Exception {
		assertEquals(40, testCoreAbbrev("40"));
	}

	@Test
	void testInvalid() {
		assertThrows(InvalidConfigurationException.class,
				() -> testCoreAbbrev("foo"));
	}

	@Test
	void testInvalid2() {
		assertThrows(InvalidConfigurationException.class,
				() -> testCoreAbbrev("2k"));
	}

	@Test
	void testInvalidNegative() {
		assertThrows(InvalidConfigurationException.class,
				() -> testCoreAbbrev("-1000"));
	}

	@Test
	void testInvalidBelowRange() {
		assertThrows(InvalidConfigurationException.class,
				() -> testCoreAbbrev("3"));
	}

	@Test
	void testInvalidBelowRange2() {
		assertThrows(InvalidConfigurationException.class,
				() -> testCoreAbbrev("-1"));
	}

	@Test
	void testInvalidAboveRange() {
		assertThrows(InvalidConfigurationException.class,
				() -> testCoreAbbrev("41"));
	}

	@Test
	void testInvalidAboveRange2() {
		assertThrows(InvalidConfigurationException.class,
				() -> testCoreAbbrev("100000"));
	}

	@Test
	void testToStringNo()
			throws InvalidConfigurationException, IOException {
		assertEquals("40", setCoreAbbrev("no").toString());
	}

	@Test
	void testToString()
			throws InvalidConfigurationException, IOException {
		assertEquals("7", setCoreAbbrev("auto").toString());
	}

	@Test
	void testToString12()
			throws InvalidConfigurationException, IOException {
		assertEquals("12", setCoreAbbrev("12").toString());
	}

	private int testCoreAbbrev(String value)
			throws InvalidConfigurationException, IOException {
		return setCoreAbbrev(value).get();
	}

	private AbbrevConfig setCoreAbbrev(String value)
			throws IOException, InvalidConfigurationException {
		FileBasedConfig config = db.getConfig();
		config.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_ABBREV, value);
		config.save();
		return AbbrevConfig.parseFromConfig(db);
	}

}

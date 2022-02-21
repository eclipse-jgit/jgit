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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;

import org.eclipse.jgit.api.errors.InvalidConfigurationException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.Test;

public class AbbrevConfigTest extends RepositoryTestCase {

	@Test
	public void testDefault() throws Exception {
		assertEquals(7, testCoreAbbrev(null));
	}

	@Test
	public void testAuto() throws Exception {
		assertEquals(7, testCoreAbbrev("auto"));
	}

	@Test
	public void testNo() throws Exception {
		assertEquals(40, testCoreAbbrev("no"));
	}

	@Test
	public void testValidMin() throws Exception {
		assertEquals(4, testCoreAbbrev("4"));
	}

	@Test
	public void testValid() throws Exception {
		assertEquals(22, testCoreAbbrev("22"));
	}

	@Test
	public void testValidMax() throws Exception {
		assertEquals(40, testCoreAbbrev("40"));
	}

	@Test
	public void testInvalid() {
		assertThrows(InvalidConfigurationException.class,
				() -> testCoreAbbrev("foo"));
	}

	@Test
	public void testInvalid2() {
		assertThrows(InvalidConfigurationException.class,
				() -> testCoreAbbrev("2k"));
	}

	@Test
	public void testInvalidNegative() {
		assertThrows(InvalidConfigurationException.class,
				() -> testCoreAbbrev("-1000"));
	}

	@Test
	public void testInvalidBelowRange() {
		assertThrows(InvalidConfigurationException.class,
				() -> testCoreAbbrev("3"));
	}

	@Test
	public void testInvalidBelowRange2() {
		assertThrows(InvalidConfigurationException.class,
				() -> testCoreAbbrev("-1"));
	}

	@Test
	public void testInvalidAboveRange() {
		assertThrows(InvalidConfigurationException.class,
				() -> testCoreAbbrev("41"));
	}

	@Test
	public void testInvalidAboveRange2() {
		assertThrows(InvalidConfigurationException.class,
				() -> testCoreAbbrev("100000"));
	}

	@Test
	public void testToStringNo()
			throws InvalidConfigurationException, IOException {
		assertEquals("40", setCoreAbbrev("no").toString());
	}

	@Test
	public void testToString()
			throws InvalidConfigurationException, IOException {
		assertEquals("7", setCoreAbbrev("auto").toString());
	}

	@Test
	public void testToString12()
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

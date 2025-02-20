/*
 * Copyright (C) 2024 Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.apache.sshd.client.config.hosts.KnownHostEntry;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.junit.Test;

public class KnownHostEntryReaderTest {

	@Test
	public void testUnsupportedHostKeyLine() {
		KnownHostEntry entry = KnownHostEntryReader.parseHostEntry(
				"[localhost]:2222 ssh-unknown AAAAC3NzaC1lZDI1NTE5AAAAIPu6ntmyfSOkqLl3qPxD5XxwW7OONwwSG3KO+TGn+PFu");
		AuthorizedKeyEntry keyEntry = entry.getKeyEntry();
		assertNotNull(keyEntry);
		assertEquals("ssh-unknown", keyEntry.getKeyType());
	}
}

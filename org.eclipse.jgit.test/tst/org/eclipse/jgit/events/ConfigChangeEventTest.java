/*
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.Test;

public class ConfigChangeEventTest extends RepositoryTestCase {
	@Test
	public void testFileRepository_ChangeEventsOnlyOnSave() throws Exception {
		final ConfigChangedEvent[] events = new ConfigChangedEvent[1];
		db.getListenerList()
				.addConfigChangedListener((ConfigChangedEvent event) -> {
					events[0] = event;
				});
		FileBasedConfig config = db.getConfig();
		assertNull(events[0]);

		// set a value to some arbitrary key
		config.setString("test", "section", "event", "value");
		// no changes until we save
		assertNull(events[0]);
		config.save();
		assertNotNull(events[0]);
		// correct repository?
		assertEquals(events[0].getRepository(), db);

		// reset for the next test
		events[0] = null;

		// unset the value we have just set above
		config.unset("test", "section", "event");
		// no changes until we save
		assertNull(events[0]);
		config.save();
		assertNotNull(events[0]);
		// correct repository?
		assertEquals(events[0].getRepository(), db);
	}
}

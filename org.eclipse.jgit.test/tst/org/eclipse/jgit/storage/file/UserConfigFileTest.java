/*
 * Copyright (C) 2023, Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.storage.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.util.FS;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class UserConfigFileTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void testParentOnlyLoad() throws Exception {
		Path xdg = tmp.getRoot().toPath().resolve("xdg.cfg");
		Files.writeString(xdg, "[user]\n\tname = Archibald Ulysses Thor");
		Path user = tmp.getRoot().toPath().resolve("user.cfg");
		UserConfigFile config = new UserConfigFile(null, user.toFile(),
				xdg.toFile(), FS.DETECTED);
		config.load();
		assertEquals("Archibald Ulysses Thor",
				config.getString("user", null, "name"));
	}

	@Test
	public void testLoadBoth() throws Exception {
		Path xdg = tmp.getRoot().toPath().resolve("xdg.cfg");
		Files.writeString(xdg, "[user]\n\tname = Archibald Ulysses Thor");
		Path user = tmp.getRoot().toPath().resolve("user.cfg");
		Files.writeString(user, "[user]\n\temail = a.u.thor@example.com");
		UserConfigFile config = new UserConfigFile(null, user.toFile(),
				xdg.toFile(), FS.DETECTED);
		config.load();
		assertEquals("Archibald Ulysses Thor",
				config.getString("user", null, "name"));
		assertEquals("a.u.thor@example.com",
				config.getString("user", null, "email"));
	}

	@Test
	public void testOverwriteChild() throws Exception {
		Path xdg = tmp.getRoot().toPath().resolve("xdg.cfg");
		Files.writeString(xdg, "[user]\n\tname = Archibald Ulysses Thor");
		Path user = tmp.getRoot().toPath().resolve("user.cfg");
		Files.writeString(user, "[user]\n\temail = a.u.thor@example.com");
		UserConfigFile config = new UserConfigFile(null, user.toFile(),
				xdg.toFile(), FS.DETECTED);
		config.load();
		assertEquals("Archibald Ulysses Thor",
				config.getString("user", null, "name"));
		assertEquals("a.u.thor@example.com",
				config.getString("user", null, "email"));
		config.setString("user", null, "name", "A U Thor");
		assertEquals("A U Thor", config.getString("user", null, "name"));
		config.save();
		UserConfigFile config2 = new UserConfigFile(null, user.toFile(),
				xdg.toFile(), FS.DETECTED);
		config2.load();
		assertEquals("A U Thor", config2.getString("user", null, "name"));
		assertEquals("a.u.thor@example.com",
				config.getString("user", null, "email"));
		FileBasedConfig cfg = new FileBasedConfig(null, xdg.toFile(),
				FS.DETECTED);
		cfg.load();
		assertEquals("Archibald Ulysses Thor",
				cfg.getString("user", null, "name"));
		assertNull(cfg.getString("user", null, "email"));
	}

	@Test
	public void testUnset() throws Exception {
		Path xdg = tmp.getRoot().toPath().resolve("xdg.cfg");
		Files.writeString(xdg, "[user]\n\tname = Archibald Ulysses Thor");
		Path user = tmp.getRoot().toPath().resolve("user.cfg");
		Files.writeString(user, "[user]\n\temail = a.u.thor@example.com");
		UserConfigFile config = new UserConfigFile(null, user.toFile(),
				xdg.toFile(), FS.DETECTED);
		config.load();
		assertEquals("Archibald Ulysses Thor",
				config.getString("user", null, "name"));
		assertEquals("a.u.thor@example.com",
				config.getString("user", null, "email"));
		config.setString("user", null, "name", "A U Thor");
		assertEquals("A U Thor", config.getString("user", null, "name"));
		config.unset("user", null, "name");
		assertEquals("Archibald Ulysses Thor",
				config.getString("user", null, "name"));
		assertEquals("a.u.thor@example.com",
				config.getString("user", null, "email"));
		config.save();
		UserConfigFile config2 = new UserConfigFile(null, user.toFile(),
				xdg.toFile(), FS.DETECTED);
		config2.load();
		assertEquals("Archibald Ulysses Thor",
				config2.getString("user", null, "name"));
		assertEquals("a.u.thor@example.com",
				config.getString("user", null, "email"));
		FileBasedConfig cfg = new FileBasedConfig(null, user.toFile(),
				FS.DETECTED);
		cfg.load();
		assertNull(cfg.getString("user", null, "name"));
		assertEquals("a.u.thor@example.com",
				cfg.getString("user", null, "email"));
	}

	@Test
	public void testUnsetSection() throws Exception {
		Path xdg = tmp.getRoot().toPath().resolve("xdg.cfg");
		Files.writeString(xdg, "[user]\n\tname = Archibald Ulysses Thor");
		Path user = tmp.getRoot().toPath().resolve("user.cfg");
		Files.writeString(user, "[user]\n\temail = a.u.thor@example.com");
		UserConfigFile config = new UserConfigFile(null, user.toFile(),
				xdg.toFile(), FS.DETECTED);
		config.load();
		assertEquals("Archibald Ulysses Thor",
				config.getString("user", null, "name"));
		assertEquals("a.u.thor@example.com",
				config.getString("user", null, "email"));
		config.unsetSection("user", null);
		assertEquals("Archibald Ulysses Thor",
				config.getString("user", null, "name"));
		config.save();
		assertTrue(Files.readString(user).strip().isEmpty());
	}

	@Test
	public void testNoChild() throws Exception {
		Path xdg = tmp.getRoot().toPath().resolve("xdg.cfg");
		Files.writeString(xdg, "[user]\n\tname = Archibald Ulysses Thor");
		Path user = tmp.getRoot().toPath().resolve("user.cfg");
		UserConfigFile config = new UserConfigFile(null, user.toFile(),
				xdg.toFile(), FS.DETECTED);
		config.load();
		assertEquals("Archibald Ulysses Thor",
				config.getString("user", null, "name"));
		assertNull(config.getString("user", null, "email"));
		config.setString("user", null, "email", "a.u.thor@example.com");
		assertEquals("a.u.thor@example.com",
				config.getString("user", null, "email"));
		config.save();
		assertFalse(Files.exists(user));
		UserConfigFile config2 = new UserConfigFile(null, user.toFile(),
				xdg.toFile(), FS.DETECTED);
		config2.load();
		assertEquals("Archibald Ulysses Thor",
				config2.getString("user", null, "name"));
		assertEquals("a.u.thor@example.com",
				config2.getString("user", null, "email"));
	}

	@Test
	public void testNoFiles() throws Exception {
		Path xdg = tmp.getRoot().toPath().resolve("xdg.cfg");
		Path user = tmp.getRoot().toPath().resolve("user.cfg");
		UserConfigFile config = new UserConfigFile(null, user.toFile(),
				xdg.toFile(), FS.DETECTED);
		config.load();
		assertNull(config.getString("user", null, "name"));
		assertNull(config.getString("user", null, "email"));
		config.setString("user", null, "name", "Archibald Ulysses Thor");
		config.setString("user", null, "email", "a.u.thor@example.com");
		assertEquals("Archibald Ulysses Thor",
				config.getString("user", null, "name"));
		assertEquals("a.u.thor@example.com",
				config.getString("user", null, "email"));
		config.save();
		assertTrue(Files.exists(user));
		assertFalse(Files.exists(xdg));
		UserConfigFile config2 = new UserConfigFile(null, user.toFile(),
				xdg.toFile(), FS.DETECTED);
		config2.load();
		assertEquals("Archibald Ulysses Thor",
				config2.getString("user", null, "name"));
		assertEquals("a.u.thor@example.com",
				config2.getString("user", null, "email"));
	}

	@Test
	public void testSetInXdg() throws Exception {
		Path xdg = tmp.getRoot().toPath().resolve("xdg.cfg");
		Files.writeString(xdg, "[user]\n\tname = Archibald Ulysses Thor");
		Path user = tmp.getRoot().toPath().resolve("user.cfg");
		UserConfigFile config = new UserConfigFile(null, user.toFile(),
				xdg.toFile(), FS.DETECTED);
		config.load();
		assertEquals("Archibald Ulysses Thor",
				config.getString("user", null, "name"));
		config.setString("user", null, "email", "a.u.thor@example.com");
		config.save();
		assertFalse(Files.exists(user));
		FileBasedConfig cfg = new FileBasedConfig(null, xdg.toFile(),
				FS.DETECTED);
		cfg.load();
		assertEquals("Archibald Ulysses Thor",
				cfg.getString("user", null, "name"));
		assertEquals("a.u.thor@example.com",
				cfg.getString("user", null, "email"));
	}

	@Test
	public void testUserConfigCreated() throws Exception {
		Path xdg = tmp.getRoot().toPath().resolve("xdg.cfg");
		Files.writeString(xdg, "[user]\n\tname = Archibald Ulysses Thor");
		Path user = tmp.getRoot().toPath().resolve("user.cfg");
		Thread.sleep(3000); // Avoid racily clean isOutdated() below.
		UserConfigFile config = new UserConfigFile(null, user.toFile(),
				xdg.toFile(), FS.DETECTED);
		config.load();
		assertEquals("Archibald Ulysses Thor",
				config.getString("user", null, "name"));
		Files.writeString(user,
				"[user]\n\temail = a.u.thor@example.com\n\tname = A U Thor");
		assertEquals("Archibald Ulysses Thor",
				config.getString("user", null, "name"));
		assertTrue(config.isOutdated());
		config.load();
		assertEquals("A U Thor", config.getString("user", null, "name"));
		assertEquals("a.u.thor@example.com",
				config.getString("user", null, "email"));
	}

	@Test
	public void testUserConfigDeleted() throws Exception {
		Path xdg = tmp.getRoot().toPath().resolve("xdg.cfg");
		Files.writeString(xdg, "[user]\n\tname = Archibald Ulysses Thor");
		Path user = tmp.getRoot().toPath().resolve("user.cfg");
		Files.writeString(user,
				"[user]\n\temail = a.u.thor@example.com\n\tname = A U Thor");
		Thread.sleep(3000); // Avoid racily clean isOutdated() below.
		UserConfigFile config = new UserConfigFile(null, user.toFile(),
				xdg.toFile(), FS.DETECTED);
		config.load();
		assertEquals("A U Thor", config.getString("user", null, "name"));
		assertEquals("a.u.thor@example.com",
				config.getString("user", null, "email"));
		Files.delete(user);
		assertEquals("A U Thor", config.getString("user", null, "name"));
		assertEquals("a.u.thor@example.com",
				config.getString("user", null, "email"));
		assertTrue(config.isOutdated());
		config.load();
		assertEquals("Archibald Ulysses Thor",
				config.getString("user", null, "name"));
		assertNull(config.getString("user", null, "email"));
	}

	@Test
	public void testXdgConfigDeleted() throws Exception {
		Path xdg = tmp.getRoot().toPath().resolve("xdg.cfg");
		Files.writeString(xdg, "[user]\n\tname = Archibald Ulysses Thor");
		Path user = tmp.getRoot().toPath().resolve("user.cfg");
		Thread.sleep(3000); // Avoid racily clean isOutdated() below.
		UserConfigFile config = new UserConfigFile(null, user.toFile(),
				xdg.toFile(), FS.DETECTED);
		config.load();
		assertEquals("Archibald Ulysses Thor",
				config.getString("user", null, "name"));
		Files.delete(xdg);
		assertEquals("Archibald Ulysses Thor",
				config.getString("user", null, "name"));
		assertTrue(config.isOutdated());
		config.load();
		assertNull(config.getString("user", null, "name"));
	}

	@Test
	public void testXdgConfigDeletedUserConfigExists() throws Exception {
		Path xdg = tmp.getRoot().toPath().resolve("xdg.cfg");
		Files.writeString(xdg, "[user]\n\tname = Archibald Ulysses Thor");
		Path user = tmp.getRoot().toPath().resolve("user.cfg");
		Files.writeString(user,
				"[user]\n\temail = a.u.thor@example.com\n\tname = A U Thor");
		Thread.sleep(3000); // Avoid racily clean isOutdated() below.
		UserConfigFile config = new UserConfigFile(null, user.toFile(),
				xdg.toFile(), FS.DETECTED);
		config.load();
		assertEquals("A U Thor", config.getString("user", null, "name"));
		Files.delete(xdg);
		assertTrue(config.isOutdated());
		config.load();
		assertEquals("A U Thor", config.getString("user", null, "name"));
	}

}

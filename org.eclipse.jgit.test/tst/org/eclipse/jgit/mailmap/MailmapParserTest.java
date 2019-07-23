/*
 * Copyright (c) 2019 Brian Riehman <briehman@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.mailmap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.io.FileInputStream;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Test;

public class MailmapParserTest {

	@Test
	public void simpleNameAndEmail() throws Exception {
		Mailmap mailmap = parse("mailmap-simple");
		PersonIdent mapped = mailmap
				.map(new PersonIdent("Commit Name", "commit@email.xx"));
		assertThat(mapped.getName(), is("Proper Name"));
		assertThat(mapped.getEmailAddress(), is("commit@email.xx"));
	}

	@Test
	public void complexEmailOnly() throws Exception {
		Mailmap mailmap = parse("mailmap-complex-email-only");
		PersonIdent mapped = mailmap
				.map(new PersonIdent("Commit Name", "commit@email.xx"));
		assertThat(mapped.getName(), is("Commit Name"));
		assertThat(mapped.getEmailAddress(), is("proper@email.xx"));
	}

	@Test
	public void complexNameAndEmail() throws Exception {
		Mailmap mailmap = parse("mailmap-complex-proper-and-email");
		PersonIdent mapped = mailmap
				.map(new PersonIdent("Commit Name", "commit@email.xx"));
		assertThat(mapped.getName(), is("Proper Name"));
		assertThat(mapped.getEmailAddress(), is("proper@email.xx"));
	}

	@Test
	public void complexNameAllSpecified() throws Exception {
		Mailmap mailmap = parse("mailmap-complex-all");
		PersonIdent mapped = mailmap
				.map(new PersonIdent("Commit Name", "commit@email.xx"));
		assertThat(mapped.getName(), is("Proper Name"));
		assertThat(mapped.getEmailAddress(), is("proper@email.xx"));
	}

	private Mailmap parse(String mailmapFilename) throws Exception {
		File testResourceFile = JGitTestUtil
				.getTestResourceFile(mailmapFilename);
		try (FileInputStream fis = new FileInputStream(testResourceFile)) {
			return MailmapParser.parse(fis);
		}
	}
}
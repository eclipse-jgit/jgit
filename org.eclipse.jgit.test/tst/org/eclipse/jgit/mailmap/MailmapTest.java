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

import static junit.framework.TestCase.assertNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Collections;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Test;

public class MailmapTest {

	@Test
	public void mapNullReturnsNull() {
		Mailmap mailmap = new Mailmap();
		assertNull(mailmap.map(null));
	}

	@Test
	public void noMatchLeavesIdentUnchanged() {
		Mailmap mailmap = new Mailmap();
		PersonIdent person = new PersonIdent("Proper Name", "commit@email.cx");
		PersonIdent mapped = mailmap.map(person);
		assertThat(mapped, is(person));
	}

	@Test
	public void previousEntriesTakePrecedence() {
		Mailmap mailmap = new Mailmap(Collections
				.singletonList(new MailmapEntry(null, "commit@email.cx", "Proper Name", null)));
		Mailmap appendMailmap = new Mailmap(Collections
				.singletonList(new MailmapEntry(null, "commit@email.cx", "Later Name", null)));
		mailmap.append(appendMailmap);

		PersonIdent person = new PersonIdent("Old Name", "commit@email.cx");
		PersonIdent mapped = mailmap.map(person);
		assertThat(mapped.getName(), is("Proper Name"));
	}
}
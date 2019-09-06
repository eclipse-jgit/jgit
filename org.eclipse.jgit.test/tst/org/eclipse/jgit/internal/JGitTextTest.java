/*
 * Copyright (C) 2019, Mincong Huang <mincong.h@gmail.com>
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
package org.eclipse.jgit.internal;

import java.util.Locale;
import org.eclipse.jgit.nls.NLS;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JGitTextTest {

	@BeforeClass
	public static void setUpBeforeClass() {
		NLS.setLocale(Locale.ENGLISH);
	}

	@Test
	public void abbreviationLengthMustBeNonNegative() {
		assertEquals("Abbreviation length must not be negative.",
				JGitText.abbreviationLengthMustBeNonNegative());
	}

	@Test
	public void abortingRebase() {
		assertEquals(
				"Aborting rebase: resetting to 16beb10ce1e456e26675d736cceabb8cc2c58c09",
				JGitText.abortingRebase(
						"16beb10ce1e456e26675d736cceabb8cc2c58c09"));
	}

	@Test
	public void abortingRebaseFailed() {
		assertEquals("Could not abort rebase", JGitText.abortingRebaseFailed());
	}

	@Test
	public void abortingRebaseFailedNoOrigHead() {
		assertEquals("Could not abort rebase since ORIG_HEAD is null",
				JGitText.abortingRebaseFailedNoOrigHead());
	}

	@Test
	public void advertisementCameBefore() {
		assertEquals("advertisement of name1^{} came before name2",
				JGitText.advertisementCameBefore("name1", "name2"));
	}

	@Test
	public void advertisementOfCameBefore() {
		assertEquals("advertisement of name1^{} came before name2",
				JGitText.advertisementOfCameBefore("name1", "name2"));
	}

	@Test
	public void amazonS3ActionFailed() {
		assertEquals("anAction of 'aKey' failed: 1 anErrorMessage", JGitText
				.amazonS3ActionFailed("anAction", "aKey", 1, "anErrorMessage"));
	}

	@Test
	public void amazonS3ActionFailedGivingUp() {
		assertEquals("anAction of 'aKey' failed: Giving up after 1 attempts.",
				JGitText.amazonS3ActionFailedGivingUp("anAction", "aKey", 1));
	}

	@Test
	public void ambiguousObjectAbbreviation() {
		assertEquals("Object abbreviation aName is ambiguous",
				JGitText.ambiguousObjectAbbreviation("aName"));
	}

	@Test
	public void aNewObjectIdIsRequired() {
		assertEquals("A NewObjectId is required.",
				JGitText.aNewObjectIdIsRequired());
	}

	@Test
	public void anExceptionOccurredWhileTryingToAddTheIdOfHEAD() {
		assertEquals("An exception occurred while trying to add the Id of HEAD",
				JGitText.anExceptionOccurredWhileTryingToAddTheIdOfHEAD());
	}
}

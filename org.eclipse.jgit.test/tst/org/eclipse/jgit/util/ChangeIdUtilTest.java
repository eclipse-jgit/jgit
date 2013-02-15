/*
 * Copyright (C) 2010, Robin Rosenberg
 * Copyright (C) 2009, Google, Inc.
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
package org.eclipse.jgit.util;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Test;

/**
 * Portions of this test is from CommitMsgHookTest in the Android project Gerrit
 */
public class ChangeIdUtilTest {

	private final String SOB1 = "Signed-off-by: J Author <ja@example.com>\n";

	private final String SOB2 = "Signed-off-by: J Committer <jc@example.com>\n";

	final PersonIdent p = RawParseUtils.parsePersonIdent(
			"A U Thor <author@example.com> 1142878501 -0500");

	final PersonIdent q = RawParseUtils.parsePersonIdent(
			"W Riter <writer@example.com> 1142878502 -0500");

	ObjectId treeId = ObjectId
			.fromString("f51de923607cd51cf872b928a6b523ba823f7f35");

	ObjectId treeId1 = ObjectId
			.fromString("4b825dc642cb6eb9a060e54bf8d69288fbee4904");

	final ObjectId treeId2 = ObjectId
			.fromString("617601c79811cbbae338512798318b4e5b70c9ac");

	ObjectId parentId = ObjectId
			.fromString("91fea719aaf9447feb9580477eb3dd08b62b5eca");

	ObjectId parentId1 = null;

	final ObjectId parentId2 = ObjectId
			.fromString("485c91e0600b165c301c278bfbae3e492413980c");

	MockSystemReader mockSystemReader = new MockSystemReader();

	final long when = mockSystemReader.getCurrentTime();

	final int tz = new MockSystemReader().getTimezone(when);

	PersonIdent author = new PersonIdent("J. Author", "ja@example.com");
	{
		author = new PersonIdent(author, when, tz);
	}

	PersonIdent committer = new PersonIdent("J. Committer", "jc@example.com");
	{
		committer = new PersonIdent(committer, when, tz);
	}

	@Test
	public void testClean() {
		assertEquals("hej", ChangeIdUtil.clean("hej\n\n"));
		assertEquals("hej\n\nsan", ChangeIdUtil.clean("hej\n\nsan\n\n"));
		assertEquals("hej\nsan", ChangeIdUtil.clean("hej\n#men\nsan\n\n#men"));
		assertEquals("hej\nsan", ChangeIdUtil.clean("hej\nsan\n\n#men"));
		assertEquals("hej\nsan", ChangeIdUtil.clean("#no\nhej\nsan\n\n#men"));
		assertEquals("hej\nsan", ChangeIdUtil
				.clean("#no\nhej\nsan\nSigned-off-by: me \n#men"));
	}

	@Test
	public void testId() throws IOException {
		String msg = "A\nMessage\n";
		ObjectId id = ChangeIdUtil.computeChangeId(treeId, parentId, p, q, msg);
		assertEquals("73f3751208ac92cbb76f9a26ac4a0d9d472e381b", ObjectId
				.toString(id));
	}

	@Test
	public void testHasChangeid() throws Exception {
		assertEquals(
				"has changeid\nmore text\n\nBug: 33\nSigned-off-by: me@you.too\n"
						+ "Change-Id: I0123456789012345678901234567890123456789\n",
				call("has changeid\nmore text\n\nBug: 33\nSigned-off-by: me@you.too\n"
						+ "Change-Id: I0123456789012345678901234567890123456789\n"));
	}

	@Test
	public void testHasChangeidWithReplacement() throws Exception {
		assertEquals(
				"has changeid\nmore text\n\nSigned-off-by: me@you.too\n"
						+ "Change-Id: I2178563fada5edb2c99a8d8c0d619471b050ec24\nBug: 33\n",
				call("has changeid\nmore text\n\nSigned-off-by: me@you.too\n"
						+ "Change-Id: I0123456789012345678901234567890123456789\nBug: 33\n",
						true));
	}

	@Test
	public void testHasChangeidWithReplacementInLastLine() throws Exception {
		assertEquals(
				"has changeid\nmore text\n\nBug: 33\nSigned-off-by: me@you.too\n"
						+ "Change-Id: I1d6578f4c96e3db4dd707705fe3d17bf658c4758\n",
				call("has changeid\nmore text\n\nBug: 33\nSigned-off-by: me@you.too\n"
						+ "Change-Id: I0123456789012345678901234567890123456789\n",
						true));
	}

	@Test
	public void testHasChangeidWithReplacementInLastLineNoLineBreak()
			throws Exception {
		assertEquals(
				"has changeid\nmore text\n\nBug: 33\nSigned-off-by: me@you.too\n"
						+ "Change-Id: I1d6578f4c96e3db4dd707705fe3d17bf658c4758",
				call("has changeid\nmore text\n\nBug: 33\nSigned-off-by: me@you.too\n"
						+ "Change-Id: I0123456789012345678901234567890123456789",
						true));
	}

	@Test
	public void testHasChangeidWithSpacesBeforeId() throws Exception {
		assertEquals(
				"has changeid\nmore text\n\nBug: 33\nSigned-off-by: me@you.too\n"
						+ "Change-Id: Ie7575eaf450fdd0002df2e642426faf251de3ad9\n",
				call("has changeid\nmore text\n\nBug: 33\nSigned-off-by: me@you.too\n"
						+ "Change-Id:    I0123456789012345678901234567890123456789\n",
						true));
	}

	@Test
	public void testHasChangeidWithReplacementWithChangeIdInCommitMessage()
			throws Exception {
		assertEquals(
				"has changeid\nmore text\n"
						+ "Change-Id: I0123456789012345678901234567890123456789\n\n"
						+ "Bug: 33\nSigned-off-by: me@you.too\n"
						+ "Change-Id: Ie48d10d59ef67995ca89688ac0171b88f10dd520\n",
				call("has changeid\nmore text\n"
						+ "Change-Id: I0123456789012345678901234567890123456789\n\n"
						+ "Bug: 33\nSigned-off-by: me@you.too\n"
						+ "Change-Id: I0123456789012345678901234567890123456789\n",
						true));
	}

	@Test
	public void testOneliner() throws Exception {
		assertEquals(
				"oneliner\n\nChange-Id: I3a98091ce4470de88d52ae317fcd297e2339f063\n",
				call("oneliner\n"));
	}

	@Test
	public void testOnelinerFollowedByBlank() throws Exception {
		assertEquals(
				"oneliner followed by blank\n\nChange-Id: I3a12c21ef342a18498f95c62efbc186cd782b743\n",
				call("oneliner followed by blank\n"));
	}

	@Test
	public void testATwoLines() throws Exception {
		assertEquals(
				"a two lines\nwith text withour break after subject line\n\nChange-Id: I549a0fed3d69b7876c54b4f5a35637135fd43fac\n",
				call("a two lines\nwith text withour break after subject line\n"));
	}

	@Test
	public void testRegularCommit() throws Exception {
		assertEquals(
				"regular commit\n\nwith header and body\n\nChange-Id: I62d8749d3c3a888c11e3fadc3924220a19389766\n",
				call("regular commit\n\nwith header and body\n"));
	}

	@Test
	public void testRegularCommitWithSob_ButNoBody() throws Exception {
		assertEquals(
				"regular commit with sob, but no body\n\nChange-Id: I0f0b4307e9944ecbd5a9f6b9489e25cfaede43c4\nSigned-off-by: me@you.too\n",
				call("regular commit with sob, but no body\n\nSigned-off-by: me@you.too\n"));
	}

	@Test
	public void testACommitWithBug_SubButNoBody() throws Exception {
		assertEquals(
				"a commit with bug, sub but no body\n\nBug: 33\nChange-Id: I337e264868613dab6d1e11a34f394db369487412\nSigned-off-by: me@you.too\n",
				call("a commit with bug, sub but no body\n\nBug: 33\nSigned-off-by: me@you.too\n"));
	}

	@Test
	public void testACommitWithSubject_NoBodySobAndBug() throws Exception {
		assertEquals(
				"a commit with subject, no body sob and bug\n\nChange-Id: Ib3616d4bf77707a3215a6cb0602c004ee119a445\nSigned-off-by: me@you.too\nBug: 33\n",
				call("a commit with subject, no body sob and bug\n\nSigned-off-by: me@you.too\nBug: 33\n"));
	}

	@Test
	public void testACommitWithSubjectBug_NonFooterLineAndSob()
			throws Exception {
		assertEquals(
				"a commit with subject bug, non-footer line and sob\n\nBug: 33\nmore text\nSigned-off-by: me@you.too\n\nChange-Id: Ia8500eab2304e6e5eac6ae488ff44d5d850d118a\n",
				call("a commit with subject bug, non-footer line and sob\n\nBug: 33\nmore text\nSigned-off-by: me@you.too\n"));
	}

	@Test
	public void testACommitWithSubject_NonFooterAndBugAndSob() throws Exception {
		assertEquals(
				"a commit with subject, non-footer and bug and sob\n\nmore text (two empty lines after bug)\nBug: 33\n\n\nChange-Id: Idac75ccbad2ab6727b8612e344df5190d87891dd\nSigned-off-by: me@you.too\n",
				call("a commit with subject, non-footer and bug and sob\n\nmore text (two empty lines after bug)\nBug: 33\n\n\nSigned-off-by: me@you.too\n"));
	}

	@Test
	public void testACommitWithSubjectBodyBugBrackersAndSob() throws Exception {
		assertEquals(
				"a commit with subject body, bug. brackers and sob\n\nText\n\nBug: 33\nChange-Id: I90ecb589bef766302532c3e00915e10114b00f62\n[bracket]\nSigned-off-by: me@you.too\n",
				call("a commit with subject body, bug. brackers and sob\n\nText\n\nBug: 33\n[bracket]\nSigned-off-by: me@you.too\n\n"));
	}

	@Test
	public void testACommitWithSubjectBodyBugLineWithASpaceAndSob()
			throws Exception {
		assertEquals(
				"a commit with subject body, bug. line with a space and sob\n\nText\n\nBug: 33\nChange-Id: I864e2218bdee033c8ce9a7f923af9e0d5dc16863\n \nSigned-off-by: me@you.too\n",
				call("a commit with subject body, bug. line with a space and sob\n\nText\n\nBug: 33\n \nSigned-off-by: me@you.too\n\n"));
	}

	@Test
	public void testACommitWithSubjectBodyBugEmptyLineAndSob() throws Exception {
		assertEquals(
				"a commit with subject body, bug. empty line and sob\n\nText\n\nBug: 33\nChange-Id: I33f119f533313883e6ada3df600c4f0d4db23a76\n \nSigned-off-by: me@you.too\n",
				call("a commit with subject body, bug. empty line and sob\n\nText\n\nBug: 33\n \nSigned-off-by: me@you.too\n\n"));
	}

	@Test
	public void testEmptyMessages() throws Exception {
		// Empty input must not produce a change id.
		hookDoesNotModify("");
		hookDoesNotModify(" ");
		hookDoesNotModify("\n");
		hookDoesNotModify("\n\n");
		hookDoesNotModify("  \n  ");

		hookDoesNotModify("#");
		hookDoesNotModify("#\n");
		hookDoesNotModify("# on branch master\n# Untracked files:\n");
		hookDoesNotModify("\n# on branch master\n# Untracked files:\n");
		hookDoesNotModify("\n\n# on branch master\n# Untracked files:\n");

		hookDoesNotModify("\n# on branch master\ndiff --git a/src b/src\n"
				+ "new file mode 100644\nindex 0000000..c78b7f0\n");
	}

	@Test
	public void testChangeIdAlreadySet() throws Exception {
		// If a Change-Id is already present in the footer, the hook must
		// not modify the message but instead must leave the identity alone.
		//
		hookDoesNotModify("a\n" + //
				"\n" + //
				"Change-Id: Iaeac9b4149291060228ef0154db2985a31111335\n");
		hookDoesNotModify("fix: this thing\n" + //
				"\n" + //
				"Change-Id: I388bdaf52ed05b55e62a22d0a20d2c1ae0d33e7e\n");
		hookDoesNotModify("fix-a-widget: this thing\n" + //
				"\n" + //
				"Change-Id: Id3bc5359d768a6400450283e12bdfb6cd135ea4b\n");
		hookDoesNotModify("FIX: this thing\n" + //
				"\n" + //
				"Change-Id: I1b55098b5a2cce0b3f3da783dda50d5f79f873fa\n");
		hookDoesNotModify("Fix-A-Widget: this thing\n" + //
				"\n" + //
				"Change-Id: I4f4e2e1e8568ddc1509baecb8c1270a1fb4b6da7\n");
	}

	@Test
	public void testChangeIdAlreadySetWithReplacement() throws Exception {
		// If a Change-Id is already present in the footer, the hook
		// replaces the Change-Id with the new value..
		//
		assertEquals("a\n" + //
				"\n" + //
				"Change-Id: Ifa324efa85bfb3c8696a46a0f67fa70c35be5f5f\n",
				call("a\n" + //
						"\n" + //
						"Change-Id: Iaeac9b4149291060228ef0154db2985a31111335\n",
						true));
		assertEquals("fix: this thing\n" + //
				"\n" + //
				"Change-Id: Ib63e4990a06412a3f24bd93bb160e98ac1bd412b\n",
				call("fix: this thing\n" + //
						"\n" + //
						"Change-Id: I388bdaf52ed05b55e62a22d0a20d2c1ae0d33e7e\n",
						true));
		assertEquals("fix-a-widget: this thing\n" + //
				"\n" + //
				"Change-Id: If0444e4d0cabcf41b3d3b46b7e9a7a64a82117af\n",
				call("fix-a-widget: this thing\n" + //
						"\n" + //
						"Change-Id: Id3bc5359d768a6400450283e12bdfb6cd135ea4b\n",
						true));
		assertEquals("FIX: this thing\n" + //
				"\n" + //
				"Change-Id: Iba5a3b2d5e5df46448f6daf362b6bfa775c6491d\n",
				call("FIX: this thing\n" + //
						"\n" + //
						"Change-Id: I1b55098b5a2cce0b3f3da783dda50d5f79f873fa\n",
						true));
		assertEquals("Fix-A-Widget: this thing\n" + //
				"\n" + //
				"Change-Id: I2573d47c62c42429fbe424d70cfba931f8f87848\n",
				call("Fix-A-Widget: this thing\n" + //
				"\n" + //
				"Change-Id: I4f4e2e1e8568ddc1509baecb8c1270a1fb4b6da7\n",
				true));
	}

	@Test
	public void testTimeAltersId() throws Exception {
		assertEquals("a\n" + //
				"\n" + //
				"Change-Id: I7fc3876fee63c766a2063df97fbe04a2dddd8d7c\n",//
				call("a\n"));

		tick();
		assertEquals("a\n" + //
				"\n" + //
				"Change-Id: I3251906b99dda598a58a6346d8126237ee1ea800\n",//
				call("a\n"));

		tick();
		assertEquals("a\n" + //
				"\n" + //
				"Change-Id: I69adf9208d828f41a3d7e41afbca63aff37c0c5c\n",//
				call("a\n"));
	}

	/** Increment the {@link #author} and {@link #committer} times. */
	protected void tick() {
		final long delta = TimeUnit.MILLISECONDS.convert(5 * 60,
				TimeUnit.SECONDS);
		final long now = author.getWhen().getTime() + delta;

		author = new PersonIdent(author, now, tz);
		committer = new PersonIdent(committer, now, tz);
	}

	@Test
	public void testFirstParentAltersId() throws Exception {
		assertEquals("a\n" + //
				"\n" + //
				"Change-Id: I7fc3876fee63c766a2063df97fbe04a2dddd8d7c\n",//
				call("a\n"));

		parentId1 = parentId2;
		assertEquals("a\n" + //
				"\n" + //
				"Change-Id: I51e86482bde7f92028541aaf724d3a3f996e7ea2\n",//
				call("a\n"));
	}

	@Test
	public void testDirCacheAltersId() throws Exception {
		assertEquals("a\n" + //
				"\n" + //
				"Change-Id: I7fc3876fee63c766a2063df97fbe04a2dddd8d7c\n",//
				call("a\n"));

		treeId1 = treeId2;
		assertEquals("a\n" + //
				"\n" + //
				"Change-Id: If56597ea9759f23b070677ea6f064c60c38da631\n",//
				call("a\n"));
	}

	@Test
	public void testSingleLineMessages() throws Exception {
		assertEquals("a\n" + //
				"\n" + //
				"Change-Id: I7fc3876fee63c766a2063df97fbe04a2dddd8d7c\n",//
				call("a\n"));

		assertEquals("fix: this thing\n" + //
				"\n" + //
				"Change-Id: I0f13d0e6c739ca3ae399a05a93792e80feb97f37\n",//
				call("fix: this thing\n"));
		assertEquals("fix-a-widget: this thing\n" + //
				"\n" + //
				"Change-Id: I1a1a0c751e4273d532e4046a501a612b9b8a775e\n",//
				call("fix-a-widget: this thing\n"));

		assertEquals("FIX: this thing\n" + //
				"\n" + //
				"Change-Id: If816d944c57d3893b60cf10c65931fead1290d97\n",//
				call("FIX: this thing\n"));
		assertEquals("Fix-A-Widget: this thing\n" + //
				"\n" + //
				"Change-Id: I3e18d00cbda2ba1f73aeb63ed8c7d57d7fd16c76\n",//
				call("Fix-A-Widget: this thing\n"));
	}

	@Test
	public void testMultiLineMessagesWithoutFooter() throws Exception {
		assertEquals("a\n" + //
				"\n" + //
				"b\n" + //
				"\n" + //
				"Change-Id: Id0b4f42d3d6fc1569595c9b97cb665e738486f5d\n",//
				call("a\n" + "\n" + "b\n"));

		assertEquals("a\n" + //
				"\n" + //
				"b\nc\nd\ne\n" + //
				"\n" + //
				"Change-Id: I7d237b20058a0f46cc3f5fabc4a0476877289d75\n",//
				call("a\n" + "\n" + "b\nc\nd\ne\n"));

		assertEquals("a\n" + //
				"\n" + //
				"b\nc\nd\ne\n" + //
				"\n" + //
				"f\ng\nh\n" + //
				"\n" + //
				"Change-Id: I382e662f47bf164d6878b7fe61637873ab7fa4e8\n",//
				call("a\n" + "\n" + "b\nc\nd\ne\n" + "\n" + "f\ng\nh\n"));
	}

	@Test
	public void testSingleLineMessagesWithSignedOffBy() throws Exception {
		assertEquals("a\n" + //
				"\n" + //
				"Change-Id: I7fc3876fee63c766a2063df97fbe04a2dddd8d7c\n" + //
				SOB1,//
				call("a\n" + "\n" + SOB1));

		assertEquals("a\n" + //
				"\n" + //
				"Change-Id: I7fc3876fee63c766a2063df97fbe04a2dddd8d7c\n" + //
				SOB1 + //
				SOB2,//
				call("a\n" + "\n" + SOB1 + SOB2));
	}

	@Test
	public void testMultiLineMessagesWithSignedOffBy() throws Exception {
		assertEquals("a\n" + //
				"\n" + //
				"b\nc\nd\ne\n" + //
				"\n" + //
				"f\ng\nh\n" + //
				"\n" + //
				"Change-Id: I382e662f47bf164d6878b7fe61637873ab7fa4e8\n" + //
				SOB1,//
				call("a\n" + "\n" + "b\nc\nd\ne\n" + "\n" + "f\ng\nh\n" + "\n"
						+ SOB1));

		assertEquals("a\n" + //
				"\n" + //
				"b\nc\nd\ne\n" + //
				"\n" + //
				"f\ng\nh\n" + //
				"\n" + //
				"Change-Id: I382e662f47bf164d6878b7fe61637873ab7fa4e8\n" + //
				SOB1 + //
				SOB2,//
				call("a\n" + //
						"\n" + //
						"b\nc\nd\ne\n" + //
						"\n" + //
						"f\ng\nh\n" + //
						"\n" + //
						SOB1 + //
						SOB2));

		assertEquals("a\n" + //
				"\n" + //
				"b: not a footer\nc\nd\ne\n" + //
				"\n" + //
				"f\ng\nh\n" + //
				"\n" + //
				"Change-Id: I8869aabd44b3017cd55d2d7e0d546a03e3931ee2\n" + //
				SOB1 + //
				SOB2,//
				call("a\n" + //
						"\n" + //
						"b: not a footer\nc\nd\ne\n" + //
						"\n" + //
						"f\ng\nh\n" + //
						"\n" + //
						SOB1 + //
						SOB2));
	}

	@Test
	public void testNoteInMiddle() throws Exception {
		assertEquals("a\n" + //
				"\n" + //
				"NOTE: This\n" + //
				"does not fix it.\n" + //
				"\n" + //
				"Change-Id: I988a127969a6ee5e58db546aab74fc46e66847f8\n", //
				call("a\n" + //
						"\n" + //
						"NOTE: This\n" + //
						"does not fix it.\n"));
	}

	@Test
	public void testKernelStyleFooter() throws Exception {
		assertEquals("a\n" + //
				"\n" + //
				"Change-Id: I1bd787f9e7590a2ac82b02c404c955ffb21877c4\n" + //
				SOB1 + //
				"[ja: Fixed\n" + //
				"     the indentation]\n" + //
				SOB2, //
				call("a\n" + //
						"\n" + //
						SOB1 + //
						"[ja: Fixed\n" + //
						"     the indentation]\n" + //
						SOB2));
	}

	@Test
	public void testChangeIdAfterBugOrIssue() throws Exception {
		assertEquals("a\n" + //
				"\n" + //
				"Bug: 42\n" + //
				"Change-Id: I8c0321227c4324e670b9ae8cf40eccc87af21b1b\n" + //
				SOB1,//
				call("a\n" + //
						"\n" + //
						"Bug: 42\n" + //
						SOB1));

		assertEquals("a\n" + //
				"\n" + //
				"Issue: 42\n" + //
				"Change-Id: Ie66e07d89ae5b114c0975b49cf326e90331dd822\n" + //
				SOB1,//
				call("a\n" + //
						"\n" + //
						"Issue: 42\n" + //
						SOB1));
	}

	public void notestCommitDashV() throws Exception {
		assertEquals("a\n" + //
				"\n" + //
				"Change-Id: I7fc3876fee63c766a2063df97fbe04a2dddd8d7c\n" + //
				SOB1 + //
				SOB2, //
				call("a\n" + //
						"\n" + //
						SOB1 + //
						SOB2 + //
						"\n" + //
						"# on branch master\n" + //
						"diff --git a/src b/src\n" + //
						"new file mode 100644\n" + //
						"index 0000000..c78b7f0\n"));
	}

	@Test
	public void testWithEndingURL() throws Exception {
		assertEquals("a\n" + //
				"\n" + //
				"http://example.com/ fixes this\n" + //
				"\n" + //
				"Change-Id: I3b7e4e16b503ce00f07ba6ad01d97a356dad7701\n", //
				call("a\n" + //
						"\n" + //
						"http://example.com/ fixes this\n"));
		assertEquals("a\n" + //
				"\n" + //
				"https://example.com/ fixes this\n" + //
				"\n" + //
				"Change-Id: I62b9039e2fc0dce274af55e8f99312a8a80a805d\n", //
				call("a\n" + //
						"\n" + //
						"https://example.com/ fixes this\n"));
		assertEquals("a\n" + //
				"\n" + //
				"ftp://example.com/ fixes this\n" + //
				"\n" + //
				"Change-Id: I71b05dc1f6b9a5540a53a693e64d58b65a8910e8\n", //
				call("a\n" + //
						"\n" + //
						"ftp://example.com/ fixes this\n"));
		assertEquals("a\n" + //
				"\n" + //
				"git://example.com/ fixes this\n" + //
				"\n" + //
				"Change-Id: Id34e942baa68d790633737d815ddf11bac9183e5\n", //
				call("a\n" + //
						"\n" + //
						"git://example.com/ fixes this\n"));
	}

	@Test
	public void testIndexOfChangeId() {
		assertEquals(3, ChangeIdUtil.indexOfChangeId("x\n" + "\n"
				+ "Change-Id: I3b7e4e16b503ce00f07ba6ad01d97a356dad7701\n",
				"\n"));
		assertEquals(3, ChangeIdUtil.indexOfChangeId("x\n" + "\n"
				+ "Change-Id:  I3b7e4e16b503ce00f07ba6ad01d97a356dad7701\n",
				"\n"));
		assertEquals(-1, ChangeIdUtil.indexOfChangeId("x\n" + "\n"
				+ "Change-Id: \n", "\n"));
		assertEquals(3, ChangeIdUtil.indexOfChangeId("x\n" + "\n"
				+ "Change-Id: I3b7e4e16b503ce00f07ba6ad01d97a356dad7701 \n",
				"\n"));
		assertEquals(12, ChangeIdUtil.indexOfChangeId("x\n" + "\n"
				+ "Bug 4711\n"
				+ "Change-Id: I3b7e4e16b503ce00f07ba6ad01d97a356dad7701\n",
				"\n"));
		assertEquals(56, ChangeIdUtil.indexOfChangeId("x\n"
				+ "Change-Id: I3b7e4e16b503ce00f07ba6ad01d97a356dad7701\n"
				+ "\n"
				+ "Change-Id: I3b7e4e16b503ce00f07ba6ad01d97a356dad7701\n",
				"\n"));
		assertEquals(5, ChangeIdUtil.indexOfChangeId("x\r\n" + "\r\n"
				+ "Change-Id: I3b7e4e16b503ce00f07ba6ad01d97a356dad7701\r\n",
				"\r\n"));
		assertEquals(3, ChangeIdUtil.indexOfChangeId("x\r" + "\r"
				+ "Change-Id: I3b7e4e16b503ce00f07ba6ad01d97a356dad7701\r",
				"\r"));
		assertEquals(3, ChangeIdUtil.indexOfChangeId("x\r" + "\r"
				+ "Change-Id: I3b7e4e16b503ce00f07ba6ad01d97a356dad7701\r",
				"\r"));
		assertEquals(8, ChangeIdUtil.indexOfChangeId("x\ny\n\nz\n" + "\n"
				+ "Change-Id: I3b7e4e16b503ce00f07ba6ad01d97a356dad7701\n",
				"\n"));
	}

	private void hookDoesNotModify(final String in) throws Exception {
		assertEquals(in, call(in));
	}

	private String call(final String body) throws Exception {
		return call(body, false);
	}

	private String call(final String body, boolean replaceExisting) throws Exception {
		ObjectId computeChangeId = ChangeIdUtil.computeChangeId(treeId1,
				parentId1, author, committer, body);
		if (computeChangeId == null)
			return body;
		return ChangeIdUtil.insertId(body, computeChangeId, replaceExisting);
	}

}

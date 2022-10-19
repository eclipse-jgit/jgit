/*
 * Copyright (C) 2011, Robin Rosenberg and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.util.GitDateFormatter.Format;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GitDateFormatterTest {

	private MockSystemReader mockSystemReader;

	private PersonIdent ident;

	@BeforeEach
	public void setUp() {
		mockSystemReader = new MockSystemReader() {
			@Override
			public long getCurrentTime() {
				return 1318125997291L;
			}
		};
		SystemReader.setInstance(mockSystemReader);
		ident = RawParseUtils
				.parsePersonIdent("A U Thor <author@example.com> 1316560165 -0400");
	}

	@AfterEach
	public void tearDown() {
		SystemReader.setInstance(null);
	}

	@Test
	void DEFAULT() {
		assertEquals("Tue Sep 20 19:09:25 2011 -0400", new GitDateFormatter(
				Format.DEFAULT).formatDate(ident));
	}

	@Test
	void RELATIVE() {
		assertEquals("3 weeks ago",
				new GitDateFormatter(Format.RELATIVE).formatDate(ident));
	}

	@Test
	void LOCAL() {
		assertEquals("Tue Sep 20 19:39:25 2011", new GitDateFormatter(
				Format.LOCAL).formatDate(ident));
	}

	@Test
	void ISO() {
		assertEquals("2011-09-20 19:09:25 -0400", new GitDateFormatter(
				Format.ISO).formatDate(ident));
	}

	@Test
	void RFC() {
		assertEquals("Tue, 20 Sep 2011 19:09:25 -0400", new GitDateFormatter(
				Format.RFC).formatDate(ident));
	}

	@Test
	void SHORT() {
		assertEquals("2011-09-20",
				new GitDateFormatter(Format.SHORT).formatDate(ident));
	}

	@Test
	void RAW() {
		assertEquals("1316560165 -0400",
				new GitDateFormatter(Format.RAW).formatDate(ident));
	}

	@Test
	void LOCALE() {
		String date = new GitDateFormatter(Format.LOCALE).formatDate(ident);
		/*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @730c14f)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @3355dbf6)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @4fd3ed67)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @288196cc)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @3f9fbc68)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @4407b1b8)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @1080d265)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @60198c7c)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @4407b1b8)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @7e06b3cb)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @331f3583)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*/assertTrue("Sep 20, 2011 7:09:25 PM -0400".equals(date)
				|| "Sep 20, 2011, 7:09:25 PM -0400".equals(date)); // JDK-8206961
	}

	@Test
	void LOCALELOCAL() {
		String date = new GitDateFormatter(Format.LOCALELOCAL)
				.formatDate(ident);
		assertTrue("Sep 20, 2011 7:39:25 PM".equals(date)
				|| "Sep 20, 2011, 7:39:25 PM".equals(date)); // JDK-8206961
	}
}

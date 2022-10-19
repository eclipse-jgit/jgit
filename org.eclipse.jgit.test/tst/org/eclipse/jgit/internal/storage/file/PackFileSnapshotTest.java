/*
 * Copyright (C) 2019, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.Random;
import java.util.zip.Deflater;

import org.eclipse.jgit.api.GarbageCollectCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.util.FS;
import org.junit.jupiter.api.Test;

public class PackFileSnapshotTest extends RepositoryTestCase {

	private static ObjectId unknownID = ObjectId
			.fromString("1234567890123456789012345678901234567890");

	@Test
	void testSamePackDifferentCompressionDetectChecksumChanged()
			throws Exception {
		Git git = Git.wrap(db);
		File f = writeTrashFile("file", "foobar ");
		for (int i = 0; i < 10; i++) {
			appendRandomLine(f);
			git.add().addFilepattern("file").call();
			git.commit().setMessage("message" + i).call();
		}

		FileBasedConfig c = db.getConfig();
		c.setInt(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTOPACKLIMIT, 1);
		c.save();
		Collection<Pack> packs = gc(Deflater.NO_COMPRESSION);
		assertEquals(1, packs.size(), "expected 1 packfile after gc");
		Pack p1 = packs.iterator().next();
		PackFileSnapshot snapshot = p1.getFileSnapshot();

		packs = gc(Deflater.BEST_COMPRESSION);
		assertEquals(1, packs.size(), "expected 1 packfile after gc");
		Pack p2 = packs.iterator().next();
		File pf = p2.getPackFile();

		// changing compression level with aggressive gc may change size,
		// fileKey (on *nix) and checksum. Hence FileSnapshot.isModified can
		// return true already based on size or fileKey.
		// So the only thing we can test here is that we ensure that checksum
		// also changed when we read it here in this test
		assertTrue(snapshot.isModified(pf),
				"expected snapshot to detect modified pack");
		assertTrue(snapshot.isChecksumChanged(pf), "expected checksum changed");
	}

	private void appendRandomLine(File f, int length, Random r)
			throws IOException {
		try (Writer w = Files.newBufferedWriter(f.toPath(),
				StandardOpenOption.APPEND)) {
			appendRandomLine(w, length, r);
		}
	}

	private void appendRandomLine(File f) throws IOException {
		appendRandomLine(f, 5, new Random());
	}

	private void appendRandomLine(Writer w, int len, Random r)
			throws IOException {
		final int c1 = 32; // ' '
		int c2 = 126; // '~'
		for (int i = 0; i < len; i++) {
			w.append((char) (c1 + r.nextInt(1 + c2 - c1)));
		}
	}

	private ObjectId createTestRepo(int testDataSeed, int testDataLength)
			throws IOException, GitAPIException, NoFilepatternException,
			NoHeadException, NoMessageException, UnmergedPathsException,
			ConcurrentRefUpdateException, WrongRepositoryStateException,
			AbortedByHookException {
		// Create a repo with two commits and one file. Each commit adds
		// testDataLength number of bytes. Data are random bytes. Since the
		// seed for the random number generator is specified we will get
		// the same set of bytes for every run and for every platform
		Random r = new Random(testDataSeed);
		Git git = Git.wrap(db);
		File f = writeTrashFile("file", "foobar ");
		appendRandomLine(f, testDataLength, r);
		git.add().addFilepattern("file").call();
		git.commit().setMessage("message1").call();
		appendRandomLine(f, testDataLength, r);
		git.add().addFilepattern("file").call();
		return git.commit().setMessage("message2").call().getId();
	}

	// Try repacking so fast that you get two new packs which differ only in
	// content/chksum but have same name, size and lastmodified.
	// Since this is done with standard gc (which creates new tmp files and
	// renames them) the filekeys of the new packfiles differ helping jgit
	// to detect the fast modification
	@Test
	void testDetectModificationAlthoughSameSizeAndModificationtime()
			throws Exception {
		int testDataSeed = 1;
		int testDataLength = 100;
		FileBasedConfig config = db.getConfig();
		// don't use mtime of the parent folder to detect pack file
		// modification.
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_TRUSTFOLDERSTAT, false);
		config.save();

		createTestRepo(testDataSeed, testDataLength);

		// repack to create initial packfile
		Pack p = repackAndCheck(5, null, null, null);
		Path packFilePath = p.getPackFile().toPath();
		AnyObjectId chk1 = p.getPackChecksum();
		String name = p.getPackName();
		Long length = Long.valueOf(p.getPackFile().length());
		FS fs = db.getFS();
		Instant m1 = fs.lastModifiedInstant(packFilePath);

		// Wait for a filesystem timer tick to enhance probability the rest of
		// this test is done before the filesystem timer ticks again.
		fsTick(packFilePath.toFile());

		// Repack to create packfile with same name, length. Lastmodified and
		// content and checksum are different since compression level differs
		AnyObjectId chk2 = repackAndCheck(6, name, length, chk1)
				.getPackChecksum();
		Instant m2 = fs.lastModifiedInstant(packFilePath);
		assumeFalse(m2.equals(m1));

		// Repack to create packfile with same name, length. Lastmodified is
		// equal to the previous one because we are in the same filesystem timer
		// slot. Content and its checksum are different
		AnyObjectId chk3 = repackAndCheck(7, name, length, chk2)
				.getPackChecksum();
		Instant m3 = fs.lastModifiedInstant(packFilePath);

		// ask for an unknown git object to force jgit to rescan the list of
		// available packs. If we would ask for a known objectid then JGit would
		// skip searching for new/modified packfiles
		db.getObjectDatabase().has(unknownID);
		assertEquals(chk3, getSinglePack(db.getObjectDatabase().getPacks())
				.getPackChecksum());
		assumeTrue(m3.equals(m2));
	}

	// Try repacking so fast that we get two new packs which differ only in
	// content and checksum but have same name, size and lastmodified.
	// To avoid that JGit detects modification by checking the filekey create
	// two new packfiles upfront and create copies of them. Then modify the
	// packfiles in-place by opening them for write and then copying the
	// content.
	@Test
	void testDetectModificationAlthoughSameSizeAndModificationtimeAndFileKey()
			throws Exception {
		int testDataSeed = 1;
		int testDataLength = 100;
		FileBasedConfig config = db.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_TRUSTFOLDERSTAT, false);
		config.save();

		createTestRepo(testDataSeed, testDataLength);

		// Repack to create initial packfile. Make a copy of it
		Pack p = repackAndCheck(5, null, null, null);
		Path packFilePath = p.getPackFile().toPath();
		Path fn = packFilePath.getFileName();
		assertNotNull(fn);
		String packFileName = fn.toString();
		Path packFileBasePath = packFilePath
				.resolveSibling(packFileName.replaceAll(".pack", ""));
		AnyObjectId chk1 = p.getPackChecksum();
		String name = p.getPackName();
		Long length = Long.valueOf(p.getPackFile().length());
		copyPack(packFileBasePath, "", ".copy1");

		// Repack to create second packfile. Make a copy of it
		AnyObjectId chk2 = repackAndCheck(6, name, length, chk1)
				.getPackChecksum();
		copyPack(packFileBasePath, "", ".copy2");

		// Repack to create third packfile
		AnyObjectId chk3 = repackAndCheck(7, name, length, chk2)
				.getPackChecksum();
		FS fs = db.getFS();
		Instant m3 = fs.lastModifiedInstant(packFilePath);
		db.getObjectDatabase().has(unknownID);
		assertEquals(chk3, getSinglePack(db.getObjectDatabase().getPacks())
				.getPackChecksum());

		// Wait for a filesystem timer tick to enhance probability the rest of
		// this test is done before the filesystem timer ticks.
		fsTick(packFilePath.toFile());

		// Copy copy2 to packfile data to force modification of packfile without
		// changing the packfile's filekey.
		copyPack(packFileBasePath, ".copy2", "");
		Instant m2 = fs.lastModifiedInstant(packFilePath);
		assumeFalse(m3.equals(m2));

		db.getObjectDatabase().has(unknownID);
		assertEquals(chk2, getSinglePack(db.getObjectDatabase().getPacks())
				.getPackChecksum());

		// Copy copy2 to packfile data to force modification of packfile without
		// changing the packfile's filekey.
		copyPack(packFileBasePath, ".copy1", "");
		Instant m1 = fs.lastModifiedInstant(packFilePath);
		assumeTrue(m2.equals(m1));
		db.getObjectDatabase().has(unknownID);
		assertEquals(chk1, getSinglePack(db.getObjectDatabase().getPacks())
				.getPackChecksum());
	}

	// Copy file from src to dst but avoid creating a new File (with new
	// FileKey) if dst already exists
	private Path copyFile(Path src, Path dst) throws IOException {
		if (Files.exists(dst)) {
			dst.toFile().setWritable(true);
			try (OutputStream dstOut = Files.newOutputStream(dst)) {
				Files.copy(src, dstOut);
				return dst;
			}
		}
		return Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
	}

	private Path copyPack(Path base, String srcSuffix, String dstSuffix)
			throws IOException {
		copyFile(Paths.get(base + ".idx" + srcSuffix),
				Paths.get(base + ".idx" + dstSuffix));
		copyFile(Paths.get(base + ".bitmap" + srcSuffix),
				Paths.get(base + ".bitmap" + dstSuffix));
		return copyFile(Paths.get(base + ".pack" + srcSuffix),
				Paths.get(base + ".pack" + dstSuffix));
	}

	private Pack repackAndCheck(int compressionLevel, String oldName,
			Long oldLength, AnyObjectId oldChkSum) throws Exception {
		Pack p = getSinglePack(gc(compressionLevel));
		File pf = p.getPackFile();
		// The following two assumptions should not cause the test to fail. If
		// on a certain platform we get packfiles (containing the same git
		// objects) where the lengths differ or the checksums don't differ we
		// just skip this test. A reason for that could be that compression
		// works differently or random number generator works differently. Then
		// we have to search for more consistent test data or checkin these
		// packfiles as test resources
		assumeTrue(oldLength == null || pf.length() == oldLength.longValue());
		assumeTrue(oldChkSum == null || !p.getPackChecksum().equals(oldChkSum));
		/*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @56f9473a)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @1d197565)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @3a412fd)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @557458f6)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @47719777)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @64b03ee6)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @5d8b8d27)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @27a8170d)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @5fd02a5c)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @4c2021d2)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @430b8a74)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @7a441948)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @284ef0a1)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @25589484)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @246f3e6e)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @1a696b7b)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @2e26d349)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @389149ea)
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
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @efcba8)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @2632fa66)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @2694d587)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @5583953)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @2ef9f032)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:76)
  org.openrewrite.java.testing.cleanup.AssertTrueEqualsToAssertEquals$1.visitMethodInvocation(AssertTrueEqualsToAssertEquals.java:51)
  org.openrewrite.java.tree.J$MethodInvocation.acceptJava(J.java:3470)
  org.openrewrite.java.tree.J.accept(J.java:60)
  org.openrewrite.TreeVisitor.visit(TreeVisitor.java:248)
  org.openrewrite.TreeVisitor.visitAndCast(TreeVisitor.java:327)
  org.openrewrite.java.JavaVisitor.visitRightPadded(JavaVisitor.java:1226)
  org.openrewrite.java.JavaVisitor.lambda$visitBlock$4(JavaVisitor.java:367)
  ...)~~>*//*~~(Recipe failed with an exception.
java.lang.ClassCastException: class org.openrewrite.java.tree.J$Binary cannot be cast to class org.openrewrite.java.tree.J$MethodInvocation (org.openrewrite.java.tree.J$Binary and org.openrewrite.java.tree.J$MethodInvocation are in unnamed module of loader org.codehaus.plexus.classworlds.realm.ClassRealm @25fbe503)
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
  ...)~~>*/assertTrue(oldName == null || p.getPackName().equals(oldName));
		return p;
	}

	private Pack getSinglePack(Collection<Pack> packs) {
		Iterator<Pack> pIt = packs.iterator();
		Pack p = pIt.next();
		assertFalse(pIt.hasNext());
		return p;
	}

	private Collection<Pack> gc(int compressionLevel) throws Exception {
		GC gc = new GC(db);
		PackConfig pc = new PackConfig(db.getConfig());
		pc.setCompressionLevel(compressionLevel);

		pc.setSinglePack(true);

		// --aggressive
		pc.setDeltaSearchWindowSize(
				GarbageCollectCommand.DEFAULT_GC_AGGRESSIVE_WINDOW);
		pc.setMaxDeltaDepth(GarbageCollectCommand.DEFAULT_GC_AGGRESSIVE_DEPTH);
		pc.setReuseObjects(false);

		gc.setPackConfig(pc);
		gc.setExpireAgeMillis(0);
		gc.setPackExpireAgeMillis(0);
		return gc.gc().get();
	}

}

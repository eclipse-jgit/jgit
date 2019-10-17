/*
 * Copyright (C) 2019, Matthias Sohn <matthias.sohn@sap.com>
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
package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
//import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;
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
import org.junit.Test;

public class PackFileSnapshotTest extends RepositoryTestCase {

	private static ObjectId unknownID = ObjectId
			.fromString("1234567890123456789012345678901234567890");

	@Test
	public void testSamePackDifferentCompressionDetectChecksumChanged()
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
		Collection<PackFile> packs = gc(Deflater.NO_COMPRESSION);
		assertEquals("expected 1 packfile after gc", 1, packs.size());
		PackFile p1 = packs.iterator().next();
		PackFileSnapshot snapshot = p1.getFileSnapshot();

		packs = gc(Deflater.BEST_COMPRESSION);
		assertEquals("expected 1 packfile after gc", 1, packs.size());
		PackFile p2 = packs.iterator().next();
		File pf = p2.getPackFile();

		// changing compression level with aggressive gc may change size,
		// fileKey (on *nix) and checksum. Hence FileSnapshot.isModified can
		// return true already based on size or fileKey.
		// So the only thing we can test here is that we ensure that checksum
		// also changed when we read it here in this test
		assertTrue("expected snapshot to detect modified pack",
				snapshot.isModified(pf));
		assertTrue("expected checksum changed", snapshot.isChecksumChanged(pf));
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
	public void testDetectModificationAlthoughSameSizeAndModificationtime()
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
		PackFile pf = repackAndCheck(5, null, null, null);
		Path packFilePath = pf.getPackFile().toPath();
		AnyObjectId chk1 = pf.getPackChecksum();
		String name = pf.getPackName();
		Long length = Long.valueOf(pf.getPackFile().length());
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
	public void testDetectModificationAlthoughSameSizeAndModificationtimeAndFileKey()
			throws Exception {
		int testDataSeed = 1;
		int testDataLength = 100;
		FileBasedConfig config = db.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_TRUSTFOLDERSTAT, false);
		config.save();

		createTestRepo(testDataSeed, testDataLength);

		// Repack to create initial packfile. Make a copy of it
		PackFile pf = repackAndCheck(5, null, null, null);
		Path packFilePath = pf.getPackFile().toPath();
		Path packFileBasePath = packFilePath.resolveSibling(
				packFilePath.getFileName().toString().replaceAll(".pack", ""));
		AnyObjectId chk1 = pf.getPackChecksum();
		String name = pf.getPackName();
		Long length = Long.valueOf(pf.getPackFile().length());
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

	private PackFile repackAndCheck(int compressionLevel, String oldName,
			Long oldLength, AnyObjectId oldChkSum)
			throws IOException, ParseException {
		PackFile p = getSinglePack(gc(compressionLevel));
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
		assertTrue(oldName == null || p.getPackName().equals(oldName));
		return p;
	}

	private PackFile getSinglePack(Collection<PackFile> packs) {
		Iterator<PackFile> pIt = packs.iterator();
		PackFile p = pIt.next();
		assertFalse(pIt.hasNext());
		return p;
	}

	private Collection<PackFile> gc(int compressionLevel)
			throws IOException, ParseException {
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
		return gc.gc();
	}

}

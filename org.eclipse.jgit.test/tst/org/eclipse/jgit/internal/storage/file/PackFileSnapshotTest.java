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
import java.nio.file.attribute.BasicFileAttributes;
import java.text.ParseException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Random;
import java.util.zip.Deflater;

import org.eclipse.jgit.api.GarbageCollectCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

public class PackFileSnapshotTest extends RepositoryTestCase {

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
		final int a = 32; // 'a'
		int e = 126; // 'e'
		for (int i = 0; i < len; i++) {
			w.append((char) (a + r.nextInt(1 + e - a)));
		}
	}

	// Try repacking so fast that you get two new packs which differ only in
	// content/chksum but have same name, size and lastmodified.
	// Since this is done with standard gc (which creates new tmp files and
	// renames them) the filekeys of the new packfiles differ helping jgit
	// to detect the fast modifications
	@Test
	public void testDetetctModificationAlthoughtSameSizeAndModificationtime()
			throws Exception {
		int testDataSeed = 1;
		int testDataLength = 100;
		FileBasedConfig config = db.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_TRUSTFOLDERSTAT, true);
		config.save();

		Random r = new Random(testDataSeed);
		// Create a repo with two commits and one file which has content of
		// first 100bytes and then 200bytes
		Git git = Git.wrap(db);
		File f = writeTrashFile("file", "foobar ");
		appendRandomLine(f, testDataLength, r);
		git.add().addFilepattern("file").call();
		ObjectId commitId = git.commit().setMessage("message1").call().getId();
		appendRandomLine(f, testDataLength, r);
		git.add().addFilepattern("file").call();
		git.commit().setMessage("message2").call().getId();

		// Pack to create initial packfile
		PackFile pf = repackAndCheck(5, null, null, null);
		Path packFilePath = pf.getPackFile().toPath();
		AnyObjectId chk1 = pf.getPackChecksum();
		String name = pf.getPackName();
		Long length = Long.valueOf(pf.getPackFile().length());
		long m1 = packFilePath.toFile().lastModified();

		// Wait for a filesystem timer tick to enhance probability the rest of
		// this test is done before the filesystem timer ticks again.
		long timerResolution = FS.getFsTimerResolution(packFilePath).toMillis();
		waitForTimerTick(packFilePath, timerResolution);

		// Repack to create packfile with same name, length. Lastmodified and
		// content/chksum are different
		AnyObjectId chk2 = repackAndCheck(6, name, length, chk1)
				.getPackChecksum();
		long m2 = packFilePath.toFile().lastModified();
		assumeFalse(m2 == m1);

		// Repack to create packfile with same name, length. Lastmodified is
		// equal to previous because we are in the same filesystemtimer slot.
		// Content/chksum is different
		AnyObjectId chk3 = repackAndCheck(7, name, length, chk2)
				.getPackChecksum();
		long m3 = packFilePath.toFile().lastModified();
		db.getObjectDatabase().open(commitId);
		assertEquals(chk3, getSinglePack(db.getObjectDatabase().getPacks())
				.getPackChecksum());
		assumeTrue(m3 == m2);
	}

	// Try modifying packfiles so fast that you get two new packs which differ
	// only in content/chksum but have same name, size and lastmodified.
	// Since the two new packfiles are created upfront and then the content is
	// copied into existing packfiles even the filekey will stay the same. Still
	// JGit should detect packfile modification by racyGit checks.

	// This test fails - have not found out why
	@Test
	public void testDetetctModificationAlthoughtSameSizeAndModificationtimeAndFileKey()
			throws Exception {
		int testDataSeed = 1;
		int testDataLength = 100;
		FileBasedConfig config = db.getConfig();
		config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_TRUSTFOLDERSTAT, true);
		config.save();

		Random r = new Random(testDataSeed);
		// Create a repo with two commits and one file which has content of
		// first 100bytes and then 200bytes
		Git git = Git.wrap(db);
		File f = writeTrashFile("file", "foobar ");
		appendRandomLine(f, testDataLength, r);
		git.add().addFilepattern("file").call();
		ObjectId commitId = git.commit().setMessage("message1").call().getId();
		appendRandomLine(f, testDataLength, r);
		git.add().addFilepattern("file").call();
		git.commit().setMessage("message2").call().getId();

		// Pack to create initial packfile. Make a copy of it
		PackFile pf = repackAndCheck(5, null, null, null);
		Path packFilePath = pf.getPackFile().toPath();
		Path packFileBasePath = packFilePath.resolveSibling(
				packFilePath.getFileName().toString().replaceAll(".pack", ""));
		printFilesMetaData(packFilePath);
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
		long m3 = packFilePath.toFile().lastModified();
		db.getObjectDatabase().open(commitId);
		assertEquals(chk3, getSinglePack(db.getObjectDatabase().getPacks())
				.getPackChecksum());

		// Wait for a filesystem timer tick to enhance probability the rest of
		// this test is done before the filesystem timer ticks.
		long timerResolution = FS.getFsTimerResolution(packFilePath).toMillis();
		waitForTimerTick(packFilePath, timerResolution);

		// Copy copy2 to packfile data to force modification of packfile without
		// filekey change.
		copyPack(packFileBasePath, ".copy2", "");
		long m2 = packFilePath.toFile().lastModified();
		assumeFalse(m3 == m2);
		db.getObjectDatabase().open(commitId);
		assertEquals(chk2, getSinglePack(db.getObjectDatabase().getPacks())
				.getPackChecksum());

		// Copy copy2 to packfile data to force modification of packfile without
		// filekey change.
		copyPack(packFileBasePath, ".copy1", "");
		long m1 = packFilePath.toFile().lastModified();
		assumeTrue(m2 == m1);
		db.getObjectDatabase().open(commitId);
		assertEquals(chk1, getSinglePack(db.getObjectDatabase().getPacks())
				.getPackChecksum());
	}

	// Copy file from src to dst but avoid creating a new File (with new
	// FileKey) when dst already exists
	private Path copyFile(Path src, Path dst) throws IOException {
		if (Files.exists(dst)) {
			dst.toFile().setWritable(true);
			try (OutputStream dstOut = Files.newOutputStream(dst)) {
				Files.copy(src, dstOut);
				return dst;
			}
		} else {
			return Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
		}
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
		// on a certain platform we get packfiles where the lengths differ or
		// the checksums don't differ we just skip this test. A reason for that
		// could be that compression works differently or random number
		// generator works different. Then we have to search for more consistent
		// test data or checkin these packfiles as test resources
		assumeTrue(oldLength == null || pf.length() == oldLength.longValue());
		assumeTrue(oldChkSum == null || !p.getPackChecksum().equals(oldChkSum));
		assertTrue(oldName == null || p.getPackName().equals(oldName));
		return p;
	}

	private void printFilesMetaData(Path... paths) throws IOException {
		for (Path p : paths) {
			System.out.println(describe(p));
		}
	}

	// Sleep until the filesystem timer has ticked
	private void waitForTimerTick(Path p, long timerResolution)
			throws IOException, InterruptedException {
		long wakeup = Files.getLastModifiedTime(p).toMillis() + timerResolution;
		Thread.sleep(10 + wakeup - System.currentTimeMillis());
	}

	private String describe(Path p) throws IOException {
		BasicFileAttributes attrs = Files.readAttributes(p,
				BasicFileAttributes.class);
		return "name=" + p.getFileName() + ", size=" + attrs.size()
				+ ", fileKey=" + attrs.fileKey() + ", lastModified="
				+ attrs.lastModifiedTime().toMillis();
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

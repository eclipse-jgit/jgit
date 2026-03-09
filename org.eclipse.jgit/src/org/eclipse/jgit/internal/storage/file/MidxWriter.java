/*
 * Copyright (C) 2026, Google LLC
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jgit.internal.revwalk.RefAdvancerWalk;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndexLoader;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndexWriter;
import org.eclipse.jgit.internal.storage.midx.PackIndexMerger;
import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.internal.storage.pack.PackBitmapCalculator;
import org.eclipse.jgit.internal.storage.pack.PackBitmapIndexWriter;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.pack.PackConfig;

/**
 * Helper to write multipack indexes.
 */
public class MidxWriter {

	/**
	 * Write a mdix over the packs
	 *
	 * @param pm
	 *            a progress monitor
	 * @param packs
	 *            packs to cover with the midx
	 * @param midxOut
	 *            file to write the resulting midx
	 * @throws IOException
	 *             an error reading any of the input packs or indexes
	 */
	public static void writeMidx(ProgressMonitor pm, Collection<Pack> packs,
			File midxOut) throws IOException {
		PackIndexMerger.Builder builder = PackIndexMerger.builder();
		builder.setProgressMonitor(pm);

		Collection<Pack> packList = packs.stream()
				.sorted(Comparator.comparing(Pack::getPackName)).toList();
		pm.beginTask("Adding packs to midx", packList.size());
		for (Pack pack : packList) {
			PackFile packFile = pack.getPackFile().create(PackExt.INDEX);
			builder.addPack(packFile.getName(), pack.getIndex());
			pm.update(1);
		}
		pm.endTask();

		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		try (FileOutputStream out = new FileOutputStream(
				midxOut.getAbsolutePath())) {
			writer.write(pm, out, builder.build());
		}
	}

	public static void createAndAttachBitmaps(ProgressMonitor pm, Repository db,
			File midxFile, PackConfig packConfig) throws IOException {
		// TODO(ifrade): Verify we duplicate the behaviour about tags of regular
		// bitmapping
		List<ObjectId> allHeads = db.getRefDatabase()
				.getRefsByPrefix(Constants.R_HEADS).stream()
				.map(r -> r.getObjectId()).filter(Objects::nonNull).toList();
		if (allHeads.isEmpty()) {
			return;
		}

		MultiPackIndex midx = MultiPackIndexLoader.open(midxFile);
		RefAdvancerWalk adv = new RefAdvancerWalk(db, c -> midx.hasObject(c));
		Set<RevCommit> inPack = adv.advance(allHeads);

		ObjectDirectory objdb = (ObjectDirectory) db.getObjectDatabase();
		try (WindowCursor ctx = (WindowCursor) objdb.newReader()) {
			byte[] checksum = midx.getChecksum();
			PackBitmapIndexBuilder writeBitmaps = new PackBitmapIndexBuilder(
					getLocalObjects(pm, ctx, objdb.getPacks(), midx));
			int commitCount = writeBitmaps.getCommits().cardinality();

			PackBitmapCalculator calculator = new PackBitmapCalculator(
					packConfig);
			// This will do ctx.getBitmapIndex() to reuse/copy previous bitmaps
			calculator.calculate(ctx, pm, commitCount, inPack, new HashSet<>(),
					writeBitmaps);
			File bitmapFile = new File(midxFile.getAbsolutePath() + ".bitmap");
			try (FileOutputStream out = new FileOutputStream(
					bitmapFile.getAbsolutePath())) {
				PackBitmapIndexWriter pbiWriter = new PackBitmapIndexWriterV1(
						out);
				pbiWriter.write(writeBitmaps, checksum);
			}
		}
	}

	private static List<ObjectToPack> getLocalObjects(ProgressMonitor pm,
			WindowCursor ctx, Collection<Pack> packs, MultiPackIndex midx)
			throws IOException {
		Pack[] realPacks = new Pack[midx.getPackNames().length];
		String[] packNames = midx.getPackNames();
		for (int i = 0; i < packNames.length; i++) {
			String packName = packNames[i];
			// c-git uses .idx for the packnames
			Pack pack = packs.stream()
					// .peek(p -> System.out.println(p.getPackFile().getName()))
					.filter(p -> p.getPackFile().create(PackExt.INDEX).getName()
							.equals(packName))
					.findFirst().orElseThrow();
			realPacks[i] = pack;
		}

		long[] accPackSizes = new long[realPacks.length];
		accPackSizes[0] = 0;
		for (int i = 1; i < accPackSizes.length; i++) {
			long prevValue = accPackSizes[i - 1];
			PackFile packFile = realPacks[i - 1].getPackFile()
					.create(PackExt.PACK);
			accPackSizes[i] = packFile.length() + prevValue;
		}

		System.out.println(Arrays.toString(accPackSizes));
		pm.beginTask("Calculating objects to pack for midx",
				midx.getObjectCount());
		List<ObjectToPack> otps = new ArrayList<>(midx.getObjectCount());
		for (MultiPackIndex.MidxIterator it = midx.iterator(); it.hasNext();) {
			MultiPackIndex.MutableEntry e = it.next();
			int objType = realPacks[e.getPackId()].getObjectType(ctx,
					e.getOffset());
			ObjectToPack otp = new ObjectToPack(e.getObjectId(), objType);
			otp.setOffset(accPackSizes[e.getPackId()] + e.getOffset());
			otps.add(otp);
			pm.update(1);
		}
		pm.endTask();
		return otps;
	}
}

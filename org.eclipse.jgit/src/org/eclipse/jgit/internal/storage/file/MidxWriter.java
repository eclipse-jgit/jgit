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

import static org.eclipse.jgit.internal.storage.pack.PackExt.BITMAP_INDEX;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jgit.internal.revwalk.RefAdvancerWalk;
import org.eclipse.jgit.internal.storage.midx.MidxMetadataReader;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndexWriter;
import org.eclipse.jgit.internal.storage.midx.PackIndexMerger;
import org.eclipse.jgit.internal.storage.pack.ObjectToPack;
import org.eclipse.jgit.internal.storage.pack.PackBitmapCalculator;
import org.eclipse.jgit.internal.storage.pack.PackBitmapIndexWriter;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.util.Base64;
import org.eclipse.jgit.util.FileUtils;

/**
 * Helper to write multipack indexes.
 */
public class MidxWriter {

	/**
	 * Do not build a midx if there are less than this amount of packs to cover.
	 */
	private static final int MIN_PACKS_FOR_MIDX = 2;

	/**
	 * Write a mdix over the packs
	 *
	 * @param pm
	 *            a progress monitor
	 * @param repo
	 *            the repository
	 * @param packs
	 *            packs to cover with the midx
	 * @param midxOut
	 *            file to write the resulting midx
	 * @param packConfig
	 *            config for the bitmap writing. Null to skip writing bitmaps.
	 * @throws IOException
	 *             an error reading any of the input packs or indexes
	 */
	public static void writeMidx(ProgressMonitor pm, Repository repo,
			Collection<Pack> packs, File midxOut, PackConfig packConfig)
			throws IOException {

		Collection<Pack> packList = flattenMidxPackList(packs).stream()
				.sorted(Comparator.comparing(Pack::getPackName)).toList();
		if (packList.size() < MIN_PACKS_FOR_MIDX) {
			return;
		}
		PackIndexMerger.Builder builder = PackIndexMerger.builder();
		builder.setProgressMonitor(pm);
		pm.beginTask("Adding packs to midx", packList.size()); //$NON-NLS-1$
		for (Pack pack : packList) {
			if (pack instanceof PackMidx) {
				throw new IllegalArgumentException(
						"Building midx from other midx not supported yet");
			}
			PackFile packFile = pack.getPackFile().create(PackExt.INDEX);
			builder.addPack(packFile.getName(), pack.getIndex());

			pm.update(1);
		}
		PackIndexMerger data = builder.build();
		pm.endTask();

		File oldMidxBitmaps = null;
		if (midxOut.exists()) {
			MidxMetadataReader.MidxMetadata midxMetadata = MidxMetadataReader
					.read(midxOut);
			byte[] checksum = midxMetadata.checksum();
			String midxBitmapsPath = midxOut.getAbsoluteFile() + "-" //$NON-NLS-1$
					+ ObjectId.fromRaw(checksum).name() + "." //$NON-NLS-1$
					+ BITMAP_INDEX.getExtension();
			File previousBitmaps = new File(midxBitmapsPath);
			if (previousBitmaps.exists()) {
				oldMidxBitmaps = previousBitmaps;
			}
		}

		String midxFilename = midxOut.getAbsolutePath();
		File midxOutTmp = new File(
				midxFilename + BITMAP_INDEX.getTmpExtension());
		MultiPackIndexWriter writer = new MultiPackIndexWriter();
		MultiPackIndexWriter.Result result;
		try (FileOutputStream out = new FileOutputStream(
				midxOutTmp.getAbsolutePath())) {
			result = writer.write(pm, out, data);
		}

		File midxOutBitmaps = new File(midxOut.getAbsoluteFile() + "-" //$NON-NLS-1$
				+ ObjectId.fromRaw(Base64.decode(result.checksum())).name()
				+ "." + BITMAP_INDEX.getExtension());
		File midxOutBitmapsTmp = null;
		if (packConfig != null) {
			midxOutBitmapsTmp = new File(midxOut.getAbsolutePath() + "-" //$NON-NLS-1$
					+ ObjectId.fromRaw(Base64.decode(result.checksum())).name()
					+ BITMAP_INDEX.getTmpExtension());
			createAndAttachBitmaps(pm, repo, midxOutBitmapsTmp,
					Base64.decode(result.checksum()), data, packList,
					new PackConfig(repo));
		}

		FileUtils.rename(midxOutTmp.getAbsoluteFile(),
				midxOut.getAbsoluteFile(), StandardCopyOption.ATOMIC_MOVE);
		if (midxOutBitmapsTmp != null) {
			FileUtils.rename(midxOutBitmapsTmp.getAbsoluteFile(),
					midxOutBitmaps.getAbsoluteFile(),
					StandardCopyOption.ATOMIC_MOVE);
		}

		if (oldMidxBitmaps != null && !oldMidxBitmaps.equals(midxOutBitmaps)) {
			FileUtils.delete(oldMidxBitmaps);
		}
	}

	private static void createAndAttachBitmaps(ProgressMonitor pm,
			Repository repo, File midxBitmapsOut, byte[] checksum,
			PackIndexMerger data, Collection<Pack> packs, PackConfig cfg)
			throws IOException {

		// TODO(ifrade): Verify we duplicate the behaviour about tags of regular
		// bitmapping
		List<ObjectId> allHeads = repo.getRefDatabase()
				.getRefsByPrefix(Constants.R_HEADS).stream()
				.map(r -> r.getObjectId()).filter(Objects::nonNull).toList();
		if (allHeads.isEmpty()) {
			return;
		}

		ObjectIdOwnerMap<ObjectToPack> byId = new ObjectIdOwnerMap<>();
		List<ObjectToPack> otps = asObjectsToPack(pm,
				(WindowCursor) repo.newObjectReader(), data,
				new ArrayList<>(packs), byId);

		RefAdvancerWalk adv = new RefAdvancerWalk(repo, c -> byId.contains(c));
		Set<RevCommit> inPack = adv.advance(allHeads);

		PackBitmapIndexBuilder writeBitmaps = new PackBitmapIndexBuilder(otps);
		int commitCount = writeBitmaps.getCommits().cardinality();

		PackBitmapCalculator calculator = new PackBitmapCalculator(cfg);
		// This will do ctx.getBitmapIndex() to reuse/copy previous bitmaps
		calculator.calculate(repo.newObjectReader(),
				NullProgressMonitor.INSTANCE, commitCount, inPack,
				new HashSet<>(), writeBitmaps);
		try (FileOutputStream out = new FileOutputStream(
				midxBitmapsOut.getAbsolutePath())) {
			PackBitmapIndexWriter pbiWriter = new PackBitmapIndexWriterV1(out);
			pbiWriter.write(writeBitmaps, checksum);
		}
	}

	private static List<ObjectToPack> asObjectsToPack(ProgressMonitor pm,
			WindowCursor ctx, PackIndexMerger data, List<Pack> packs,
			ObjectIdOwnerMap<ObjectToPack> byId) throws IOException {
		long[] accPackSize = new long[packs.size()];
		for (int i = 1; i < packs.size(); i++) {
			long prevValue = accPackSize[i - 1];
			accPackSize[i] = prevValue
					+ packs.get(i - 1).getPackFile().length();
		}

		pm.beginTask("Converting midx to ObjectsToPack", //$NON-NLS-1$
				data.getUniqueObjectCount());
		List<ObjectToPack> result = new ArrayList<>(
				data.getUniqueObjectCount());
		MultiPackIndex.MidxIterator it = data.bySha1Iterator();
		while (it.hasNext()) {
			MultiPackIndex.MutableEntry entry = it.next();
			int objectType = packs.get(entry.getPackId()).getObjectType(ctx,
					entry.getOffset());
			ObjectToPack o = new ObjectToPack(entry.getObjectId().toObjectId(),
					objectType);
			o.setOffset(accPackSize[entry.getPackId()] + entry.getOffset());
			result.add(o);
			byId.add(o);
			pm.update(1);
		}
		pm.endTask();
		return result;
	}

	static List<Pack> flattenMidxPackList(Collection<Pack> packs) {
		List<Pack> output = new ArrayList<>();
		for (Pack p : packs) {
			List<Pack> coveredPacks = new ArrayList<>(p.getCoveredPacks());
			if (coveredPacks.isEmpty()) {
				output.add(p);
			} else {
				Collections.reverse(coveredPacks);
				output.addAll(coveredPacks);
			}
		}
		return Collections.unmodifiableList(output);
	}
}

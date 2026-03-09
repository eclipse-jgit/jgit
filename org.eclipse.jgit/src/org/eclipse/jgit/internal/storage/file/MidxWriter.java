package org.eclipse.jgit.internal.storage.file;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;

import org.eclipse.jgit.internal.storage.midx.MultiPackIndexWriter;
import org.eclipse.jgit.internal.storage.midx.PackIndexMerger;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.lib.ProgressMonitor;

/**
 * Helper to write multipack indexes.
 */
public class MidxWriter {

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
}

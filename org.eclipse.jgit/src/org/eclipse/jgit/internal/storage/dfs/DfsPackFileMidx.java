package org.eclipse.jgit.internal.storage.dfs;

import java.io.IOException;
import java.util.List;
import java.util.zip.DataFormatException;

import org.apache.commons.lang3.NotImplementedException;
import org.eclipse.jgit.errors.StoredObjectRepresentationNotAvailableException;
import org.eclipse.jgit.internal.storage.file.PackReverseIndex;
import org.eclipse.jgit.internal.storage.midx.MultiPackIndex;
import org.eclipse.jgit.internal.storage.pack.PackOutputStream;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectLoader;

class DfsPackFileMidx extends DfsPackFile {
	private final List<DfsPackFile> packs;

	private final DfsMidx dfsMidx;

	private MultiPackIndex midx;

	DfsPackFileMidx(DfsBlockCache cache, DfsMidx dfsMidx,
			List<DfsPackFile> packs) {
		super(cache, dfsMidx.getPackDescription());
		this.dfsMidx = dfsMidx;
		this.packs = packs;
	}

	private MultiPackIndex getMidx(DfsReader ctx) throws IOException {
		if (midx != null) {
			return midx;
		}

		midx = dfsMidx.getMultiPackIndex(ctx);
		return midx;
	}

	@Override
	long getObjectCount(DfsReader ctx) throws IOException {
		return getMidx(ctx).getObjectCount();
	}

	@Override
	void copyPackAsIs(PackOutputStream out, DfsReader ctx) throws IOException {
		throw new NotImplementedException();
	}

	@Override
	void copyAsIs(PackOutputStream out, DfsObjectToPack src, boolean validate,
			DfsReader ctx) throws IOException,
			StoredObjectRepresentationNotAvailableException {
		throw new NotImplementedException();
	}

	@Override
	ObjectLoader load(DfsReader ctx, long pos) throws IOException {

		return
	}

	@Override
	byte[] getDeltaHeader(DfsReader wc, long pos)
			throws IOException, DataFormatException {
		return super.getDeltaHeader(wc, pos);
	}

	@Override
	int getObjectType(DfsReader ctx, long pos) throws IOException {
		return super.getObjectType(ctx, pos);
	}

	@Override
	long getObjectSize(DfsReader ctx, AnyObjectId id) throws IOException {
		return super.getObjectSize(ctx, id);
	}

	@Override
	long getObjectSize(DfsReader ctx, long pos) throws IOException {
		return super.getObjectSize(ctx, pos);
	}

	@Override
	boolean hasObjectSizeIndex(DfsReader ctx) throws IOException {
		return super.hasObjectSizeIndex(ctx);
	}

	@Override
	int getObjectSizeIndexThreshold(DfsReader ctx) throws IOException {
		return super.getObjectSizeIndexThreshold(ctx);
	}

	@Override
	long getIndexedObjectSize(DfsReader ctx, AnyObjectId id)
			throws IOException {
		return super.getIndexedObjectSize(ctx, id);
	}

	@Override
	void representation(DfsObjectRepresentation r, long pos, DfsReader ctx)
			throws IOException {
		super.representation(r, pos, ctx);
	}

	@Override
	void representation(DfsObjectRepresentation r, long pos, DfsReader ctx,
			PackReverseIndex rev) throws IOException {
		super.representation(r, pos, ctx, rev);
	}

	@Override
	boolean isCorrupt(long offset) {
		return super.isCorrupt(offset);
	}
}

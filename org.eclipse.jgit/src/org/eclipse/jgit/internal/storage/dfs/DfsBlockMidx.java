package org.eclipse.jgit.internal.storage.dfs;

import org.eclipse.jgit.internal.storage.pack.PackOutputStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * DfsBlock where offsets come from the multipack index
 * <p>
 * This is the DfsBlock returned by the multipack index. It wraps the block of
 * the real file translating offsets from midx to pack.
 */
final class DfsBlockMidx extends DfsBlock {
	private final DfsBlock src;

	private final long midxPackStart;

	/**
	 * Map midx offsets to a real pack block
	 * 
	 * @param packBlock
	 *            the real pack block
	 * @param midxPackStart
	 *            midx offset where the pack (of this block) starts
	 */
	DfsBlockMidx(DfsBlock packBlock, long midxPackStart) {
		// Use start/end/key from the pack block
		super(packBlock);
		this.src = packBlock;
		this.midxPackStart = midxPackStart;
	}

	@Override
	int size() {
		return src.size();
	}

	@Override
	ByteBuffer zeroCopyByteBuffer(int n) {
		return src.zeroCopyByteBuffer(n);
	}

	@Override
	boolean contains(DfsStreamKey want, long pos) {
		return src.contains(want, pos - midxPackStart);
	}

	@Override
	int copy(long pos, byte[] dstbuf, int dstoff, int cnt) {
		return src.copy(pos - midxPackStart, dstbuf, dstoff, cnt);
	}

	@Override
	int setInput(long pos, Inflater inf) throws DataFormatException {
		return src.setInput(pos - midxPackStart, inf);
	}

	@Override
	void crc32(CRC32 out, long pos, int cnt) {
		src.crc32(out, pos - midxPackStart, cnt);
	}

	@Override
	void write(PackOutputStream out, long pos, int cnt) throws IOException {
		src.write(out, pos - midxPackStart, cnt);
	}

	@Override
	void check(Inflater inf, byte[] tmp, long pos, int cnt)
			throws DataFormatException {
		src.check(inf, tmp, pos - midxPackStart, cnt);
	}
}

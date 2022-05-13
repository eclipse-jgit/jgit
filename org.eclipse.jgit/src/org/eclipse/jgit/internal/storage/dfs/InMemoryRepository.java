package org.eclipse.jgit.internal.storage.dfs;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackSource;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.internal.storage.reftable.ReftableConfig;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefDatabase;

/**
 * Git repository stored entirely in the local process memory.
 * <p>
 * This implementation builds on the DFS repository by storing all reference and
 * object data in the local process. It is not very efficient and exists only
 * for unit testing and small experiments.
 * <p>
 * The repository is thread-safe. Memory used is released only when this object
 * is garbage collected. Closing the repository has no impact on its memory.
 */
public class InMemoryRepository extends DfsRepository {
	/** Builder for in-memory repositories. */
	public static class Builder
			extends DfsRepositoryBuilder<Builder, InMemoryRepository> {
		@Override
		public InMemoryRepository build() throws IOException {
			return new InMemoryRepository(this);
		}
	}

	static final AtomicInteger packId = new AtomicInteger();

	private final MemObjDatabase objdb;
	private final MemRefDatabase refdb;
	private String gitwebDescription;

	/**
	 * Initialize a new in-memory repository.
	 *
	 * @param repoDesc
	 *            description of the repository.
	 */
	public InMemoryRepository(DfsRepositoryDescription repoDesc) {
		this(new Builder().setRepositoryDescription(repoDesc));
	}

	InMemoryRepository(Builder builder) {
		super(builder);
		objdb = new MemObjDatabase(this);
		refdb = new MemRefDatabase();
	}

	/** {@inheritDoc} */
	@Override
	public MemObjDatabase getObjectDatabase() {
		return objdb;
	}

	/** {@inheritDoc} */
	@Override
	public RefDatabase getRefDatabase() {
		return refdb;
	}

	/**
	 * Enable (or disable) the atomic reference transaction support.
	 * <p>
	 * Useful for testing atomic support enabled or disabled.
	 *
	 * @param atomic
	 *            whether to use atomic reference transaction support
	 */
	public void setPerformsAtomicTransactions(boolean atomic) {
		refdb.performsAtomicTransactions = atomic;
	}

	/** {@inheritDoc} */
	@Override
	@Nullable
	public String getGitwebDescription() {
		return gitwebDescription;
	}

	/** {@inheritDoc} */
	@Override
	public void setGitwebDescription(@Nullable String d) {
		gitwebDescription = d;
	}

	/** DfsObjDatabase used by InMemoryRepository. */
	public static class MemObjDatabase extends DfsObjDatabase {
		private List<DfsPackDescription> packs = new ArrayList<>();
		private int blockSize;
		private Set<ObjectId> shallowCommits = Collections.emptySet();

		MemObjDatabase(DfsRepository repo) {
			super(repo, new DfsReaderOptions());
		}

		/**
		 * @param blockSize
		 *            force a different block size for testing.
		 */
		public void setReadableChannelBlockSizeForTest(int blockSize) {
			this.blockSize = blockSize;
		}

		@Override
		protected synchronized List<DfsPackDescription> listPacks() {
			return packs;
		}

		@Override
		protected DfsPackDescription newPack(PackSource source) {
			int id = packId.incrementAndGet();
			return new MemPack(
					"pack-" + id + "-" + source.name(), //$NON-NLS-1$ //$NON-NLS-2$
					getRepository().getDescription(),
					source);
		}

		@Override
		protected synchronized void commitPackImpl(
				Collection<DfsPackDescription> desc,
				Collection<DfsPackDescription> replace) {
			List<DfsPackDescription> n;
			n = new ArrayList<>(desc.size() + packs.size());
			n.addAll(desc);
			n.addAll(packs);
			if (replace != null)
				n.removeAll(replace);
			packs = n;
			clearCache();
		}

		@Override
		protected void rollbackPack(Collection<DfsPackDescription> desc) {
			// Do nothing. Pack is not recorded until commitPack.
		}

		@Override
		protected ReadableChannel openFile(DfsPackDescription desc, PackExt ext)
				throws FileNotFoundException, IOException {
			MemPack memPack = (MemPack) desc;
			byte[] file = memPack.get(ext);
			if (file == null)
				throw new FileNotFoundException(desc.getFileName(ext));
			return new ByteArrayReadableChannel(file, blockSize);
		}

		@Override
		protected DfsOutputStream writeFile(DfsPackDescription desc,
				PackExt ext) throws IOException {
			MemPack memPack = (MemPack) desc;
			return new Out() {
				@Override
				public void flush() {
					memPack.put(ext, getData());
				}
			};
		}

		@Override
		public Set<ObjectId> getShallowCommits() throws IOException {
			return shallowCommits;
		}

		@Override
		public void setShallowCommits(Set<ObjectId> shallowCommits) {
			this.shallowCommits = shallowCommits;
		}

		@Override
		public long getApproximateObjectCount() {
			long count = 0;
			for (DfsPackDescription p : packs) {
				count += p.getObjectCount();
			}
			return count;
		}
	}

	private static class MemPack extends DfsPackDescription {
		final byte[][] fileMap = new byte[PackExt.values().length][];

		MemPack(String name, DfsRepositoryDescription repoDesc, PackSource source) {
			super(repoDesc, name, source);
		}

		void put(PackExt ext, byte[] data) {
			fileMap[ext.getPosition()] = data;
		}

		byte[] get(PackExt ext) {
			return fileMap[ext.getPosition()];
		}
	}

	private abstract static class Out extends DfsOutputStream {
		private final ByteArrayOutputStream dst = new ByteArrayOutputStream();
		private byte[] data;

		@Override
		public void write(byte[] buf, int off, int len) {
			data = null;
			dst.write(buf, off, len);
		}

		@Override
		public int read(long position, ByteBuffer buf) {
			byte[] d = getData();
			int n = Math.min(buf.remaining(), d.length - (int) position);
			if (n == 0)
				return -1;
			buf.put(d, (int) position, n);
			return n;
		}

		byte[] getData() {
			if (data == null)
				data = dst.toByteArray();
			return data;
		}

		@Override
		public abstract void flush();

		@Override
		public void close() {
			flush();
		}
	}

	private static class ByteArrayReadableChannel implements ReadableChannel {
		private final byte[] data;
		private final int blockSize;
		private int position;
		private boolean open = true;

		ByteArrayReadableChannel(byte[] buf, int blockSize) {
			data = buf;
			this.blockSize = blockSize;
		}

		@Override
		public int read(ByteBuffer dst) {
			int n = Math.min(dst.remaining(), data.length - position);
			if (n == 0)
				return -1;
			dst.put(data, position, n);
			position += n;
			return n;
		}

		@Override
		public void close() {
			open = false;
		}

		@Override
		public boolean isOpen() {
			return open;
		}

		@Override
		public long position() {
			return position;
		}

		@Override
		public void position(long newPosition) {
			position = (int) newPosition;
		}

		@Override
		public long size() {
			return data.length;
		}

		@Override
		public int blockSize() {
			return blockSize;
		}

		@Override
		public void setReadAheadBytes(int b) {
			// Unnecessary on a byte array.
		}
	}

	/** DfsRefDatabase used by InMemoryRepository. */
	protected class MemRefDatabase extends DfsReftableDatabase {
		boolean performsAtomicTransactions = true;

		/** Initialize a new in-memory ref database. */
		protected MemRefDatabase() {
			super(InMemoryRepository.this);
		}

		@Override
		public ReftableConfig getReftableConfig() {
			ReftableConfig cfg = new ReftableConfig();
			cfg.setAlignBlocks(false);
			cfg.setIndexObjects(false);
			cfg.fromConfig(getRepository().getConfig());
			return cfg;
		}

		@Override
		public boolean performsAtomicTransactions() {
			return performsAtomicTransactions;
		}
	}
}

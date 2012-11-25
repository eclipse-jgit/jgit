package org.eclipse.jgit.storage.dfs;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Ref.Storage;
import org.eclipse.jgit.util.RefList;

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
	private static final AtomicInteger packId = new AtomicInteger();

	private final DfsObjDatabase objdb;

	private final DfsRefDatabase refdb;

	/**
	 * Initialize a new in-memory repository.
	 *
	 * @param repoDesc
	 *            description of the repository.
	 * @since 2.0
	 */
	public InMemoryRepository(DfsRepositoryDescription repoDesc) {
		super(new DfsRepositoryBuilder<DfsRepositoryBuilder, InMemoryRepository>() {
			@Override
			public InMemoryRepository build() throws IOException {
				throw new UnsupportedOperationException();
			}
		}.setRepositoryDescription(repoDesc));

		objdb = new MemObjDatabase(this);
		refdb = new MemRefDatabase();
	}

	@Override
	public DfsObjDatabase getObjectDatabase() {
		return objdb;
	}

	@Override
	public DfsRefDatabase getRefDatabase() {
		return refdb;
	}

	private class MemObjDatabase extends DfsObjDatabase {
		private List<DfsPackDescription> packs = new ArrayList<DfsPackDescription>();

		MemObjDatabase(DfsRepository repo) {
			super(repo, new DfsReaderOptions());
		}

		@Override
		protected synchronized List<DfsPackDescription> listPacks() {
			return packs;
		}

		@Override
		protected DfsPackDescription newPack(PackSource source) {
			int id = packId.incrementAndGet();
			DfsPackDescription desc = new MemPack(
					"pack-" + id + "-" + source.name(), //$NON-NLS-1$ //$NON-NLS-2$
					getRepository().getDescription());
			return desc.setPackSource(source);
		}

		@Override
		protected synchronized void commitPackImpl(
				Collection<DfsPackDescription> desc,
				Collection<DfsPackDescription> replace) {
			List<DfsPackDescription> n;
			n = new ArrayList<DfsPackDescription>(desc.size() + packs.size());
			n.addAll(desc);
			n.addAll(packs);
			if (replace != null)
				n.removeAll(replace);
			packs = n;
		}

		@Override
		protected void rollbackPack(Collection<DfsPackDescription> desc) {
			// Do nothing. Pack is not recorded until commitPack.
		}

		@Override
		protected ReadableChannel openPackFile(DfsPackDescription desc)
				throws FileNotFoundException {
			MemPack memPack = (MemPack) desc;
			if (memPack.packFile == null)
				throw new FileNotFoundException(desc.getPackName());
			return new ByteArrayReadableChannel(memPack.packFile);
		}

		@Override
		protected ReadableChannel openPackIndex(DfsPackDescription desc)
				throws FileNotFoundException {
			MemPack memPack = (MemPack) desc;
			if (memPack.packIndex == null)
				throw new FileNotFoundException(desc.getIndexName());
			return new ByteArrayReadableChannel(memPack.packIndex);
		}

		@Override
		protected DfsOutputStream writePackFile(DfsPackDescription desc) {
			final MemPack memPack = (MemPack) desc;
			return new Out() {
				@Override
				public void flush() {
					memPack.packFile = getData();
				}
			};
		}

		@Override
		protected DfsOutputStream writePackIndex(DfsPackDescription desc) {
			final MemPack memPack = (MemPack) desc;
			return new Out() {
				@Override
				public void flush() {
					memPack.packIndex = getData();
				}
			};
		}
	}

	private static class MemPack extends DfsPackDescription {
		private byte[] packFile;

		private byte[] packIndex;

		MemPack(String name, DfsRepositoryDescription repoDesc) {
			super(repoDesc, name);
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

		private int position;

		private boolean open = true;

		ByteArrayReadableChannel(byte[] buf) {
			data = buf;
		}

		public int read(ByteBuffer dst) {
			int n = Math.min(dst.remaining(), data.length - position);
			if (n == 0)
				return -1;
			dst.put(data, position, n);
			position += n;
			return n;
		}

		public void close() {
			open = false;
		}

		public boolean isOpen() {
			return open;
		}

		public long position() {
			return position;
		}

		public void position(long newPosition) {
			position = (int) newPosition;
		}

		public long size() {
			return data.length;
		}

		public int blockSize() {
			return 0;
		}
	}

	private class MemRefDatabase extends DfsRefDatabase {
		private final ConcurrentMap<String, Ref> refs = new ConcurrentHashMap<String, Ref>();

		MemRefDatabase() {
			super(InMemoryRepository.this);
		}

		@Override
		protected RefCache scanAllRefs() throws IOException {
			RefList.Builder<Ref> ids = new RefList.Builder<Ref>();
			RefList.Builder<Ref> sym = new RefList.Builder<Ref>();
			for (Ref ref : refs.values()) {
				if (ref.isSymbolic())
					sym.add(ref);
				ids.add(ref);
			}
			ids.sort();
			sym.sort();
			return new RefCache(ids.toRefList(), sym.toRefList());
		}

		@Override
		protected boolean compareAndPut(Ref oldRef, Ref newRef)
				throws IOException {
			String name = newRef.getName();
			if (oldRef == null || oldRef.getStorage() == Storage.NEW)
				return refs.putIfAbsent(name, newRef) == null;
			Ref cur = refs.get(name);
			if (cur != null && eq(cur, oldRef))
				return refs.replace(name, cur, newRef);
			else
				return false;

		}

		@Override
		protected boolean compareAndRemove(Ref oldRef) throws IOException {
			String name = oldRef.getName();
			Ref cur = refs.get(name);
			if (cur != null && eq(cur, oldRef))
				return refs.remove(name, cur);
			else
				return false;
		}

		private boolean eq(Ref a, Ref b) {
			if (a.getObjectId() == null && b.getObjectId() == null)
				return true;
			if (a.getObjectId() != null)
				return a.getObjectId().equals(b.getObjectId());
			return false;
		}
	}
}

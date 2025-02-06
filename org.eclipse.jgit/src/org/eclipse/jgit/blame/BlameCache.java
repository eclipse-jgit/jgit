package org.eclipse.jgit.blame;


import java.util.Arrays;

/**
 * Provide already calculated blame regions
 * <p>
 * This cache should NOT autopopulate if the value doesn't exist, as it could
 * create an infinite loop (generator looking at cache that autopopulates
 * invoking a generator).
 */
public interface BlameCache {
	Entry get(Key cacheKey);

	void put(Key cacheKey, Entry value);

	default EntryBuilder entryBuilder(Key key, int sourceLength) {
		return new EntryBuilder(key, sourceLength);
	}

	record Key(String repoKey, String commitId, String path) {
		public static Key of(String repoKey, String commitId, String path) {
			return new Key(repoKey, commitId, path);
		}
	}

	class Entry {
		// This array is the length of the result file
		// with the commitId per-line
		private final String[] commitIds;

		public Entry(String[] commitIds) {
			this.commitIds = commitIds;
		}

		/**
		 * @param line line in source (starting by 0)
		 * @return commitId that last touched that line
		 */
		public String getCommitId(int line) {
			return commitIds[line];
		}

		@Override
		public String toString() {
			return String.join(", ", Arrays.asList(commitIds));
		}
	}

	class EntryBuilder {
		private final Key key;
		private final String[] commitIds;

		EntryBuilder(Key key, int resultLength) {
			this.key = key;
			commitIds = new String[resultLength];
		}

		Key getKey() {
			return key;
		}

		void add(Candidate c) {
			String commitId = c.sourceCommit.name();
			Region region = c.regionList;
			while (region != null) {
				int start = region.resultStart;
				int end = region.resultStart + region.length;
				for (int i = start; i < end; i++) {
					commitIds[i] = commitId;
				}
				region = region.next;
			}
		}

		Entry done() {
			  Entry e = new Entry(commitIds);
			  return e;
		}
	}

}

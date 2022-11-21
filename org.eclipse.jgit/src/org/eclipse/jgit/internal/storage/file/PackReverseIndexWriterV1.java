// TODO: copyright?

package org.eclipse.jgit.internal.storage.file;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.stream.IntStream;

/**
 * TODO
 */
final class PackReverseIndexWriterV1 extends PackReverseIndexWriter {
	// TODO comment about GIT_HASH_SHA1 == 1 and GIT_HASH_SHA256 == 2
	private static final int OID_VERSION_SHA1 = 1;
	private static final int DEFAULT_OID_VERSION = OID_VERSION_SHA1;

	PackReverseIndexWriterV1(final OutputStream dst) {
		super(dst);
	}

	protected void writeHeader() throws IOException {
		out.write(MAGIC);
		dataOutput.writeInt(VERSION_1);
		dataOutput.writeInt(DEFAULT_OID_VERSION);
	}

	protected void writeBody() throws IOException {
		int[] indexPositionsSortedByOffset = IntStream.range(0, objectsSortedByIndexPosition.size()).boxed()
				.sorted(Comparator.comparingLong(
						indexPosition -> objectsSortedByIndexPosition.get((int) indexPosition).getOffset()))
				.mapToInt(i -> i).toArray();

		for (int indexPosition : indexPositionsSortedByOffset) {
			dataOutput.writeInt(indexPosition);
		}
	}
}

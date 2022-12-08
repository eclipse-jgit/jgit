// TODO: copyright?

package org.eclipse.jgit.internal.storage.file;

import static org.eclipse.jgit.internal.storage.file.PackReverseIndex.MAGIC;
import static org.eclipse.jgit.internal.storage.file.PackReverseIndex.VERSION_1;
import static org.eclipse.jgit.internal.storage.file.PackReverseIndexV1.OID_VERSION_SHA1;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.stream.IntStream;

/**
 * TODO
 */
final class PackReverseIndexWriterV1 extends PackReverseIndexWriter {
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

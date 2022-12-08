/*
TODO
 */

package org.eclipse.jgit.internal.storage.file;

import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;

import java.io.DataInput;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.text.MessageFormat;
import java.util.Arrays;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.PackMismatchException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.Hex;
import org.eclipse.jgit.util.IO;

/**
 * TODO
 */
final class PackReverseIndexV1 extends PackReverseIndex {
	// TODO comment about SHA1 == 1 and SHA256 == 2
	static final int OID_VERSION_SHA1 = 1;
	private static final int SHA1_BYTES = OBJECT_ID_LENGTH;

	private final DigestInputStream inputStream;
	private final DataInput dataIn;
	private final PackBitmapIndex.SupplierWithIOException<PackIndex> packIndexSupplier;

	private int objectCount;
	private byte[] packChecksum;
	private int[] indexPositionsSortedByOffset;
	private boolean isParsed;
	private PackIndex packIndex;

	PackReverseIndexV1(DigestInputStream inputStream, long objectCount,
			PackBitmapIndex.SupplierWithIOException<PackIndex> packIndexSupplier) throws IOException {
		this.inputStream = inputStream;
		// TODO do we support `long` object count? Then one array is not big enough, because only int-indexed arrays
		// are supported in Java.
		this.objectCount = (int) objectCount;
		dataIn = new SimpleDataInput(inputStream);

		int oid_version = dataIn.readInt();
		if (oid_version != OID_VERSION_SHA1) {
			// Only SHA1 is currently supported.
			throw new IOException(MessageFormat.format(JGitText.get().expectedGot, OID_VERSION_SHA1, oid_version));
		}

		indexPositionsSortedByOffset = new int[this.objectCount];

		this.packIndexSupplier = packIndexSupplier;
	}

	@Override
	public void verifyPackChecksum(String packFilePath) throws PackMismatchException {
		if (!Arrays.equals(packChecksum, getPackIndex().getChecksum())) {
			throw new PackMismatchException(MessageFormat.format(JGitText.get().packChecksumMismatch, packFilePath,
					PackExt.INDEX.getExtension(), Hex.toHexString(getPackIndex().getChecksum()),
					PackExt.REVERSE_INDEX.getExtension(), Hex.toHexString(packChecksum)));
		}
	}

	void parse() throws IOException {
		if (isParsed) {
			throw new IllegalStateException("Already parsed the reverse index input stream");
		}
		parseBody();
		parseChecksums();
		isParsed = true;
	}

	private void parseBody() throws IOException {
		for (int i = 0; i < objectCount; i++) {
			indexPositionsSortedByOffset[i] = dataIn.readInt();
		}
	}

	private void parseChecksums() throws IOException {
		packChecksum = new byte[SHA1_BYTES];
		IO.readFully(inputStream, packChecksum);

		byte[] observedSelfChecksum = inputStream.getMessageDigest().digest();
		byte[] readSelfChecksum = new byte[SHA1_BYTES];
		IO.readFully(inputStream, readSelfChecksum);
		if (!Arrays.equals(readSelfChecksum, observedSelfChecksum)) {
			throw new CorruptObjectException(MessageFormat.format(JGitText.get().corruptReverseIndexChecksumIncorrect,
					Hex.toHexString(readSelfChecksum), Hex.toHexString(observedSelfChecksum)));
		}
	}

	@Override
	public ObjectId findObject(long offset) {
		int reversePosition = findPosition(offset);
		if (reversePosition < 0) {
			return null;
		}
		int forwardPosition = findForwardPositionByReversePosition(reversePosition);
		return getPackIndex().getObjectId(forwardPosition);
	}

	@Override
	public long findNextOffset(long offset, long maxOffset) throws CorruptObjectException {
		final int position = findPosition(offset);
		if (position < 0) {
			throw new CorruptObjectException(
					MessageFormat.format(JGitText.get().cantFindObjectInReversePackIndexForTheSpecifiedOffset,
							Long.valueOf(offset)));
		}
		if (position + 1 == objectCount) {
			return maxOffset;
		}
		return findOffsetByReversePosition(position + 1);
	}

	@Override
	int findPosition(long offset) {
		return binarySearchByOffset(offset);
	}

	@Override
	ObjectId findObjectByPosition(int position) {
		return getPackIndex().getObjectId(findForwardPositionByReversePosition(position));
	}

	private long findOffsetByReversePosition(int position) {
		return getPackIndex().getOffset(findForwardPositionByReversePosition(position));
	}

	private int findForwardPositionByReversePosition(int reversePosition) {
		assert (reversePosition >= 0);
		assert (reversePosition < indexPositionsSortedByOffset.length);
		return indexPositionsSortedByOffset[reversePosition];
	}

	private int binarySearchByOffset(long wantedOffset) {
		int low = 0;
		int high = objectCount - 1;
		while (low <= high) {
			final int mid = (low + high) >>> 1;
			final long offsetAtMid = findOffsetByReversePosition(mid);
			if (offsetAtMid == wantedOffset)
				return mid;
			else if (offsetAtMid > wantedOffset)
				high = mid - 1;
			else
				low = mid + 1;
		}
		return -1;
	}

	private PackIndex getPackIndex() {
		if (packIndex == null) {
			try {
				packIndex = packIndexSupplier.get();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return packIndex;
	}
}

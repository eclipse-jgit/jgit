package org.eclipse.jgit.lib.objectcheckers;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;

public class TreeChecker implements IObjectChecker {

	public void check(final byte[] raw) throws CorruptObjectException {
        final int sz = raw.length;
        int ptr = 0;
        int lastNameB = 0, lastNameE = 0, lastMode = 0;

        while (ptr < sz) {
            int thisMode = 0;
            for (;;) {
                if (ptr == sz)
                    throw new CorruptObjectException("truncated in mode");
                final byte c = raw[ptr++];
                if (' ' == c)
                    break;
                if (c < '0' || c > '7')
                    throw new CorruptObjectException("invalid mode character");
                if (thisMode == 0 && c == '0')
                    throw new CorruptObjectException("mode starts with '0'");
                thisMode <<= 3;
                thisMode += c - '0';
            }

            if (FileMode.fromBits(thisMode).getObjectType() == Constants.OBJ_BAD)
                throw new CorruptObjectException("invalid mode " + thisMode);

            final int thisNameB = ptr;
            for (;;) {
                if (ptr == sz)
                    throw new CorruptObjectException("truncated in name");
                final byte c = raw[ptr++];
                if (c == 0)
                    break;
                if (c == '/')
                    throw new CorruptObjectException("name contains '/'");
            }
            if (thisNameB + 1 == ptr)
                throw new CorruptObjectException("zero length name");
            if (raw[thisNameB] == '.') {
                final int nameLen = (ptr - 1) - thisNameB;
                if (nameLen == 1)
                    throw new CorruptObjectException("invalid name '.'");
                if (nameLen == 2 && raw[thisNameB + 1] == '.')
                    throw new CorruptObjectException("invalid name '..'");
            }
            if (duplicateName(raw, thisNameB, ptr - 1))
                throw new CorruptObjectException("duplicate entry names");

            if (lastNameB != 0) {
                final int cmp = pathCompare(raw, lastNameB, lastNameE,
                        lastMode, thisNameB, ptr - 1, thisMode);
                if (cmp > 0)
                    throw new CorruptObjectException("incorrectly sorted");
            }

            lastNameB = thisNameB;
            lastNameE = ptr - 1;
            lastMode = thisMode;

            ptr += Constants.OBJECT_ID_LENGTH;
            if (ptr > sz)
                throw new CorruptObjectException("truncated in object id");
        }
	}

    private static boolean duplicateName(final byte[] raw,
			final int thisNamePos, final int thisNameEnd) {
		final int sz = raw.length;
		int nextPtr = thisNameEnd + 1 + Constants.OBJECT_ID_LENGTH;
		for (;;) {
			int nextMode = 0;
			for (;;) {
				if (nextPtr >= sz)
					return false;
				final byte c = raw[nextPtr++];
				if (' ' == c)
					break;
				nextMode <<= 3;
				nextMode += c - '0';
			}

			final int nextNamePos = nextPtr;
			for (;;) {
				if (nextPtr == sz)
					return false;
				final byte c = raw[nextPtr++];
				if (c == 0)
					break;
			}
			if (nextNamePos + 1 == nextPtr)
				return false;

			final int cmp = pathCompare(raw, thisNamePos, thisNameEnd,
					FileMode.TREE.getBits(), nextNamePos, nextPtr - 1, nextMode);
			if (cmp < 0)
				return false;
			else if (cmp == 0)
				return true;

			nextPtr += Constants.OBJECT_ID_LENGTH;
		}
	}

	private static int pathCompare(final byte[] raw, int aPos, final int aEnd,
			final int aMode, int bPos, final int bEnd, final int bMode) {
		while (aPos < aEnd && bPos < bEnd) {
			final int cmp = (raw[aPos++] & 0xff) - (raw[bPos++] & 0xff);
			if (cmp != 0)
				return cmp;
		}

		if (aPos < aEnd)
			return (raw[aPos] & 0xff) - lastPathChar(bMode);
		if (bPos < bEnd)
			return lastPathChar(aMode) - (raw[bPos] & 0xff);
		return 0;
	}

    private static int lastPathChar(final int mode) {
		return FileMode.TREE.equals(mode) ? '/' : '\0';
	}
}

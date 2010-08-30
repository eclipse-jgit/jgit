package org.eclipse.jgit.lib.objectcheckers;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.util.MutableInteger;

import static org.eclipse.jgit.util.RawParseUtils.*;

public class TagChecker implements IObjectChecker {
    /**
	 * Check an annotated tag for errors.
	 *
	 * @param raw
	 *            the tag data. The array is never modified.
	 * @throws org.eclipse.jgit.errors.CorruptObjectException
	 *             if any error was detected.
	 */
	public void check(final byte[] raw) throws CorruptObjectException {
		int ptr = 0;

		if ((ptr = match(raw, ptr, ObjectChecker.object)) < 0)
			throw new CorruptObjectException("no object header");
		if ((ptr = id(raw, ptr)) < 0 || raw[ptr++] != '\n')
			throw new CorruptObjectException("invalid object");

		if ((ptr = match(raw, ptr, ObjectChecker.type)) < 0)
			throw new CorruptObjectException("no type header");
		ptr = nextLF(raw, ptr);

		if ((ptr = match(raw, ptr, ObjectChecker.tag)) < 0)
			throw new CorruptObjectException("no tag header");
		ptr = nextLF(raw, ptr);

		if ((ptr = match(raw, ptr, ObjectChecker.tagger)) > 0) {
			if ((ptr = personIdent(raw, ptr)) < 0 || raw[ptr++] != '\n')
				throw new CorruptObjectException("invalid tagger");
		}
	}

    private int id(final byte[] raw, final int ptr) {
		try {
			new MutableObjectId().fromString(raw, ptr);
			return ptr + Constants.OBJECT_ID_STRING_LENGTH;
		} catch (IllegalArgumentException e) {
			return -1;
		}
	}

    private int personIdent(final byte[] raw, int ptr) {
        final int emailB = nextLF(raw, ptr, '<');
        if (emailB == ptr || raw[emailB - 1] != '<')
            return -1;

        final int emailE = nextLF(raw, emailB, '>');
        if (emailE == emailB || raw[emailE - 1] != '>')
            return -1;
        if (emailE == raw.length || raw[emailE] != ' ')
            return -1;

        MutableInteger ptrout=new MutableInteger();
        parseBase10(raw, emailE + 1, ptrout); // when
        ptr = ptrout.value;
        if (emailE + 1 == ptr)
            return -1;
        if (ptr == raw.length || raw[ptr] != ' ')
            return -1;

        parseBase10(raw, ptr + 1, ptrout); // tz offset
        if (ptr + 1 == ptrout.value)
            return -1;
        return ptrout.value;
    }
    
}

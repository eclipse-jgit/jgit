package org.eclipse.jgit.internal.storage.file.midx;

import java.io.IOException;

public class MIDXFormatException extends IOException {

    private static final long serialVersionUID = 1L;

    /**
     * Construct an exception.
     *
     * @param why
     *            description of the type of error.
     */
    MIDXFormatException(String why) {
        super(why);
    }
}

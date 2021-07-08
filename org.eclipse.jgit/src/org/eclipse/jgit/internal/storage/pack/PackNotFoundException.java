package org.eclipse.jgit.internal.storage.pack;

import java.text.MessageFormat;
import org.eclipse.jgit.internal.storage.file.Pack;
import org.eclipse.jgit.internal.JGitText;

import java.io.IOException;

/**
 * Indicates that a pack hasn't been found.
 *
 * @since 5.12
 */
public class PackNotFoundException extends IOException {
    private static final long serialVersionUID = 1L;

    private final Pack pack;
    private final IOException originalException;

    /**
     * <p>Constructor for PackNotFoundException.</p>
     *
     * @param missingPack
     *            a message explaining the state. This message should not
     *            be shown to an end-user.
     * @param ioe
     *            original IOEXception
     */
    public PackNotFoundException(Pack missingPack, IOException ioe) {
        super(MessageFormat.format(JGitText.get().packWasDeleted,
				missingPack.getPackFile().getAbsolutePath()));
        originalException = ioe;
        pack = missingPack;
    }

    /**
     * Get the missing Pack.
     *
     * @return missing Pack object.
     */
    public Pack getPack() {
        return pack;
    }

    /**
     * Get the original IOEXception thrown.
     *
     * @return original IOEXception thrown.
     */
    public IOException getOriginalException() {
        return originalException;
    }
}

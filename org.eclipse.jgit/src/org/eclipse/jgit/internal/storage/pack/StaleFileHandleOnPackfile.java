package org.eclipse.jgit.internal.storage.pack;

import java.text.MessageFormat;
import org.eclipse.jgit.internal.storage.file.Pack;
import org.eclipse.jgit.internal.JGitText;

import java.io.IOException;

/**
 * Indicates that a stae file handle error has been raised against a pack file
 *
 */
public class StaleFileHandleOnPackfile extends IOException {
    private static final long serialVersionUID = 1L;

    private final Pack pack;

    /**
     * <p>Constructor for PackNotFoundException.</p>
     *
     * @param missingPack
     *            a message explaining the state. This message should not
     *            be shown to an end-user.s
     */
    public StaleFileHandleOnPackfile(Pack missingPack) {
        super(MessageFormat.format(JGitText.get().packWasDeleted,
				missingPack.getPackFile().getAbsolutePath()));
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
        return (IOException) this.getCause();
    }
}

package org.eclipse.jgit.errors;

import org.eclipse.jgit.internal.JGitText;

import java.io.IOException;
import java.text.MessageFormat;
import java.time.Duration;

/**
 * Thrown when the search for reuse phase times out.
 */
public class SearchForReuseTimeout extends IOException {
    private static final long serialVersionUID = 1L;

    /**
     * Construct a search for reuse timeout error.
     *
     * @param timeout
     *            time exceeded during the search for reuse phase.
     */
    public SearchForReuseTimeout(Duration timeout) {
        super(MessageFormat.format(JGitText.get().searchForReuseTimeout,
                timeout.toSeconds()));
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
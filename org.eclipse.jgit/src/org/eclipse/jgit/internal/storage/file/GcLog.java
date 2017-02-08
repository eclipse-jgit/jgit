package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.util.GitDateParser;
import org.eclipse.jgit.util.SystemReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.FileTime;
import java.text.MessageFormat;
import java.text.ParseException;
import java.time.Instant;

/**
 * This class manages the gc.log file for a {@link FileRepository}.
 * @since 4.7
 */
public class GcLog {
    private final FileRepository repo;
    private final File logFile;
    private final LockFile lock;
    private Instant gcLogExpire;
    private static final String LOG_EXPIRY_DEFAULT = "1.day"; //$NON-NLS-1$
    private boolean nonEmpty = false;

    /**
     * Construct a GcLog object for a {@link FileRepository}
     * @param repo the repository
     */
    public GcLog(FileRepository repo) {
        this.repo = repo;
        logFile = new File(repo.getDirectory(), "gc.log"); //$NON-NLS-1$
        lock = new LockFile(logFile);
    }

    private Instant getLogExpiry() throws ParseException {
        if (gcLogExpire == null) {
            String logExpiryStr = repo.getConfig().getString(
                    ConfigConstants.CONFIG_GC_SECTION, null,
                    ConfigConstants.CONFIG_KEY_LOGEXPIRY);
            if (logExpiryStr == null) {
                logExpiryStr = LOG_EXPIRY_DEFAULT;
            }
            gcLogExpire = GitDateParser.parse(logExpiryStr, null, SystemReader
                    .getInstance().getLocale()).toInstant();
        }
        return gcLogExpire;
    }

    private boolean autoGcBlockedByOldLockFile(boolean failOnExistingLockFile) {
        try {
            FileTime lastModified = Files.getLastModifiedTime(logFile.toPath());
            if (lastModified.toInstant().compareTo(getLogExpiry()) < 0) {
                // There is an existing log file, which is too recent to ignore
                if (!failOnExistingLockFile) {
                    try (BufferedReader reader = Files.newBufferedReader(logFile.toPath())) {
                        char[] buf = new char[1000];
                        int len = reader.read(buf, 0, 1000);
                        String oldError = new String(buf, 0, len);

                        throw new JGitInternalException("A previous GC run reported an error: " +
                                oldError +
                                "\nAutomatic gc will fail until the file " + logFile +
                                " is removed");
                    }
                }
                return true;
            }
        } catch (NoSuchFileException e) {
            // No existing log file, OK.
        } catch (IOException | ParseException e) {
            throw new JGitInternalException(e.getMessage(), e);
        }
        return false;
    }

    /**
     * Lock the GC log file for updates
     *
     * @param failOnExistingLockFile If true, and if gc.log already exists, unlock and return false
     * @return true if we hold the lock
     */
    public boolean lock(boolean failOnExistingLockFile) {
        try {
            if (!lock.lock()) {
                return false;
            }
        } catch (IOException e) {
            throw new JGitInternalException(e.getMessage(), e);
        }
        if (autoGcBlockedByOldLockFile(failOnExistingLockFile)) {
            lock.unlock();;
            return false;
        }
        return true;
    }

    /**
     * Unlock (roll back) the GC log lock
     */
    public void unlock() {
        lock.unlock();
    }

    /**
     * Delete any existing gc.log file
     */
    public void cleanUp() {
        logFile.delete();
    }

    /**
     * Commit changes to the gc log, if there have been any writes.
     * Otherwise, just unlock and delete the existing file (if any)
     * @return true if committing (or unlocking/deleting) succeeds.
     */
    public boolean commit() {
        if (nonEmpty) {
            return lock.commit();
        } else {
            logFile.delete();
            lock.unlock();
            return true;
        }
    }

    /**
     * Write to the pending gc log.  Content will be committed upon a call to commit()
     * @param content The content to write
     * @throws IOException
     */
    public void write(byte[] content) throws IOException {
        if (content.length > 0) {
            nonEmpty = true;
        }
        lock.write(content);
    }
}
